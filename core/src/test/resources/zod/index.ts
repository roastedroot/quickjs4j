declare const java_check_tuna;
declare const java_check_number;

import { z } from "zod";

const mySchema = z.string();

const parsedTuna = mySchema.safeParse("tuna"); // => { success: true; data: "tuna" }
const parsedNumber = mySchema.safeParse(12); // => { success: false; error: ZodError }

java_check_tuna(parsedTuna);
java_check_number(parsedNumber);
