// Test support for device sync: two real databases, migrated as the Android build migrates them
// (core/db/migrate.ts over drizzle's async sqlite-proxy driver), standing in for two devices, and a
// fake network between a computer and a phone. node:sqlite rather than better-sqlite3: the latter is
// built for Electron and does not load in plain Node. Imported by tests only.
import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { DatabaseSync, type SQLInputValue } from "node:sqlite";
import { fileURLToPath } from "node:url";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import * as schema from "../db/schema";
import { applyMigrations, type MigrationJournal } from "../db/migrate";
import { installPlatform } from "../platform";
import type { Platform } from "../../platform/types";
import { applyChanges, collectChanges } from "./changes";
import { DISCOVERY_PROBE } from "./protocol";
import { answerProbe, handleSyncRequest } from "./server";

const migrationsDir = fileURLToPath(new URL("../db/migrations", import.meta.url));
export const journal = JSON.parse(readFileSync(`${migrationsDir}/meta/_journal.json`, "utf-8")) as MigrationJournal;
export const sqlByTag = Object.fromEntries(
  readdirSync(migrationsDir).filter((name) => name.endsWith(".sql")).map((name) => [name.replace(/\.sql$/, ""), readFileSync(`${migrationsDir}/${name}`, "utf-8")]),
);

/** Runs one statement; a test can watch (or interleave with) the queries a device makes. */
export type QueryHook = (sql: string) => void | Promise<void>;

export async function openDevice(onQuery?: QueryHook) {
  const sqlite = new DatabaseSync(":memory:");
  const values = (sql: string, params: unknown[]) => sqlite.prepare(sql).all(...(params as SQLInputValue[])).map((row) => Object.values(row));
  const db = drizzle(async (sql, params, method) => {
    if (onQuery) await onQuery(sql);
    if (method === "run") {
      sqlite.prepare(sql).run(...(params as SQLInputValue[]));
      return { rows: [] };
    }
    const rows = values(sql, params);
    return { rows: method === "get" ? (rows[0] as unknown as unknown[]) : rows };
  }, { schema });
  await applyMigrations(journal, sqlByTag, {
    exec: async (sql) => void sqlite.exec(sql),
    values: async (sql) => values(sql, []),
  }, async (text) => createHash("sha256").update(text).digest("hex"));
  return db;
}
export type Db = Awaited<ReturnType<typeof openDevice>>;

/** Makes `db` the database core code sees, as if it were running on that device. */
export function on(db: Db): Db {
  installPlatform({ db: { get: () => db } } as unknown as Platform);
  return db;
}

/** Everything one device has changed since `since`, applied on the other, as a sync round does. */
export async function push(from: Db, to: Db, since = 0): Promise<number> {
  let cursor = since;
  for (;;) {
    on(from);
    const batch = await collectChanges(cursor);
    on(to);
    await applyChanges(batch);
    cursor = batch.upTo;
    if (!batch.more) return cursor;
  }
}

/**
 * The computer's request handler behind a fake network: each request runs with the computer's
 * database installed, then the phone's comes back. `state` lets a test break the line: cut it
 * altogether, drop one kind of request, lose an answer after the computer has acted on the request,
 * or move the computer to another address.
 */
export function connect(computer: Db, phoneDb: Db) {
  const events: string[] = [];
  const state = {
    reachable: true,
    /** Every request that reached the line, as sent. */
    requests: [] as string[],
    /** The computer's answers, as sent back (the ones that arrived and the ones that were lost). */
    answers: [] as string[],
    /** How many more requests get through before the line goes down; null for no limit. */
    budget: null as number | null,
    /** Requests of this type (pair-hello, sync, unpair...) never arrive. */
    failType: null as string | null,
    /** Requests of this type are carried out, but their answer is lost. */
    loseAnswerOf: null as string | null,
    /** Hosts nothing answers at. */
    deadHosts: new Set<string>(),
    /** Where discovery says the computer is. */
    computerHost: "10.0.0.2",
  };
  const base = {
    secureStore: { isAvailable: async () => true, encrypt: async (text: string) => `x${text}`, decrypt: async (text: string) => text.slice(1) },
    events: { emit: (_channel: string, payload: unknown) => events.push((payload as { what: string }).what) },
  };
  const asComputer = () => installPlatform({ ...base, db: { get: () => computer } } as unknown as Platform);
  const asPhone = () => installPlatform({
    ...base,
    db: { get: () => phoneDb },
    syncTransport: {
      deviceName: async () => "Test phone",
      // The computer answers as it does on the network: its pairing probe only while a window is open.
      discover: async (_timeoutMs: number, probe?: string) => {
        asComputer();
        const answer = await answerProbe(probe ?? DISCOVERY_PROBE, "Test PC", 47652, "computer");
        asPhone();
        return answer ? [{ deviceId: answer.deviceId, name: answer.name, host: state.computerHost, port: answer.port, kind: answer.kind }] : [];
      },
      request: async (host: string, _port: number, message: string) => {
        if (!state.reachable || state.deadHosts.has(host)) throw new Error("connection refused");
        if (state.budget !== null) {
          if (state.budget <= 0) throw new Error("connection lost");
          state.budget--;
        }
        const type = (JSON.parse(message) as { type: string }).type;
        if (state.failType === type) throw new Error("connection reset");
        state.requests.push(message);
        asComputer();
        let answer: string;
        try {
          answer = await handleSyncRequest(message, "10.0.0.5", "Test PC");
        } finally {
          asPhone();
        }
        state.answers.push(answer);
        if (state.loseAnswerOf === type) throw new Error("connection reset before the answer");
        return answer;
      },
    },
  } as unknown as Platform);
  return { asComputer, asPhone, events, state };
}
