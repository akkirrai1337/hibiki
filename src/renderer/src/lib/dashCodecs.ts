// Some DASH manifests leave out `codecs` - an APK source's own local server (HentaiHaven's, which
// turns an HLS stream into a DASH manifest so seeking works) writes just `mimeType="video/mp4"`.
// dash.js plays only what it can match against MediaSource, and without a codec it matches nothing:
// "No streams to play". The codec is written in each stream's init segment, so it is read from there.

/** The ISO BMFF boxes directly inside `data[start, end)`. */
function* boxes(data: Uint8Array, start: number, end: number): Generator<{ type: string; start: number; end: number; body: number }> {
  const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
  let offset = start;
  while (offset + 8 <= end) {
    let size = view.getUint32(offset);
    const type = String.fromCharCode(data[offset + 4], data[offset + 5], data[offset + 6], data[offset + 7]);
    let body = offset + 8;
    if (size === 1) {
      if (offset + 16 > end) return;
      size = Number(view.getBigUint64(offset + 8));
      body = offset + 16;
    } else if (size === 0) {
      size = end - offset;
    }
    if (size < body - offset || offset + size > end) return;
    yield { type, start: offset, end: offset + size, body };
    offset += size;
  }
}

function child(data: Uint8Array, parent: { body: number; end: number }, type: string) {
  for (const box of boxes(data, parent.body, parent.end)) if (box.type === type) return box;
  return undefined;
}

/** A box of `type` anywhere after `from` in the sample entry - simpler than knowing each entry's fixed header. */
function findBox(data: Uint8Array, from: number, end: number, type: string): { body: number; end: number } | undefined {
  for (let i = from; i + 8 <= end; i++) {
    if (data[i + 4] !== type.charCodeAt(0) || data[i + 5] !== type.charCodeAt(1) || data[i + 6] !== type.charCodeAt(2) || data[i + 7] !== type.charCodeAt(3)) continue;
    const size = new DataView(data.buffer, data.byteOffset).getUint32(i);
    if (size >= 8 && i + size <= end) return { body: i + 8, end: i + size };
  }
  return undefined;
}

const hex = (value: number) => value.toString(16).padStart(2, "0").toUpperCase();
const two = (value: number) => String(value).padStart(2, "0");

/** The RFC 6381 codec string of one sample entry, or null for one this does not know. */
function sampleEntryCodec(data: Uint8Array, entry: { type: string; body: number; end: number }): string | null {
  const { type } = entry;
  switch (type) {
    case "avc1":
    case "avc3": {
      const avcC = findBox(data, entry.body, entry.end, "avcC");
      return avcC ? `${type}.${hex(data[avcC.body + 1])}${hex(data[avcC.body + 2])}${hex(data[avcC.body + 3])}` : type;
    }
    case "hvc1":
    case "hev1": {
      const hvcC = findBox(data, entry.body, entry.end, "hvcC");
      if (!hvcC) return type;
      const b = hvcC.body;
      const profileSpace = ["", "A", "B", "C"][data[b + 1] >> 6];
      const tier = (data[b + 1] >> 5) & 1 ? "H" : "L";
      const profile = data[b + 1] & 0x1f;
      // The compatibility flags are written most significant bit first; the codec string wants them reversed.
      let flags = 0;
      for (let i = 0; i < 4; i++) flags = (flags << 8) | data[b + 2 + i];
      let reversed = 0;
      for (let i = 0; i < 32; i++) reversed = (reversed << 1) | ((flags >>> i) & 1);
      const constraints = Array.from(data.subarray(b + 6, b + 12));
      while (constraints.length > 0 && constraints[constraints.length - 1] === 0) constraints.pop();
      const level = data[b + 12];
      return [`${type}.${profileSpace}${profile}`, (reversed >>> 0).toString(16).toUpperCase(), `${tier}${level}`, ...constraints.map((c) => c.toString(16).toUpperCase())].join(".");
    }
    case "vp08":
    case "vp09": {
      const vpcC = findBox(data, entry.body, entry.end, "vpcC");
      if (!vpcC) return type;
      // A FullBox: version and flags, then profile, level, and bit depth in the top four bits.
      const b = vpcC.body + 4;
      return `${type}.${two(data[b])}.${two(data[b + 1])}.${two(data[b + 2] >> 4)}`;
    }
    case "av01": {
      const av1C = findBox(data, entry.body, entry.end, "av1C");
      if (!av1C) return type;
      const b = av1C.body;
      const profile = data[b + 1] >> 5;
      const level = data[b + 1] & 0x1f;
      const tier = data[b + 2] >> 7 ? "H" : "M";
      const highBitDepth = (data[b + 2] >> 6) & 1;
      const twelveBit = (data[b + 2] >> 5) & 1;
      const bitDepth = highBitDepth ? (twelveBit ? 12 : 10) : 8;
      return `av01.${profile}.${two(level)}${tier}.${two(bitDepth)}`;
    }
    case "mp4a": {
      const esds = findBox(data, entry.body, entry.end, "esds");
      if (!esds) return "mp4a.40.2";
      // A FullBox holding ES_Descriptor (0x03) > DecoderConfigDescriptor (0x04: objectTypeIndication
      // first) > DecoderSpecificInfo (0x05), whose first five bits are the AAC audio object type.
      let p = esds.body + 4;
      const descriptor = (tag: number) => {
        if (data[p] !== tag) return false;
        p++;
        while (p < esds.end && data[p] & 0x80) p++;
        p++;
        return p < esds.end;
      };
      if (!descriptor(0x03)) return "mp4a.40.2";
      p += 2; // ES_ID
      const flags = data[p++];
      if (flags & 0x80) p += 2;
      if (flags & 0x40) p += 1 + data[p];
      if (flags & 0x20) p += 2;
      if (!descriptor(0x04)) return "mp4a.40.2";
      const objectType = data[p];
      if (objectType !== 0x40) return `mp4a.${objectType.toString(16)}`;
      p += 13; // objectType, streamType, bufferSize, maxBitrate, avgBitrate
      if (!descriptor(0x05)) return "mp4a.40.2";
      return `mp4a.40.${data[p] >> 3 || 2}`;
    }
    case "Opus":
      return "opus";
    case "fLaC":
      return "flac";
    case "ac-3":
    case "ec-3":
      return type;
    default:
      return null;
  }
}

/** The codec of the first track an fMP4 init segment describes, or null when it cannot tell. */
export function codecFromInitSegment(data: Uint8Array): string | null {
  const moov = [...boxes(data, 0, data.length)].find((box) => box.type === "moov");
  if (!moov) return null;
  for (const trak of boxes(data, moov.body, moov.end)) {
    if (trak.type !== "trak") continue;
    const stbl = ["mdia", "minf", "stbl"].reduce<{ body: number; end: number } | undefined>((box, type) => box && child(data, box, type), trak);
    const stsd = stbl && child(data, stbl, "stsd");
    if (!stsd) continue;
    // A FullBox with an entry count before the entries.
    const entry = boxes(data, stsd.body + 8, stsd.end).next().value;
    if (entry) return sampleEntryCodec(data, entry);
  }
  return null;
}

/**
 * The manifest with every Representation's missing `codecs` read from its init segment, or null
 * when nothing was missing (or nothing could be filled). `baseUrl` is where the manifest came from:
 * it resolves relative init URLs and is kept as the BaseURL of the rewritten manifest, which is then
 * no longer loaded from there.
 */
export async function fillMissingCodecs(mpd: string, baseUrl: string, loadInit: (url: string) => Promise<Uint8Array>): Promise<string | null> {
  const doc = new DOMParser().parseFromString(mpd, "application/xml");
  if (doc.querySelector("parsererror")) return null;
  const missing = [...doc.getElementsByTagName("Representation")].filter((representation) =>
    !representation.getAttribute("codecs") && !representation.parentElement?.getAttribute("codecs"));
  if (missing.length === 0) return null;
  let filled = 0;
  await Promise.all(missing.map(async (representation) => {
    const initUrl = initializationUrl(representation);
    if (!initUrl) return;
    try {
      const codec = codecFromInitSegment(await loadInit(new URL(initUrl, baseUrl).toString()));
      if (!codec) return;
      representation.setAttribute("codecs", codec);
      filled++;
    } catch {
      // Left as it was: dash.js reports what it cannot play the way it always has.
    }
  }));
  if (filled === 0) return null;
  const root = doc.documentElement;
  if (![...root.children].some((element) => element.localName === "BaseURL")) {
    const base = doc.createElementNS(root.namespaceURI, "BaseURL");
    base.textContent = baseUrl;
    root.insertBefore(base, root.firstChild);
  }
  return new XMLSerializer().serializeToString(doc);
}

/** The init segment of a Representation, from its own SegmentList/SegmentTemplate or its AdaptationSet's. */
function initializationUrl(representation: Element): string | null {
  for (const scope of [representation, representation.parentElement]) {
    if (!scope) continue;
    for (const element of [...scope.children]) {
      if (element.localName === "SegmentList" || element.localName === "SegmentBase") {
        const initialization = [...element.children].find((c) => c.localName === "Initialization");
        const url = initialization?.getAttribute("sourceURL");
        if (url) return url;
      }
      if (element.localName === "SegmentTemplate") {
        const template = element.getAttribute("initialization");
        if (template) {
          return template
            .replace(/\$RepresentationID\$/g, representation.getAttribute("id") ?? "")
            .replace(/\$Bandwidth\$/g, representation.getAttribute("bandwidth") ?? "");
        }
      }
    }
  }
  return null;
}
