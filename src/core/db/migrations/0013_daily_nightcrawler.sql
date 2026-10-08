CREATE TABLE `title_ratings` (
	`source_id` text NOT NULL,
	`anime_id` text NOT NULL,
	`rating` integer NOT NULL,
	`rated_at` integer NOT NULL,
	PRIMARY KEY(`source_id`, `anime_id`)
);
