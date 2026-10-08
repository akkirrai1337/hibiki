// Browser stand-in for node:zlib - globals.ts's Gzip.inflate only needs gunzipSync.
import { Buffer } from "buffer";
import { gunzipSync as fflateGunzip } from "fflate";

function gunzipSync(input: Uint8Array): Buffer {
  return Buffer.from(fflateGunzip(input));
}

export default { gunzipSync };
export { gunzipSync };
