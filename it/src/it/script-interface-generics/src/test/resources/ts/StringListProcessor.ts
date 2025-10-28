import { log } from "./StringListProcessor_Builtins.mjs";

export function toUpperCase(items: string[]): string[] {
    log("Converting " + items.length + " items to uppercase");
    return items.map(item => item.toUpperCase());
}

export function filterContaining(items: string[], filter: string): string[] {
    log("Filtering items containing '" + filter + "'");
    return items.filter(item => item.includes(filter));
}

export function joinStrings(items: string[], delimiter: string): string {
    log("Joining " + items.length + " items with delimiter '" + delimiter + "'");
    return items.join(delimiter);
}

export function generateStrings(count: number): string[] {
    log("Generating " + count + " strings");
    const result: string[] = [];
    for (let i = 0; i < count; i++) {
        result.push("Item " + i);
    }
    return result;
}
