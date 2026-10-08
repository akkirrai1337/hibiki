CREATE TABLE `cached_source_queries` (
	`query_key` text PRIMARY KEY NOT NULL,
	`titles_json` text NOT NULL,
	`cached_at` integer NOT NULL
);
