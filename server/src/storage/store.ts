import * as path from "node:path";
import { config as defaultConfig, type Config } from "../config";
import { openDatabase, type DatabaseManager } from "./db";
import { migrateLegacyData, computeHash } from "./migration";
import { RulesRepository } from "./rulesRepo";
import { StatsRepository } from "./statsRepo";

export function createStore(
  customConfig: Config = defaultConfig,
  options: { flushDelayMs?: number } = {}
) {
  const isDefault = customConfig === defaultConfig;
  let config: Config;

  if (isDefault) {
    config = defaultConfig;
  } else {
    config = { ...defaultConfig, ...customConfig };
    if (customConfig.DATA_DIR && customConfig.DATA_DIR !== defaultConfig.DATA_DIR) {
      if (customConfig.DB_FILE === undefined || customConfig.DB_FILE === defaultConfig.DB_FILE) {
        config.DB_FILE = path.join(customConfig.DATA_DIR, "adskip.db");
      }
      if (customConfig.RULES_FILE === undefined || customConfig.RULES_FILE === defaultConfig.RULES_FILE) {
        config.RULES_FILE = path.join(customConfig.DATA_DIR, "rules.json");
      }
      if (customConfig.STATS_DIR === undefined || customConfig.STATS_DIR === defaultConfig.STATS_DIR) {
        config.STATS_DIR = path.join(customConfig.DATA_DIR, "stats");
      }
      if (customConfig.BACKUP_DIR === undefined || customConfig.BACKUP_DIR === defaultConfig.BACKUP_DIR) {
        config.BACKUP_DIR = path.join(customConfig.DATA_DIR, "backups");
      }
    } else if (customConfig.STATS_DIR && customConfig.STATS_DIR !== defaultConfig.STATS_DIR) {
      if (customConfig.DB_FILE === undefined || customConfig.DB_FILE === defaultConfig.DB_FILE) {
        config.DB_FILE = path.join(path.dirname(customConfig.STATS_DIR), "adskip.db");
      }
    } else if (customConfig.RULES_FILE && customConfig.RULES_FILE !== defaultConfig.RULES_FILE) {
      if (customConfig.DB_FILE === undefined || customConfig.DB_FILE === defaultConfig.DB_FILE) {
        config.DB_FILE = path.join(path.dirname(customConfig.RULES_FILE), "adskip.db");
      }
    }
  }

  const dbManager: DatabaseManager = openDatabase(config.DB_FILE);
  const db = dbManager.db;

  // 执行旧版文件数据迁移（若 SQLite 表为空）
  migrateLegacyData(db, config);

  const rulesRepo = new RulesRepository(db, config);
  const statsRepo = new StatsRepository(db, config, options);

  // 定期清理过期统计（6 小时一次）
  const statsCleanupTimer = setInterval(() => {
    try {
      statsRepo.cleanupOldStats();
    } catch {
      /* ignore */
    }
  }, 6 * 60 * 60 * 1000);
  statsCleanupTimer.unref?.();

  function dispose(): void {
    clearInterval(statsCleanupTimer);
    statsRepo.dispose();
    dbManager.close();
  }

  const _internal = {
    computeHash,
    listStatsDays: () => statsRepo.listStatsDays(),
  };

  return {
    computeHash,
    getRules: () => rulesRepo.getRules(),
    saveRules: (cleaned: any) => rulesRepo.saveRules(cleaned),
    recordSkip: (pkgRaw: string, labelRaw: string, channelRaw: string, day?: string) =>
      statsRepo.recordSkip(pkgRaw, labelRaw, channelRaw, day),
    recordBatch: (deviceId: string, events: any[]) =>
      statsRepo.recordBatch(deviceId, events),
    storageHealthy: () => statsRepo.storageHealthy(),
    dispose,
    statsSummary: () => statsRepo.statsSummary(),
    cleanupOldStats: (now?: Date) => statsRepo.cleanupOldStats(now),
    _resetSummaryCacheForTests: () => statsRepo._resetSummaryCacheForTests(),
    _resetStatsCacheForTests: () => statsRepo._resetStatsCacheForTests(),
    _resetRulesCacheForTests: () => rulesRepo.resetCache(),
    _rotateStatsBackupForTests: (day: string) => statsRepo.rotateStatsBackup(day),
    flush: () => statsRepo.flush(),
    _internal,
  };
}

const defaultStoreInstance = createStore(defaultConfig);

export const computeHashExport = computeHash;
export { computeHashExport as computeHash };
export const getRules = () => defaultStoreInstance.getRules();
export const saveRules = (cleaned: any) => defaultStoreInstance.saveRules(cleaned);
export const recordSkip = (pkgRaw: string, labelRaw: string, channelRaw: string, day?: string) =>
  defaultStoreInstance.recordSkip(pkgRaw, labelRaw, channelRaw, day);
export const recordBatch = (deviceId: string, events: any[]) =>
  defaultStoreInstance.recordBatch(deviceId, events);
export const storageHealthy = () => defaultStoreInstance.storageHealthy();
export const dispose = () => defaultStoreInstance.dispose();
export const statsSummary = () => defaultStoreInstance.statsSummary();
export const cleanupOldStats = (now?: Date) => defaultStoreInstance.cleanupOldStats(now);
export const _resetSummaryCacheForTests = () => defaultStoreInstance._resetSummaryCacheForTests();
export const _resetStatsCacheForTests = () => defaultStoreInstance._resetStatsCacheForTests();
export const _resetRulesCacheForTests = () => defaultStoreInstance._resetRulesCacheForTests();
export const _rotateStatsBackupForTests = (day: string) => defaultStoreInstance._rotateStatsBackupForTests(day);
export const flush = () => defaultStoreInstance.flush();
export const _internal = defaultStoreInstance._internal;
