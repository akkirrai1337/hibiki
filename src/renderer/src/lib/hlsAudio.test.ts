import { describe, expect, it } from "vitest";
import { audioTrackLabel, matchAudioTrack } from "./hlsAudio";

const kaa = [{ name: "English", lang: "eng" }, { name: "Japanese", lang: "jpn" }];

describe("matchAudioTrack", () => {
  it("finds the dub by its name", () => {
    expect(matchAudioTrack(kaa, "English")).toBe(0);
    expect(matchAudioTrack(kaa, "japanese")).toBe(1);
  });

  it("finds it from a longer dub name or by language code alone", () => {
    expect(matchAudioTrack(kaa, "English (dub)")).toBe(0);
    expect(matchAudioTrack([{ lang: "en" }, { lang: "ja" }], "Japanese")).toBe(1);
  });

  it("names nothing when nothing fits", () => {
    expect(matchAudioTrack(kaa, "Spanish")).toBe(-1);
    expect(matchAudioTrack(kaa, null)).toBe(-1);
  });
});

describe("audioTrackLabel", () => {
  it("uses the name, else the language, else a number", () => {
    expect(audioTrackLabel({ name: "Japanese" }, 0)).toBe("Japanese");
    expect(audioTrackLabel({ lang: "en" }, 0)).toBe("English");
    expect(audioTrackLabel({}, 1)).toBe("Audio 2");
  });
});
