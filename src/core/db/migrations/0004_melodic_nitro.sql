CREATE TABLE `cached_anime` (
	`source_id` text NOT NULL,
	`anime_id` text NOT NULL,
	`anime_json` text NOT NULL,
	`cached_at` integer NOT NULL,
	PRIMARY KEY(`source_id`, `anime_id`)
);
--> statement-breakpoint
CREATE TABLE `cached_playback_groups` (
	`source_id` text NOT NULL,
	`anime_id` text NOT NULL,
	`groups_json` text NOT NULL,
	`cached_at` integer NOT NULL,
	PRIMARY KEY(`source_id`, `anime_id`)
);
--> statement-breakpoint
CREATE TABLE `downloaded_episodes` (
	`source_id` text NOT NULL,
	`anime_id` text NOT NULL,
	`group_id` text NOT NULL,
	`episode_id` text NOT NULL,
	`episode_number` integer NOT NULL,
	`episode_label` text NOT NULL,
	`file_path` text NOT NULL,
	`file_size_bytes` integer NOT NULL,
	`downloaded_at` integer NOT NULL,
	PRIMARY KEY(`source_id`, `anime_id`, `episode_id`)
);
