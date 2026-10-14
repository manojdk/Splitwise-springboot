-- Every access token carries the user's token version; raising it signs out every session at once (a password
-- change, "sign out of all devices"). Tokens issued before this column existed count as version 0.
ALTER TABLE app_user ADD COLUMN token_version INT NOT NULL DEFAULT 0;
