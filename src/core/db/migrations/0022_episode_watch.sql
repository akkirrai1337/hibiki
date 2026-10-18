CREATE TABLE `episode_watch` (
	`source_id` text NOT NULL,
	`anime_id` text NOT NULL,
	`bell` text,
	`group_id` text,
	`known_count` integer,
	`known_max` real,
	`arrivals` text DEFAULT '[]' NOT NULL,
	`waiting_since` integer,
	`next_episode_at` integer,
	`failures` integer DEFAULT 0 NOT NULL,
	`next_check_at` integer DEFAULT 0 NOT NULL,
	`checked_at` integer,
	PRIMARY KEY(`source_id`, `anime_id`)
);
--> statement-breakpoint
CREATE INDEX `episode_watch_next_check` ON `episode_watch` (`next_check_at`);