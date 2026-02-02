-- 1️⃣ Drop GIN indexes (required before type change)
DROP INDEX IF EXISTS idx_jira_stories_components;
DROP INDEX IF EXISTS idx_jira_stories_labels;

-- 2️⃣ Convert arrays → text using your delimiter
ALTER TABLE jira_stories
ALTER COLUMN components TYPE TEXT
USING array_to_string(components, '|||');

ALTER TABLE jira_stories
ALTER COLUMN labels TYPE TEXT
USING array_to_string(labels, '|||');

-- 3️⃣ Optional: add normal BTREE indexes (only if you really need them)
-- CREATE INDEX idx_jira_stories_components_text ON jira_stories (components);
-- CREATE INDEX idx_jira_stories_labels_text ON jira_stories (labels);