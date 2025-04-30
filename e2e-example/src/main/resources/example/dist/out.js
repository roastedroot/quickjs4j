// api.mjs
function log(args) {
  javaExposedFunctions.log(args);
}

// index.mjs
function add(x, y) {
  log(`add: ${x} ${y}`);
  return x + y;
}
async function sub(x, y) {
  log(`add: ${x} ${y}`);
  return x - y;
}
export {
  add,
  sub
};
