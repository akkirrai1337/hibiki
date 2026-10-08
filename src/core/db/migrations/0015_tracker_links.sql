CREATE TABLE `tracker_links` (
	`tracker` text NOT NULL,
	`source_id` text NOT NULL,
	`anime_id` text NOT NULL,
	`remote_id` integer,
	`remote_title` text,
	`linked_by` text NOT NULL,
	`checked_at` integer NOT NULL,
	PRIMARY KEY(`tracker`, `source_id`, `anime_id`)
);
