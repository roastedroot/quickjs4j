import { z } from "zod";

const stringSchema = z.string();

export async function validateString(str) {
    // const result = await stringSchema.safeParseAsync(str);
    const result = await stringSchema.safeParseAsync(str);
    console.log("my result is");
    console.log(JSON.stringify(result));
    return result;
}
