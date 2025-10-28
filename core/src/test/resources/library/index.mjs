// written by the user

import { log } from "./java_api.mjs";

export function add(x, y) {
    log(`add: ${x} ${y}`);

    return x + y;
}

export function append(arr, elem) {
    arr.push(elem);
    return arr;
}
