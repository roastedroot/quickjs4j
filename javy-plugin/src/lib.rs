extern crate javy_plugin_api;

use javy_plugin_api::{import_namespace, javy::quickjs::prelude::Func, Config};

import_namespace!("chicory_plugin");

#[link(wasm_import_module = "chicory")]
extern "C" {
    fn imported_function();
}

#[export_name = "initialize_runtime"]
pub extern "C" fn initialize_runtime() {
    let mut config = Config::default();
    config
        .text_encoding(true)
        .javy_stream_io(true);

    javy_plugin_api::initialize_runtime(config, |runtime| {
        runtime.context().with(|ctx| {
            ctx.globals().set("plugin", true).unwrap();
            ctx.globals()
                .set("myJavaFunc", Func::from(|| unsafe { imported_function() }))
                .unwrap();
        });
        runtime
    })
    .unwrap();
}
