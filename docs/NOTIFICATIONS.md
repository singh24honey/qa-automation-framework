# Notification System Documentation

## Overview

The QA Framework notification system provides multi-channel notifications for test execution events.

## Supported Channels

### Email
- SMTP-based email notifications
- HTML templates supported
- Configurable recipients per test

### Slack
- Incoming webhook integration
- Rich message formatting
- Color-coded based on event type

### Webhooks
- Generic HTTP POST webhooks
- JSON payload
- Configurable timeout

## Configuration

### Email Setup (Gmail Example)

1. Enable 2-factor authentication on Gmail
2. Generate app password: https://myaccount.google.com/apppasswords
3. Update `application-dev.yml`:
```yaml
notification:
  email:
    enabled: true
    from: your-email@gmail.com
    smtp:
      host: smtp.gmail.com
      port: 587
      username: your-email@gmail.com
      password: your-app-password
```

### Slack Setup

1. Create Slack incoming webhook: https://api.slack.com/messaging/webhooks
2. Copy webhook URL
3. Update `application-dev.yml`:
```yaml
notification:
  slack:
    enabled: true
    webhook-url: https://hooks.slack.com/services/YOUR/WEBHOOK/URL
    channel: '#qa-alerts'
```

## Notification Events

- `TEST_STARTED` - Test execution begins
- `TEST_COMPLETED` - Test execution finishes
- `TEST_FAILED` - Test execution fails
- `TEST_RECOVERED` - Test passes after previous failure
- `TEST_CANCELLED` - Test execution cancelled
- `TEST_TIMEOUT` - Test execution timeout
- `SYSTEM_ERROR` - System-level error

## API Usage

### Send Manual Notification
```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "X-API-Key: YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "event": "TEST_COMPLETED",
    "channels": ["EMAIL", "SLACK"],
    "testId": "uuid",
    "testName": "Login Test",
    "recipients": ["user@example.com"],
    "data": {
      "status": "PASSED"
    }
  }'
```

### Get Notification History
```bash
curl -X GET http://localhost:8080/api/v1/notifications/history/{executionId} \
  -H "X-API-Key: YOUR_KEY"
```

## Retry Logic

- Failed notifications are automatically retried
- Default: 3 retry attempts
- Retry delay: 10 seconds
- Retry runs every 5 minutes via scheduler

## Testing

Use webhook.site for testing webhooks:
1. Visit https://webhook.site
2. Copy your unique URL
3. Use it in webhook notifications
4. View received payloads in real-time