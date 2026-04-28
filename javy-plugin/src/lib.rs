extern crate javy_plugin_api;
use javy_plugin_api::{import_namespace, javy::quickjs::prelude::Func, Config};
use std::alloc::{alloc, dealloc, Layout};

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

#[export_name = "abi_free"]
pub unsafe extern "C" fn abi_free(ptr: *mut u8, size: usize, alignment: usize) {
    if size > 0 {
        dealloc(ptr, Layout::from_size_align(size, alignment).unwrap())
    }
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
        dealloc(*wide_ptr as *mut u8, Layout::from_size_align(8, 1).unwrap());
        dealloc(*ptr as *mut u8, Layout::from_size_align(*len as usize, 1).unwrap());
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
        let bytecode_ptr = alloc(Layout::from_size_align(bytecode_len, 1).unwrap());
        
        std::ptr::copy_nonoverlapping(bytecode.as_ptr(), bytecode_ptr, bytecode_len);
        
        // Return a pointer to [ptr, len] as u32 array
        let wide_ptr = alloc(Layout::from_size_align(8, 1).unwrap()) as *mut u32;
        
        let ptr_u32 = bytecode_ptr as u32;
        let len_u32 = bytecode_len as u32;
        
        std::ptr::write(wide_ptr, ptr_u32);
        std::ptr::write(wide_ptr.add(1), len_u32);
        
        wide_ptr as *const u32
    }
}

#[export_name = "eval"]
pub extern "C" fn eval(
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
