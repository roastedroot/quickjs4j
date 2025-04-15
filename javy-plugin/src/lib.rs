extern crate javy_plugin_api;

use javy_plugin_api::javy::alloc;
use javy_plugin_api::{import_namespace, javy::quickjs::prelude::Func, Config};

import_namespace!("chicory_plugin");

#[link(wasm_import_module = "chicory")]
extern "C" {
    fn invoke(proxy_ptr: u32, args_str_ptr: *const u8, args_str_len: usize) -> *const u32;
}

fn invoke_exec(proxy_ptr: u32, args_str: String) -> String {
    let bytes: &[u8] = args_str.as_bytes();

    let return_str = unsafe {
        let wide_ptr = invoke(proxy_ptr, bytes.as_ptr(), bytes.len());
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
    config.text_encoding(true).javy_stream_io(true);

    javy_plugin_api::initialize_runtime(config, |runtime| {
        runtime.context().with(|ctx| {
            ctx.globals().set("plugin", true).unwrap();
            ctx.globals()
                .set(
                    "java_invoke",
                    Func::from(|idx: u32, args: String| invoke_exec(idx, args)),
                )
                .unwrap();
        });
        runtime
    })
    .unwrap();
}
