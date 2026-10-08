CREATE TABLE `daily_activity` (
	`date` text PRIMARY KEY NOT NULL,
	`watched_ms` integer DEFAULT 0 NOT NULL,
	`completed_count` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE TABLE `library` (
	`anime_id` text NOT NULL,
	`source_id` text NOT NULL,
	`category` text NOT NULL,
	`added_at` integer NOT NULL,
	`anime_json` text NOT NULL,
	PRIMARY KEY(`source_id`, `anime_id`)
);
--> statement-breakpoint
CREATE TABLE `watch_progress` (
	`source_id` text NOT NULL,
	`title_id` text NOT NULL,
	`episode_id` text NOT NULL,
	`episode_number` integer NOT NULL,
	`quality` text,
	`position_ms` integer NOT NULL,
	`duration_ms` integer NOT NULL,
	`updated_at` integer NOT NULL,
	PRIMARY KEY(`source_id`, `title_id`, `episode_id`)
);
