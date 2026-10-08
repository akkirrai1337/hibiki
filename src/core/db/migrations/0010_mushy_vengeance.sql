CREATE TABLE `external_metadata_matches` (
	`source_id` text NOT NULL,
	`anime_id` text NOT NULL,
	`provider` text NOT NULL,
	`external_id` integer,
	`confidence` integer,
	`manual` integer DEFAULT false NOT NULL,
	`matched_at` integer NOT NULL,
	PRIMARY KEY(`source_id`, `anime_id`, `provider`)
);
--> statement-breakpoint
CREATE TABLE `external_metadata_media` (
	`provider` text NOT NULL,
	`external_id` integer NOT NULL,
	`media_json` text NOT NULL,
	`cached_at` integer NOT NULL,
	PRIMARY KEY(`provider`, `external_id`)
);
