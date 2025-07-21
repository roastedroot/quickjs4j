import { z } from "zod";

const stringSchema = z.string();

export async function validateString(str) {
    const result = await stringSchema.safeParseAsync(str);
    return result;
}
