-- Add notification preferences to tests table
ALTER TABLE tests
ADD COLUMN notify_on_failure BOOLEAN DEFAULT true,
ADD COLUMN notify_on_success BOOLEAN DEFAULT false,
ADD COLUMN notification_channels VARCHAR(255)[] DEFAULT '{EMAIL}',
ADD COLUMN notification_recipients VARCHAR(255)[] DEFAULT '{}';