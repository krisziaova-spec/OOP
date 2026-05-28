-- Run this once if your existing local database was created before profile photos were added.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS user_photo_url TEXT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS user_photo_storage_path VARCHAR(500);
