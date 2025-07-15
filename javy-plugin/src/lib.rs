extern crate javy_plugin_api;

use javy_plugin_api::javy::alloc;
use javy_plugin_api::{import_namespace, javy::quickjs::prelude::Func, Config};

import_namespace!("chicory_plugin");

#[link(wasm_import_module = "chicory")]
extern "C" {
    fn invoke(
        module_str_ptr: *const u8,
        module_str_len: usize,
        name_str_ptr: *const u8,
        name_str_len: usize,
        args_str_ptr: *const u8,
        args_str_len: usize,
    ) -> *const u32;
}

fn invoke_exec(module_str: String, name_str: String, args_str: String) -> String {
    let module_bytes: &[u8] = module_str.as_bytes();
    let name_bytes: &[u8] = name_str.as_bytes();
    let args_bytes: &[u8] = args_str.as_bytes();

    let return_str = unsafe {
        let wide_ptr = invoke(
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
        alloc::canonical_abi_free(*wide_ptr as *mut u8, 8, 1);
        alloc::canonical_abi_free(*ptr as *mut u8, *len as usize, 1);
        str_result
    };

    return_str
}

#[export_name = "initialize_runtime"]
pub extern "C" fn initialize_runtime() {
    let mut config = Config::default();
    config
        .event_loop(true)
        .text_encoding(true)
        .javy_stream_io(true);

    javy_plugin_api::initialize_runtime(config, |runtime| {
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
    })
    .unwrap();
}
