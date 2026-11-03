declare const from_java;

import { z } from "zod";

const mySchema = z.string();

const parsedTuna = mySchema.safeParse("tuna"); // => { success: true; data: "tuna" }
const parsedNumber = mySchema.safeParse(12); // => { success: false; error: ZodError }

from_java.java_check_tuna(parsedTuna);
from_java.java_check_number(parsedNumber);
