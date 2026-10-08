CREATE TABLE `sync_peers` (
	`device_id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`key_ciphertext` text NOT NULL,
	`sent_seq` integer DEFAULT 0 NOT NULL,
	`received_seq` integer DEFAULT 0 NOT NULL,
	`last_address` text,
	`last_sync_at` integer,
	`paired_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `sync_state` (
	`key` text PRIMARY KEY NOT NULL,
	`value` text NOT NULL
);
--> statement-breakpoint
CREATE TABLE `sync_tombstones` (
	`tbl` text NOT NULL,
	`key` text NOT NULL,
	`deleted_at` integer NOT NULL,
	`change_seq` integer DEFAULT 0 NOT NULL,
	PRIMARY KEY(`tbl`, `key`)
);
--> statement-breakpoint
CREATE INDEX `sync_tombstones_change_seq` ON `sync_tombstones` (`change_seq`);--> statement-breakpoint
INSERT INTO `sync_state` ("key", "value") VALUES ('device_id', lower(hex(randomblob(16))));--> statement-breakpoint
INSERT INTO `sync_state` ("key", "value") VALUES ('seq', '1');--> statement-breakpoint
PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_daily_activity` (
	`date` text NOT NULL,
	`device_id` text DEFAULT '' NOT NULL,
	`watched_ms` integer DEFAULT 0 NOT NULL,
	`completed_count` integer DEFAULT 0 NOT NULL,
	`change_seq` integer DEFAULT 0 NOT NULL,
	PRIMARY KEY(`date`, `device_id`)
);
--> statement-breakpoint
INSERT INTO `__new_daily_activity`("date", "device_id", "watched_ms", "completed_count", "change_seq") SELECT "date", (SELECT "value" FROM `sync_state` WHERE "key" = 'device_id'), "watched_ms", "completed_count", `rowid` FROM `daily_activity`;--> statement-breakpoint
DROP TABLE `daily_activity`;--> statement-breakpoint
ALTER TABLE `__new_daily_activity` RENAME TO `daily_activity`;--> statement-breakpoint
PRAGMA foreign_keys=ON;--> statement-breakpoint
CREATE INDEX `daily_activity_change_seq` ON `daily_activity` (`change_seq`);--> statement-breakpoint
ALTER TABLE `library` ADD `updated_at` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE `library` ADD `change_seq` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
CREATE INDEX `library_change_seq` ON `library` (`change_seq`);--> statement-breakpoint
ALTER TABLE `title_ratings` ADD `change_seq` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
CREATE INDEX `title_ratings_change_seq` ON `title_ratings` (`change_seq`);--> statement-breakpoint
ALTER TABLE `watch_progress` ADD `change_seq` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
CREATE INDEX `watch_progress_change_seq` ON `watch_progress` (`change_seq`);--> statement-breakpoint
ALTER TABLE `xp_events` ADD `uid` text;--> statement-breakpoint
ALTER TABLE `xp_events` ADD `change_seq` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
CREATE UNIQUE INDEX `xp_events_uid` ON `xp_events` (`uid`);--> statement-breakpoint
CREATE INDEX `xp_events_change_seq` ON `xp_events` (`change_seq`);--> statement-breakpoint
UPDATE `library` SET `updated_at` = `added_at`, `change_seq` = `rowid`;--> statement-breakpoint
UPDATE `watch_progress` SET `change_seq` = `rowid`;--> statement-breakpoint
UPDATE `title_ratings` SET `change_seq` = `rowid`;--> statement-breakpoint
UPDATE `xp_events` SET `uid` = lower(hex(randomblob(16))), `change_seq` = `id`;--> statement-breakpoint
UPDATE `sync_state` SET `value` = CAST(max(1, (SELECT ifnull(max(`change_seq`), 0) FROM `library`), (SELECT ifnull(max(`change_seq`), 0) FROM `watch_progress`), (SELECT ifnull(max(`change_seq`), 0) FROM `title_ratings`), (SELECT ifnull(max(`change_seq`), 0) FROM `xp_events`), (SELECT ifnull(max(`change_seq`), 0) FROM `daily_activity`)) AS TEXT) WHERE `key` = 'seq';--> statement-breakpoint
CREATE TRIGGER `library_sync_insert` AFTER INSERT ON `library` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `library` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq'), `updated_at` = CASE WHEN NEW.`updated_at` = 0 THEN CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) ELSE `updated_at` END WHERE `source_id` = NEW.`source_id` AND `anime_id` = NEW.`anime_id`; DELETE FROM `sync_tombstones` WHERE `tbl` = 'library' AND `key` = NEW.`source_id` || char(31) || NEW.`anime_id`; END;--> statement-breakpoint
CREATE TRIGGER `library_sync_update` AFTER UPDATE ON `library` WHEN NEW.`change_seq` = OLD.`change_seq` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `library` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq'), `updated_at` = CASE WHEN NEW.`updated_at` = OLD.`updated_at` AND NEW.`category` IS NOT OLD.`category` THEN CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) ELSE `updated_at` END WHERE `source_id` = NEW.`source_id` AND `anime_id` = NEW.`anime_id`; END;--> statement-breakpoint
CREATE TRIGGER `library_sync_delete` AFTER DELETE ON `library` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; INSERT OR REPLACE INTO `sync_tombstones` (`tbl`, `key`, `deleted_at`, `change_seq`) VALUES ('library', OLD.`source_id` || char(31) || OLD.`anime_id`, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq')); END;--> statement-breakpoint
CREATE TRIGGER `watch_progress_sync_insert` AFTER INSERT ON `watch_progress` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `watch_progress` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq') WHERE `source_id` = NEW.`source_id` AND `title_id` = NEW.`title_id` AND `episode_id` = NEW.`episode_id`; DELETE FROM `sync_tombstones` WHERE `tbl` = 'watch_progress' AND `key` = NEW.`source_id` || char(31) || NEW.`title_id` || char(31) || NEW.`episode_id`; END;--> statement-breakpoint
CREATE TRIGGER `watch_progress_sync_update` AFTER UPDATE ON `watch_progress` WHEN NEW.`change_seq` = OLD.`change_seq` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `watch_progress` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq') WHERE `source_id` = NEW.`source_id` AND `title_id` = NEW.`title_id` AND `episode_id` = NEW.`episode_id`; END;--> statement-breakpoint
CREATE TRIGGER `watch_progress_sync_delete` AFTER DELETE ON `watch_progress` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; INSERT OR REPLACE INTO `sync_tombstones` (`tbl`, `key`, `deleted_at`, `change_seq`) VALUES ('watch_progress', OLD.`source_id` || char(31) || OLD.`title_id` || char(31) || OLD.`episode_id`, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq')); END;--> statement-breakpoint
CREATE TRIGGER `title_ratings_sync_insert` AFTER INSERT ON `title_ratings` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `title_ratings` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq') WHERE `source_id` = NEW.`source_id` AND `anime_id` = NEW.`anime_id`; DELETE FROM `sync_tombstones` WHERE `tbl` = 'title_ratings' AND `key` = NEW.`source_id` || char(31) || NEW.`anime_id`; END;--> statement-breakpoint
CREATE TRIGGER `title_ratings_sync_update` AFTER UPDATE ON `title_ratings` WHEN NEW.`change_seq` = OLD.`change_seq` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `title_ratings` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq') WHERE `source_id` = NEW.`source_id` AND `anime_id` = NEW.`anime_id`; END;--> statement-breakpoint
CREATE TRIGGER `title_ratings_sync_delete` AFTER DELETE ON `title_ratings` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; INSERT OR REPLACE INTO `sync_tombstones` (`tbl`, `key`, `deleted_at`, `change_seq`) VALUES ('title_ratings', OLD.`source_id` || char(31) || OLD.`anime_id`, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq')); END;--> statement-breakpoint
CREATE TRIGGER `xp_events_sync_insert` AFTER INSERT ON `xp_events` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `xp_events` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq'), `uid` = ifnull(NEW.`uid`, lower(hex(randomblob(16)))) WHERE `id` = NEW.`id`; DELETE FROM `sync_tombstones` WHERE `tbl` = 'xp_events' AND `key` = (SELECT `uid` FROM `xp_events` WHERE `id` = NEW.`id`); END;--> statement-breakpoint
CREATE TRIGGER `xp_events_sync_update` AFTER UPDATE ON `xp_events` WHEN NEW.`change_seq` = OLD.`change_seq` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `xp_events` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq') WHERE `id` = NEW.`id`; END;--> statement-breakpoint
CREATE TRIGGER `xp_events_sync_delete` AFTER DELETE ON `xp_events` WHEN OLD.`uid` IS NOT NULL BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; INSERT OR REPLACE INTO `sync_tombstones` (`tbl`, `key`, `deleted_at`, `change_seq`) VALUES ('xp_events', OLD.`uid`, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq')); END;--> statement-breakpoint
CREATE TRIGGER `daily_activity_sync_insert` AFTER INSERT ON `daily_activity` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `daily_activity` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq') WHERE `date` = NEW.`date` AND `device_id` = NEW.`device_id`; DELETE FROM `sync_tombstones` WHERE `tbl` = 'daily_activity' AND `key` = NEW.`date` || char(31) || NEW.`device_id`; END;--> statement-breakpoint
CREATE TRIGGER `daily_activity_sync_update` AFTER UPDATE ON `daily_activity` WHEN NEW.`change_seq` = OLD.`change_seq` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; UPDATE `daily_activity` SET `change_seq` = (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq') WHERE `date` = NEW.`date` AND `device_id` = NEW.`device_id`; END;--> statement-breakpoint
CREATE TRIGGER `daily_activity_sync_delete` AFTER DELETE ON `daily_activity` BEGIN UPDATE `sync_state` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = 'seq'; INSERT OR REPLACE INTO `sync_tombstones` (`tbl`, `key`, `deleted_at`, `change_seq`) VALUES ('daily_activity', OLD.`date` || char(31) || OLD.`device_id`, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), (SELECT CAST(`value` AS INTEGER) FROM `sync_state` WHERE `key` = 'seq')); END;
