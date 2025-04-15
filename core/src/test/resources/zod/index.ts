import { z } from "zod";

const mySchema = z.string();

const parseTuna = mySchema.safeParse("tuna"); // => { success: true; data: "tuna" }
const parseNumber = mySchema.safeParse(12); // => { success: false; error: ZodError }

(java_check_tuna as any)(parseTuna);
(java_check_number as any)(parseNumber);
