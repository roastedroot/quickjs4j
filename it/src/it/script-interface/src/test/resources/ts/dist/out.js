// dist/Calculator_Builtins.mjs
function log(args0) {
  Calculator_Builtins.log(args0);
}

// dist/Calculator.js
function add(term1, term2) {
  log("Adding " + term1 + " to " + term2);
  return term1 + term2;
}
function subtract(term1, term2) {
  log("Subtracting " + term2 + " from " + term1);
  return term1 - term2;
}
function multiply(factor1, factor2) {
  log("Multiplying " + factor1 + " and " + factor2);
  return factor1 * factor2;
}
function divide(dividend, divisor) {
  log("Dividing " + dividend + " by " + divisor);
  return dividend / divisor;
}
export {
  add,
  divide,
  multiply,
  subtract
};
