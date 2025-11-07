extern crate javy_plugin_api;
use javy_plugin_api::{import_namespace, javy::quickjs::prelude::Func, Config};
use std::alloc::{alloc, dealloc, Layout};
use std::ptr::copy_nonoverlapping;

import_namespace!("chicory_plugin");

mod chicory_imports {
    #[link(wasm_import_module = "chicory")]
    extern "C" {
        pub fn invoke(
            module_str_ptr: *const u8,
            module_str_len: usize,
            name_str_ptr: *const u8,
            name_str_len: usize,
            args_str_ptr: *const u8,
            args_str_len: usize,
        ) -> *const u32;
    }
}

// Implement canonical ABI functions using Javy's allocator implementation
// Based on: https://github.com/bytecodealliance/javy/blob/4142700f24f250b65478f165e27605d7a0571a3d/crates/javy/src/alloc.rs

// Unlike C's realloc, zero-length allocations need not have
// unique addresses, so a zero-length allocation may be passed
// in and also requested, but it's ok to return anything that's
// non-zero to indicate success.
const ZERO_SIZE_ALLOCATION_PTR: *mut u8 = 1 as _;

/// Allocates memory in instance.
///
/// 1. Allocate memory of new_size with alignment.
/// 2. If original_ptr != 0.  
///    a. copy min(new_size, original_size) bytes from original_ptr to new memory.  
///    b. de-allocate original_ptr.
/// 3. Return new memory ptr.
///
/// # Safety
///
/// * `original_ptr` must be 0 or a valid pointer.
/// * If `original_ptr` is not 0, it must be valid for reads of `original_size`
///   bytes.
/// * If `original_ptr` is not 0, it must be properly aligned.
/// * If `original_size` is not 0, it must match the `new_size` value provided
///   in the original `canonical_abi_realloc` call that returned `original_ptr`.
#[no_mangle]
pub unsafe extern "C" fn canonical_abi_realloc(
    original_ptr: *mut u8,
    original_size: usize,
    alignment: usize,
    new_size: usize,
) -> *mut std::ffi::c_void {
    assert!(new_size >= original_size);

    let new_mem = match new_size {
        0 => ZERO_SIZE_ALLOCATION_PTR,
        // this call to `alloc` is safe since `new_size` must be > 0
        _ => alloc(Layout::from_size_align(new_size, alignment).unwrap()),
    };

    if !original_ptr.is_null() && original_size != 0 {
        copy_nonoverlapping(original_ptr, new_mem, original_size);
        canonical_abi_free(original_ptr, original_size, alignment);
    }
    new_mem as _
}

/// Frees allocated memory in instance.
///
/// # Safety
///
/// * `ptr` must denote a block of memory allocated by `canonical_abi_realloc`.
/// * `size` and `alignment` must match the values provided in the original
///   `canonical_abi_realloc` call that returned `ptr`.
#[no_mangle]
pub unsafe extern "C" fn canonical_abi_free(ptr: *mut u8, size: usize, alignment: usize) {
    if size > 0 {
        dealloc(ptr, Layout::from_size_align(size, alignment).unwrap())
    };
}


fn invoke_exec(module_str: String, name_str: String, args_str: String) -> String {
    let module_bytes: &[u8] = module_str.as_bytes();
    let name_bytes: &[u8] = name_str.as_bytes();
    let args_bytes: &[u8] = args_str.as_bytes();

    let return_str = unsafe {
        let wide_ptr = chicory_imports::invoke(
            module_bytes.as_ptr(),
            module_bytes.len(),
            name_bytes.as_ptr(),
            name_bytes.len(),
            args_bytes.as_ptr(),
            args_bytes.len(),
        );
        let [ptr, len] = std::slice::from_raw_parts(wide_ptr, 2) else {
            unreachable!()
        };
        let res = std::slice::from_raw_parts(*ptr as *const u8, *len as usize);
        let str_result = std::str::from_utf8(res).unwrap().to_string();
        canonical_abi_free(*wide_ptr as *mut u8, 8, 1);
        canonical_abi_free(*ptr as *mut u8, *len as usize, 1);
        str_result
    };

    return_str
}

#[export_name = "initialize_runtime"]
pub extern "C" fn initialize_runtime() {
    javy_plugin_api::initialize_runtime(
        || {
            let mut config = Config::default();
            config
                .event_loop(true)
                .text_encoding(true)
                .javy_stream_io(true);
            config
        },
        |runtime| {
            runtime.context().with(|ctx| {
                ctx.globals().set("plugin", true).unwrap();
                ctx.globals()
                    .set(
                        "java_invoke",
                        Func::from(|module: String, name: String, args: String| {
                            invoke_exec(module, name, args)
                        }),
                    )
                    .unwrap();
            });
            runtime
        },
    )
    .unwrap();
}

// Re-export compileSrc and invoke to match 3.0.0 exports
#[no_mangle]
pub extern "C" fn compileSrc(js_src_ptr: *const u8, js_src_len: usize) -> *const u32 {
    unsafe {
        let source = std::slice::from_raw_parts(js_src_ptr, js_src_len);
        let bytecode = javy_plugin_api::compile_src(source).unwrap();
        
        let bytecode_len = bytecode.len();
        let bytecode_ptr = canonical_abi_realloc(
            std::ptr::null_mut(),
            0,
            1,
            bytecode_len,
        ) as *mut u8;
        
        std::ptr::copy_nonoverlapping(bytecode.as_ptr(), bytecode_ptr, bytecode_len);
        
        // Return a pointer to [ptr, len] as u32 array
        let wide_ptr = canonical_abi_realloc(
            std::ptr::null_mut(),
            0,
            1,
            8, // 2 * u32 = 8 bytes
        ) as *mut u32;
        
        let ptr_u32 = bytecode_ptr as u32;
        let len_u32 = bytecode_len as u32;
        
        std::ptr::write(wide_ptr, ptr_u32);
        std::ptr::write(wide_ptr.add(1), len_u32);
        
        wide_ptr as *const u32
    }
}

#[no_mangle]
pub extern "C" fn invoke(
    bytecode_ptr: *const u8,
    bytecode_len: usize,
    fn_name_ptr: *const u8,
    fn_name_len: usize,
) {
    unsafe {
        let bytecode = std::slice::from_raw_parts(bytecode_ptr, bytecode_len);
        
        let fn_name = if fn_name_ptr.is_null() || fn_name_len == 0 {
            None
        } else {
            let fn_name_slice = std::slice::from_raw_parts(fn_name_ptr, fn_name_len);
            Some(std::str::from_utf8(fn_name_slice).unwrap())
        };
        
        javy_plugin_api::invoke(bytecode, fn_name).unwrap();
    }
}
