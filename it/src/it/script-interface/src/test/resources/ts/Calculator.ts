import { log, throwDivideByZero } from "./Calculator_Builtins.mjs";

export function add(term1: number, term2: number): number {
    log("Adding " + term1 + " to " + term2);
    return term1 + term2;
}

export function subtract(term1: number, term2: number): number {
    log("Subtracting " + term2 + " from " + term1);
    return term1 - term2;
}

export function multiply(factor1: number, factor2: number): number {
    log("Multiplying " + factor1 + " and " + factor2);
    return factor1 * factor2;
}

export function divide(dividend: number, divisor: number): number {
    log("Dividing " + dividend + " by " + divisor);
    if (divisor === 0) {
        throwDivideByZero();
    }
    return dividend / divisor;
}
