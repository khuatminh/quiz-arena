-- Upgrade a version 3 schema once, after 003: lifelines (quyền trợ giúp).
-- x2 doubles one question's points (at most 6) and is usable once per match, so a match can
-- score 3 points above its round count times 3 (ranked 33, community at most 153).
-- Discover the generated earned_points CHECK name rather than depending on name allocation.
SET @drop_answer_checks=(SELECT CONCAT('ALTER TABLE MATCH_ANSWERS ',GROUP_CONCAT(CONCAT('DROP CHECK `',tc.CONSTRAINT_NAME,'`') SEPARATOR ',')) FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='MATCH_ANSWERS' AND tc.CONSTRAINT_TYPE='CHECK' AND cc.CHECK_CLAUSE LIKE '%earned_points%between%');
PREPARE lifeline_migration FROM @drop_answer_checks;
EXECUTE lifeline_migration;
DEALLOCATE PREPARE lifeline_migration;
ALTER TABLE MATCH_ANSWERS ADD CONSTRAINT chk_ma_points CHECK(earned_points BETWEEN 0 AND 6);
ALTER TABLE MATCHES DROP CHECK chk_matches_scores;
ALTER TABLE MATCHES ADD CONSTRAINT chk_matches_scores CHECK(score1 BETWEEN 0 AND 153 AND score2 BETWEEN 0 AND 153);
UPDATE SCHEMA_METADATA SET version=4;
