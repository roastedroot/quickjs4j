// written by the user

import { log } from "./api.mjs";

export function add(x, y) {
    log(`add: ${x} ${y}`);

    return x + y;
}

export async function sub(x, y) {
    log(`sub: ${x} ${y}`);

    return x - y;
}
