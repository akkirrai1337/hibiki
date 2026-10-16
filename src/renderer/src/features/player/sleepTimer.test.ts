import { afterEach, describe, expect, it } from "vitest";
import { minutesLeft, sleepTimerTakesEpisodeEnd, useSleepTimerStore } from "./sleepTimer";

afterEach(() => useSleepTimerStore.getState().clear());

describe("sleep timer", () => {
  it("counts minutes from now and rounds the last one up", () => {
    const before = Date.now();
    useSleepTimerStore.getState().set(30);
    const { endsAt } = useSleepTimerStore.getState();
    expect(endsAt! - before).toBeGreaterThanOrEqual(30 * 60_000);
    expect(minutesLeft(endsAt!, endsAt! - 29 * 60_000 - 1)).toBe(30);
    expect(minutesLeft(endsAt!, endsAt! - 5_000)).toBe(1);
  });

  it("stops one episode end, then is off", () => {
    useSleepTimerStore.getState().set("episode");
    expect(useSleepTimerStore.getState().endsAt).toBeNull();
    expect(sleepTimerTakesEpisodeEnd()).toBe(true);
    expect(useSleepTimerStore.getState().choice).toBe("off");
    expect(sleepTimerTakesEpisodeEnd()).toBe(false);
  });

  it("leaves episode ends alone for a minutes timer", () => {
    useSleepTimerStore.getState().set(15);
    expect(sleepTimerTakesEpisodeEnd()).toBe(false);
    expect(useSleepTimerStore.getState().choice).toBe(15);
  });
});
