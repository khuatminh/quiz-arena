-- Upgrade schema 4 to 5 once. Existing questions, matches and scores are preserved.
-- The existing JSON columns store range settings/keys and normalized image regions.
SET @drop_question_type_check=(SELECT CONCAT('ALTER TABLE QUESTIONS ',GROUP_CONCAT(CONCAT('DROP CHECK `',tc.CONSTRAINT_NAME,'`') SEPARATOR ',')) FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='QUESTIONS' AND tc.CONSTRAINT_TYPE='CHECK' AND cc.CHECK_CLAUSE LIKE '%question_type%');
PREPARE interactive_migration FROM @drop_question_type_check;
EXECUTE interactive_migration;
DEALLOCATE PREPARE interactive_migration;
ALTER TABLE QUESTIONS ADD CONSTRAINT chk_question_type CHECK(question_type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','SHORT_ANSWER','REORDER','RANGE','PINPOINT'));
UPDATE SCHEMA_METADATA SET version=5;
