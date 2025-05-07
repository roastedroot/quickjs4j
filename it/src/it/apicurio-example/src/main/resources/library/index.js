
import { log } from "./apicurio.mjs";

export function validate(artifact) {
    log(artifact.name);

    return (artifact.type === "openapi");
}
