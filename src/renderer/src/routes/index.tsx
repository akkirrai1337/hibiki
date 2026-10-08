import { createFileRoute } from "@tanstack/react-router";

// The page itself is mounted persistently and loaded on demand by __root.tsx.
export const Route = createFileRoute("/")({ component: () => null });
