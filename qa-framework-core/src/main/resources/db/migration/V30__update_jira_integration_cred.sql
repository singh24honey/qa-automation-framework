-- Update the default configuration with your values
UPDATE jira_configurations
SET
    jira_url = 'https://singh24honey.atlassian.net',
    project_key = 'SCRUM',
    secret_arn = 'arn:aws:secretsmanager:us-east-1:452284481031:secret:prod/jira/qa-bot-0qUJJy'
WHERE config_name = 'default-dev';

-- Verify the update
SELECT
    config_name,
    jira_url,
    project_key,
    enabled,
    secret_arn
FROM jira_configurations
WHERE config_name = 'default-dev';