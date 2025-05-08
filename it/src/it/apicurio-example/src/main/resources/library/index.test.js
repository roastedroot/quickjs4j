import { expect, test, vi } from "vitest";

import { validate } from "./index.js";
import { log } from "./apicurio.mjs";

vi.mock("./apicurio.mjs", () => ({
  log: vi.fn().mockImplementation((str) => console.log(`Mocked log: {str}`))
}));

test('can validate artifacts', () => {
    expect(validate({ name: "xyz", type: "openapi"})).toBe(true);
    expect(validate({ name: "xyz", type: "raml"})).toBe(false);
});
