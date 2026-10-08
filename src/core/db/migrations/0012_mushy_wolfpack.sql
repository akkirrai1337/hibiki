CREATE TABLE `external_metadata_unresolved` (
	`source_id` text NOT NULL,
	`provider` text NOT NULL,
	`external_id` integer NOT NULL,
	`attempted_at` integer NOT NULL,
	PRIMARY KEY(`source_id`, `provider`, `external_id`)
);
