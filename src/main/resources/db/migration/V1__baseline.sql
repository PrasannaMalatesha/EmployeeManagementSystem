-- V1 baseline: Postgres extensions required by later migrations.
--
-- Real schema lands in later milestones (M1: identity, M2: employees, ...).
-- Keeping V1 to extensions only means the earliest migrations that add
-- tables stay small and focused, and this file will never need editing.
--
-- Migration policy (PRD §10): every migration must be backward-compatible
-- with the currently running version. Adding NOT NULL without a default,
-- DROP COLUMN, or RENAME on a populated table is a CI failure.

-- Trigram indexing for the directory search endpoint (M2).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Case-insensitive text — used for user email uniqueness in M1.
CREATE EXTENSION IF NOT EXISTS citext;
