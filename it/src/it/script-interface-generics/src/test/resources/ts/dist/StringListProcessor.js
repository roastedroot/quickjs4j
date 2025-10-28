import { log } from "./StringListProcessor_Builtins.mjs";
export function toUpperCase(items) {
    log("Converting " + items.length + " items to uppercase");
    return items.map(item => item.toUpperCase());
}
export function filterContaining(items, filter) {
    log("Filtering items containing '" + filter + "'");
    return items.filter(item => item.includes(filter));
}
export function joinStrings(items, delimiter) {
    log("Joining " + items.length + " items with delimiter '" + delimiter + "'");
    return items.join(delimiter);
}
export function generateStrings(count) {
    log("Generating " + count + " strings");
    const result = [];
    for (let i = 0; i < count; i++) {
        result.push("Item " + i);
    }
    return result;
}
