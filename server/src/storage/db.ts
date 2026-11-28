import { Database } from "bun:sqlite";
import * as fs from "node:fs";
import * as path from "node:path";
import { SCHEMA_SQL } from "./schema";

export interface DatabaseManager {
  db: Database;
  checkpoint(): void;
  close(): void;
}

export function openDatabase(dbFile: string): DatabaseManager {
  if (dbFile !== ":memory:") {
    const dir = path.dirname(dbFile);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
  }

  const db = new Database(dbFile, { create: true });
  db.run("PRAGMA journal_mode = WAL;");
  db.run("PRAGMA synchronous = NORMAL;");
  db.run("PRAGMA foreign_keys = ON;");
  db.run("PRAGMA busy_timeout = 5000;");

  db.run(SCHEMA_SQL);

  return {
    db,
    checkpoint() {
      try {
        db.run("PRAGMA wal_checkpoint(PASSIVE);");
      } catch {
        /* ignore checkpoint errors during runtime */
      }
    },
    close() {
      try {
        db.close();
      } catch {
        /* ignore close errors */
      }
    },
  };
}
