
.PHONY: clean
clean:
	rm -rf target

.PHONY: build
build:
	cargo build --release --target=wasm32-wasip1
	rm -f ../javy_chicory_plugin.wasm
	cp target/wasm32-wasip1/release/javy_chicory_plugin.wasm ../javy_chicory_plugin.wasm
