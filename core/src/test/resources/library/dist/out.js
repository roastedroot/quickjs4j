// java_api.mjs
function log(arg0) {
  java_api.log(arg0);
}

// index.mjs
function add(x, y) {
  log(`add: ${x} ${y}`);
  return x + y;
}
function append(arr, elem) {
  arr.push(elem);
  return arr;
}
export {
  add,
  append
};
