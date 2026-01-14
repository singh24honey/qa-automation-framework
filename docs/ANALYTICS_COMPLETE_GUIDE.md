# Analytics System - Complete Guide

## Overview

The Analytics System provides comprehensive insights into test suite health, performance, and reliability through advanced analysis and metrics.

## Features

### 1. Flaky Test Detection (Part 1)

Identifies tests with inconsistent pass/fail patterns.

**Endpoint:** `GET /api/v1/analytics/flaky-tests`

**Metrics:**
- Pass rate percentage
- Flakiness score (0-100)
- Stability classification
- Common error patterns
- Recommendations

### 2. Performance Metrics (Part 1)

Track test execution duration and identify slow tests.

**Endpoint:** `GET /api/v1/analytics/performance`

**Metrics:**
- Average/Min/Max/Median duration
- Standard deviation
- Performance trend
- Performance rating (FAST/NORMAL/SLOW)

### 3. Failure Pattern Analysis (Part 2)

Cluster and analyze common failure types.

**Endpoint:** `GET /api/v1/analytics/failure-patterns`

**Insights:**
- Error type clustering
- Affected tests and browsers
- Occurrence frequency
- Targeted recommendations

### 4. Suite Health Score (Part 2)

Overall test suite quality metric (0-100).

**Endpoint:** `GET /api/v1/analytics/suite-health`

**Components:**
- Health score calculation
- Active vs flaky test counts
- Pass rate trends
- Top issues and recommendations

### 5. Analytics Dashboard (Part 2)

Complete overview combining all metrics.

**Endpoint:** `GET /api/v1/analytics/dashboard`

**Includes:**
- Suite health summary
- Top 10 flaky tests
- Top 10 slowest tests
- Top 5 failure patterns
- Daily trend analysis

## API Reference

### Query Parameters (All Endpoints)

- `startDate` (optional): Filter start date (YYYY-MM-DD)
- `endDate` (optional): Filter end date (YYYY-MM-DD)
- Default: Last 30 days

### Example Requests
```bash
# Get complete dashboard
curl -H "X-API-Key: your-key" \
  "http://localhost:8080/api/v1/analytics/dashboard"

# Get flaky tests for specific period
curl -H "X-API-Key: your-key" \
  "http://localhost:8080/api/v1/analytics/flaky-tests?startDate=2025-01-01&endDate=2025-01-31"

# Get suite health
curl -H "X-API-Key: your-key" \
  "http://localhost:8080/api/v1/analytics/suite-health"
```

## Interpreting Results

### Health Score (0-100)

- **90-100**: Excellent ��
- **80-89**: Good 🟡
- **60-79**: Fair 🟠 (needs attention)
- **<60**: Poor 🔴 (urgent action required)

**Calculation:**
- 70% from pass rate
- 30% deduction for flaky tests

### Flakiness Score (0-100)

- **0-20**: Stable ✅
- **20-50**: Minor issues ⚠️
- **50-80**: Moderate flakiness 🔸
- **80-100**: Severe flakiness ❌

**Calculation:**
- Based on deviation from 50% pass rate
- Higher score = more inconsistent behavior

### Performance Rating

- **FAST**: < 5 seconds average
- **NORMAL**: 5-30 seconds average
- **SLOW**: > 30 seconds average

### Trend Direction

- **IMPROVING**: Metrics getting better 📈
- **STABLE**: No significant change ➡️
- **DEGRADING**: Metrics getting worse 📉
- **INSUFFICIENT_DATA**: Need more executions ❓

## Best Practices

### 1. Monitor Regularly

- Check analytics dashboard weekly
- Review suite health daily
- Track trends monthly

### 2. Act on Recommendations

- Fix flaky tests immediately (score > 80)
- Investigate failure patterns
- Optimize slow tests (> 30 seconds)

### 3. Set Thresholds

- Maintain health score > 80
- Keep flaky test count < 10%
- Average pass rate > 90%

### 4. Use Trends

- Watch for DEGRADING trends
- Celebrate IMPROVING trends
- Investigate sudden changes

## Automation

### Daily Health Check
```bash
#!/bin/bash
HEALTH=$(curl -s -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/analytics/suite-health" \
  | jq '.data.overallHealthScore')

if (( $(echo "$HEALTH < 80" | bc -l) )); then
  echo "ALERT: Suite health is $HEALTH"
  # Send alert
fi
```

### Weekly Report
```bash
#!/bin/bash
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/analytics/dashboard?startDate=$(date -d '7 days ago' +%Y-%m-%d)" \
  | jq '.data' > weekly-report.json
```

## Troubleshooting

### No Data Returned

- Ensure test executions exist in database
- Check date range parameters
- Verify API key is valid

### Low Health Score

1. Review flaky tests list
2. Check failure patterns
3. Investigate performance metrics
4. Follow recommendations

### Inaccurate Trends

- Need at least 10 executions for trends
- Ensure consistent test execution
- Check date range covers enough data

## Integration with CI/CD

### Jenkins Example
```groovy
stage('Analytics Check') {
    steps {
        script {
            def health = sh(
                script: "curl -H 'X-API-Key: ${API_KEY}' ${API_URL}/analytics/suite-health | jq '.data.overallHealthScore'",
                returnStdout: true
            ).trim()
            
            if (health.toFloat() < 80) {
                error("Suite health below threshold: ${health}")
            }
        }
    }
}
```

### GitHub Actions Example
```yaml
- name: Check Suite Health
  run: |
    HEALTH=$(curl -H "X-API-Key: ${{ secrets.API_KEY }}" \
      ${{ env.API_URL }}/analytics/suite-health \
      | jq '.data.overallHealthScore')
    
    if (( $(echo "$HEALTH < 80" | bc -l) )); then
      echo "::error::Suite health is $HEALTH"
      exit 1
    fi
```

## API Response Examples

### Dashboard Response
```json
{
  "success": true,
  "data": {
    "suiteHealth": {
      "overallHealthScore": 85.5,
      "totalTests": 50,
      "activeTests": 48,
      "stableTests": 40,
      "flakyTests": 8,
      "averagePassRate": 92.5,
      "trend": "STABLE"
    },
    "flakyTests": [...],
    "slowTests": [...],
    "commonFailures": [...],
    "trends": {...}
  }
}
```

## Support

For issues or questions:
1. Check logs: `~/qa-framework/artifacts/logs/application.log`
2. Verify database connectivity
3. Ensure test executions have complete data