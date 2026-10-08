CREATE TABLE `xp_events` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`kind` text NOT NULL,
	`xp` integer NOT NULL,
	`created_at` integer NOT NULL
);
