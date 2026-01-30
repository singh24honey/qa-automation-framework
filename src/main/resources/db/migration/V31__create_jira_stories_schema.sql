-- Week 9 Day 2: JIRA Stories Storage Schema
-- Migration: V41__create_jira_stories_schema.sql
-- Purpose: Store fetched JIRA stories for AI test generation

-- Main table for JIRA stories
CREATE TABLE jira_stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id UUID NOT NULL REFERENCES jira_configurations(id) ON DELETE CASCADE,

    -- JIRA identifiers
    jira_key VARCHAR(50) UNIQUE NOT NULL,  -- e.g., "QA-123"
    jira_id VARCHAR(50),                   -- JIRA internal ID

    -- Story content
    summary TEXT NOT NULL,
    description TEXT,
    acceptance_criteria TEXT,              -- Extracted AC field

    -- Metadata
    story_type VARCHAR(50),                -- Story, Task, Bug, Epic
    status VARCHAR(50),                    -- To Do, In Progress, Done
    priority VARCHAR(20),                  -- High, Medium, Low
    assignee VARCHAR(100),
    reporter VARCHAR(100),

    -- Labels and components
    labels TEXT[],                         -- Array of labels
    components TEXT[],                     -- Array of component names

    -- Timestamps from JIRA
    jira_created_at TIMESTAMP,
    jira_updated_at TIMESTAMP,

    -- Raw storage for full context
    raw_json JSONB NOT NULL,               -- Full JIRA API response

    -- Tracking
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Custom fields support
    custom_fields JSONB                    -- Store custom field mappings
);

-- Performance indexes
CREATE INDEX idx_jira_stories_key ON jira_stories(jira_key);
CREATE INDEX idx_jira_stories_config ON jira_stories(config_id);
CREATE INDEX idx_jira_stories_status ON jira_stories(status);
CREATE INDEX idx_jira_stories_type ON jira_stories(story_type);
CREATE INDEX idx_jira_stories_fetched ON jira_stories(fetched_at DESC);

-- JSONB indexes for querying
CREATE INDEX idx_jira_stories_raw_json ON jira_stories USING GIN (raw_json);
CREATE INDEX idx_jira_stories_custom_fields ON jira_stories USING GIN (custom_fields);

-- Array indexes for labels/components
CREATE INDEX idx_jira_stories_labels ON jira_stories USING GIN (labels);
CREATE INDEX idx_jira_stories_components ON jira_stories USING GIN (components);

-- Trigger for updated_at timestamp
CREATE OR REPLACE FUNCTION update_jira_stories_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_jira_stories_updated_at
    BEFORE UPDATE ON jira_stories
    FOR EACH ROW
    EXECUTE FUNCTION update_jira_stories_updated_at();

-- Comments for documentation
COMMENT ON TABLE jira_stories IS 'Stores JIRA stories fetched for AI-powered test generation';
COMMENT ON COLUMN jira_stories.jira_key IS 'JIRA story key (e.g., QA-123)';
COMMENT ON COLUMN jira_stories.raw_json IS 'Complete JIRA API response for reference';
COMMENT ON COLUMN jira_stories.acceptance_criteria IS 'Extracted acceptance criteria text';
COMMENT ON COLUMN jira_stories.custom_fields IS 'Flexible storage for JIRA custom fields';