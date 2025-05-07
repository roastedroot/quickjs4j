// index.js
function validate(artifact) {
  apicurio.log(artifact.name);
  return artifact.type === "openapi";
}
export {
  validate
};
