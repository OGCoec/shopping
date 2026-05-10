import { THREE_MODULE_PATH } from "./constants.js";

let threeModulePromise = null;

export function getThree() {
  if (!threeModulePromise) {
    threeModulePromise = import(THREE_MODULE_PATH);
  }
  return threeModulePromise;
}
