/**
 * Agent Helper Utilities
 *
 * Provides helper functions for agent operations, formatting, and calculations.
 */

/**
 * Get agent type display name
 *
 * @param {string} agentType - Agent type enum value
 * @returns {string} Display name
 */
export const getAgentTypeDisplayName = (agentType) => {
  const displayNames = {
    'PLAYWRIGHT_TEST_GENERATOR': 'Playwright Test Generator',
    'FLAKY_TEST_FIXER':          'Flaky Test Fixer',
    'SELF_HEALING_TEST_FIXER':   'Self-Healing Test Fixer',
    'TEST_FAILURE_ANALYZER':     'Test Failure Analyzer',
    'QUALITY_MONITOR':           'Quality Monitor',
  };
  return displayNames[agentType] || agentType;
};

/**
 * Get agent type icon
 *
 * @param {string} agentType - Agent type enum value
 * @returns {string} Emoji icon
 */
export const getAgentTypeIcon = (agentType) => {
  const icons = {
    'PLAYWRIGHT_TEST_GENERATOR': '🤖',
    'FLAKY_TEST_FIXER':          '🔧',
    'SELF_HEALING_TEST_FIXER':   '💊',
    'TEST_FAILURE_ANALYZER':     '🔬',
    'QUALITY_MONITOR':           '📊',
  };
  return icons[agentType] || '🤖';
};

/**
 * Get status color class
 *
 * @param {string} status - Agent execution status
 * @returns {string} CSS class name
 */
export const getStatusColor = (status) => {
  const colors = {
    'RUNNING': 'status-running',
    'SUCCEEDED': 'status-success',
    'FAILED': 'status-error',
    'STOPPED': 'status-stopped',
    'TIMEOUT': 'status-timeout',
    'WAITING_APPROVAL': 'status-warning',
  };
  return colors[status] || 'status-default';
};

/**
 * Get status display name
 *
 * @param {string} status - Agent execution status
 * @returns {string} Display name
 */
export const getStatusDisplayName = (status) => {
  const names = {
    'RUNNING': 'Running',
    'SUCCEEDED': 'Succeeded',
    'FAILED': 'Failed',
    'STOPPED': 'Stopped',
    'TIMEOUT': 'Timeout',
    'WAITING_APPROVAL': 'Waiting Approval',
  };
  return names[status] || status;
};

/**
 * Calculate progress percentage
 *
 * @param {number} current - Current iteration
 * @param {number} max - Max iterations
 * @returns {number} Progress percentage (0-100)
 */
export const calculateProgress = (current, max) => {
  if (!max || max === 0) return 0;
  return Math.round((current / max) * 100);
};

/**
 * Format AI cost
 *
 * @param {number} cost - Cost in dollars
 * @returns {string} Formatted cost string
 */
export const formatCost = (cost) => {
  if (cost === null || cost === undefined) return '$0.00';
  return `$${cost.toFixed(2)}`;
};

/**
 * Format duration from seconds
 *
 * @param {number} seconds - Duration in seconds
 * @returns {string} Formatted duration (e.g., "2m 35s")
 */
export const formatDuration = (seconds) => {
  if (!seconds || seconds === 0) return '0s';

  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);

  const parts = [];
  if (hours > 0) parts.push(`${hours}h`);
  if (minutes > 0) parts.push(`${minutes}m`);
  if (secs > 0 || parts.length === 0) parts.push(`${secs}s`);

  return parts.join(' ');
};

/**
 * Get action type display name
 *
 * @param {string} actionType - Action type enum value
 * @returns {string} Display name
 */
export const getActionTypeDisplayName = (actionType) => {
  const names = {
    'FETCH_JIRA_STORY': 'Fetch JIRA Story',
    'QUERY_ELEMENT_REGISTRY': 'Query Element Registry',
    'GENERATE_CODE': 'Generate Code',
    'WRITE_FILE': 'Write File',
    'CREATE_APPROVAL_REQUEST': 'Create Approval Request',
    'WAIT_FOR_APPROVAL': 'Wait for Approval',
    'CREATE_GIT_BRANCH': 'Create Git Branch',
    'COMMIT_CHANGES': 'Commit Changes',
    'CREATE_PULL_REQUEST': 'Create Pull Request',
    'EXECUTE_TEST': 'Execute Test',
    'ANALYZE_FAILURE': 'Analyze Failure',
    'SUGGEST_FIX': 'Suggest Fix',
    'EXTRACT_BROKEN_LOCATOR': 'Extract Broken Locator',
    'DISCOVER_LOCATOR': 'Discover Locator',
    'UPDATE_ELEMENT_REGISTRY': 'Update Element Registry',
  };
  return names[actionType] || actionType.replace(/_/g, ' ');
};

/**
 * Get action type icon
 *
 * @param {string} actionType - Action type enum value
 * @returns {string} Emoji icon
 */
export const getActionTypeIcon = (actionType) => {
  const icons = {
    'FETCH_JIRA_STORY': '📋',
    'QUERY_ELEMENT_REGISTRY': '🔍',
    'GENERATE_CODE': '⚡',
    'WRITE_FILE': '💾',
    'CREATE_APPROVAL_REQUEST': '✋',
    'WAIT_FOR_APPROVAL': '⏳',
    'CREATE_GIT_BRANCH': '🌿',
    'COMMIT_CHANGES': '📝',
    'CREATE_PULL_REQUEST': '🔀',
    'EXECUTE_TEST': '▶️',
    'ANALYZE_FAILURE': '🔬',
    'SUGGEST_FIX': '💡',
    'EXTRACT_BROKEN_LOCATOR': '🔎',
    'DISCOVER_LOCATOR': '🎯',
    'UPDATE_ELEMENT_REGISTRY': '📚',
  };
  return icons[actionType] || '⚙️';
};

/**
 * Get action status icon
 *
 * @param {boolean} success - Action success status
 * @param {boolean} requiredApproval - Whether action required approval
 * @returns {string} Status icon
 */
export const getActionStatusIcon = (success, requiredApproval) => {
  if (requiredApproval) return '⏳';
  return success ? '✓' : '✗';
};

/**
 * Parse JSON safely
 *
 * @param {string} jsonString - JSON string
 * @returns {Object|null} Parsed object or null
 */
export const safeParseJSON = (jsonString) => {
  try {
    return JSON.parse(jsonString);
  } catch (error) {
    return null;
  }
};

/**
 * Truncate text
 *
 * @param {string} text - Text to truncate
 * @param {number} maxLength - Maximum length
 * @returns {string} Truncated text
 */
export const truncateText = (text, maxLength = 100) => {
  if (!text) return '';
  if (text.length <= maxLength) return text;
  return text.substring(0, maxLength) + '...';
};

/**
 * Validate JIRA key format
 *
 * @param {string} jiraKey - JIRA key to validate
 * @returns {boolean} True if valid
 */
export const isValidJiraKey = (jiraKey) => {
  const jiraKeyPattern = /^[A-Z]+-\d+$/;
  return jiraKeyPattern.test(jiraKey);
};

/**
 * Get framework display name
 *
 * @param {string} framework - Framework enum value
 * @returns {string} Display name
 */
export const getFrameworkDisplayName = (framework) => {
  const names = {
    'PLAYWRIGHT': 'Playwright',
    'CUCUMBER': 'Cucumber',
    'TESTNG': 'TestNG',
  };
  return names[framework] || framework;
};

/**
 * Get framework icon
 *
 * @param {string} framework - Framework enum value
 * @returns {string} Emoji icon
 */
export const getFrameworkIcon = (framework) => {
  const icons = {
    'PLAYWRIGHT': '🎭',
    'CUCUMBER': '🥒',
    'TESTNG': '🧪',
  };
  return icons[framework] || '🔧';
};