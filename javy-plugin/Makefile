
.PHONY: clean
clean:
	rm -rf target

.PHONY: build
build:
	cargo build --release --target=wasm32-wasip1
	rm -f ../javy_quickjs4j_plugin.wasm
	cp target/wasm32-wasip1/release/javy_quickjs4j_plugin.wasm ../javy_quickjs4j_plugin.wasm
