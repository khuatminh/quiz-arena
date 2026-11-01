-- Optional data conversion for an existing schema 5 installation.
-- Deploy the server supporting versioned system banks before applying this file.
-- Stop the server and back up the database/media first. Safe to run again.
-- Preserve IDs, publication versions, draft contents, images and match history.
-- Unpublished/inactive drafts stay inactive; they are not exposed in the lobby.
START TRANSACTION;
UPDATE QUIZZES
SET quiz_source='SYSTEM', updated_at=UTC_TIMESTAMP(3)
WHERE quiz_source='COMMUNITY';
-- Imported descriptions may still advertise the old unranked behavior.
UPDATE QUIZZES
SET description=REPLACE(description, 'Không tính xếp hạng.', 'Quiz hệ thống, có tính xếp hạng.')
WHERE quiz_source='SYSTEM' AND public_version_id IS NOT NULL;
COMMIT;
