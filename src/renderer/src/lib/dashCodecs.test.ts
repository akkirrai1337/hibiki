import { describe, expect, it } from "vitest";
import { codecFromInitSegment } from "./dashCodecs";

/** An ISO BMFF box: size, type, payload. */
function box(type: string, ...payload: (Uint8Array | number[])[]): Uint8Array {
  const body = payload.flatMap((part) => Array.from(part));
  const size = 8 + body.length;
  return new Uint8Array([size >>> 24, (size >>> 16) & 255, (size >>> 8) & 255, size & 255, ...Array.from(type, (c) => c.charCodeAt(0)), ...body]);
}

/** An init segment whose one track has `entry` as its sample entry. */
function initSegment(entry: Uint8Array): Uint8Array {
  const stsd = box("stsd", [0, 0, 0, 0, 0, 0, 0, 1], entry);
  return box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd)))));
}

// The fixed part of a VisualSampleEntry (78 bytes) and of an AudioSampleEntry (28 bytes).
const visual = new Array(78).fill(0);
const audio = new Array(28).fill(0);

describe("codecFromInitSegment", () => {
  it("reads VP9 from vpcC", () => {
    expect(codecFromInitSegment(initSegment(box("vp09", visual, box("vpcC", [1, 0, 0, 0], [0, 31, 0x80, 2, 2, 2, 0, 0]))))).toBe("vp09.00.31.08");
  });

  it("reads H.264 from avcC", () => {
    expect(codecFromInitSegment(initSegment(box("avc1", visual, box("avcC", [1, 0x64, 0x00, 0x28, 0xff]))))).toBe("avc1.640028");
  });

  it("reads HEVC from hvcC", () => {
    const hvcC = [1, 0x01, 0x60, 0, 0, 0, 0x90, 0, 0, 0, 0, 0, 93];
    expect(codecFromInitSegment(initSegment(box("hvc1", visual, box("hvcC", hvcC))))).toBe("hvc1.1.6.L93.90");
  });

  it("reads AV1 from av1C", () => {
    expect(codecFromInitSegment(initSegment(box("av01", visual, box("av1C", [0x81, 0x08, 0x0c, 0]))))).toBe("av01.0.08M.08");
  });

  it("reads the AAC object type from esds", () => {
    const esds = [0, 0, 0, 0, 0x03, 25, 0, 1, 0, 0x04, 17, 0x40, 0x15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x05, 2, 0x12, 0x10];
    expect(codecFromInitSegment(initSegment(box("mp4a", audio, box("esds", esds))))).toBe("mp4a.40.2");
  });

  it("knows Opus and gives up on anything else", () => {
    expect(codecFromInitSegment(initSegment(box("Opus", audio)))).toBe("opus");
    expect(codecFromInitSegment(initSegment(box("encv", visual)))).toBeNull();
    expect(codecFromInitSegment(new Uint8Array([0, 0, 0, 8]))).toBeNull();
  });
});
