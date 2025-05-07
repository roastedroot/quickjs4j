// apicurio.mjs
function log(args0) {
  apicurio.log(args0);
}

// index.js
function validate(artifact) {
  log(artifact.name);
  return artifact.type === "openapi";
}
export {
  validate
};
