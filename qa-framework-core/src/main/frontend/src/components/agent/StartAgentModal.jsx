import React, { useState, useEffect } from 'react';
import agentService from '../../services/AgentService';
import { isValidJiraKey } from '../../utils/AgentHelpers';
import './StartAgentModal.css';

const AGENT_META = {
  PLAYWRIGHT_TEST_GENERATOR: {
    goalType: 'GENERATE_PLAYWRIGHT_TEST',
    requiresJiraKey: true,
    requiresTestPicker: false,
    description: 'Generates a Playwright, Cucumber, or TestNG test from a JIRA story using AI.',
  },
  FLAKY_TEST_FIXER: {
    goalType: 'FIX_FLAKY_TEST',
    requiresJiraKey: false,
    requiresTestPicker: true,
    description: 'Detects and fixes flaky tests. Pick a specific test or choose "Scan All" to check every active test.',
  },
  SELF_HEALING_TEST_FIXER: {
    goalType: 'HEAL_BROKEN_TEST',
    requiresJiraKey: false,
    requiresTestPicker: true,
    description: 'Fixes tests with broken locators via Element Registry and AI discovery. Select a specific test or scan all.',
  },
  TEST_FAILURE_ANALYZER: {
    goalType: 'ANALYZE_TEST_FAILURES',
    requiresJiraKey: false,
    requiresTestPicker: false,
    description: 'Analyzes recent test failures, identifies patterns, and suggests improvements.',
  },
  QUALITY_MONITOR: {
    goalType: 'MONITOR_QUALITY',
    requiresJiraKey: false,
    requiresTestPicker: false,
    description: 'Monitors test quality trends and generates a report with recommendations.',
  },
};

const DEFAULT_FORM = {
  agentType: 'PLAYWRIGHT_TEST_GENERATOR',
  goalType: 'GENERATE_PLAYWRIGHT_TEST',
  jiraKey: '',
  framework: 'PLAYWRIGHT',
  testId: '',
  scanAll: true,
  errorMessage: '',
  maxIterations: 20,
  maxAICost: 5.0,
  approvalTimeoutSeconds: 3600,
};

const StartAgentModal = ({ isOpen, onClose, onAgentStarted }) => {
  const [formData, setFormData] = useState({ ...DEFAULT_FORM });
  const [validationErrors, setValidationErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [availableTests, setAvailableTests] = useState([]);
  const [testsLoading, setTestsLoading] = useState(false);
  const [testSearchQuery, setTestSearchQuery] = useState('');

  const meta = AGENT_META[formData.agentType] || {};

  useEffect(() => {
    if (isOpen) {
      setFormData({ ...DEFAULT_FORM });
      setValidationErrors({});
      setError(null);
      setShowAdvanced(false);
      setTestSearchQuery('');
    }
  }, [isOpen]);

  useEffect(() => {
    if (isOpen && meta.requiresTestPicker) {
      loadTests();
    }
  }, [formData.agentType, isOpen]);

  const loadTests = async () => {
    setTestsLoading(true);
    try {
      const response = await fetch('/api/v1/tests', {
        headers: { 'X-API-Key': localStorage.getItem('apiKey') || '' },
      });
      const json = await response.json();
      setAvailableTests(json.data || json || []);
    } catch (err) {
      console.error('Error loading tests:', err);
      setAvailableTests([]);
    } finally {
      setTestsLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    const newValue = type === 'checkbox' ? checked : value;

    if (name === 'agentType') {
      const newMeta = AGENT_META[value] || {};
      setFormData({ ...DEFAULT_FORM, agentType: value, goalType: newMeta.goalType || '' });
    } else {
      setFormData((prev) => ({ ...prev, [name]: newValue }));
    }

    if (validationErrors[name]) {
      setValidationErrors((prev) => ({ ...prev, [name]: null }));
    }
  };

  const validateForm = () => {
    const errors = {};
    if (!formData.agentType) errors.agentType = 'Agent type is required';
    if (meta.requiresJiraKey) {
      if (!formData.jiraKey) errors.jiraKey = 'JIRA key is required';
      else if (!isValidJiraKey(formData.jiraKey)) errors.jiraKey = 'Invalid format — use PROJECT-123';
    }
    if (meta.requiresTestPicker && !formData.scanAll && !formData.testId) {
      errors.testId = 'Select a test or enable "Scan All"';
    }
    if (formData.maxIterations < 1 || formData.maxIterations > 50)
      errors.maxIterations = 'Must be between 1 and 50';
    if (formData.maxAICost < 0.1 || formData.maxAICost > 100)
      errors.maxAICost = 'Must be between $0.10 and $100.00';
    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateForm()) return;
    setLoading(true);
    setError(null);
    try {
      const parameters = {};
      if (meta.requiresJiraKey) {
        parameters.jiraKey = formData.jiraKey;
        parameters.framework = formData.framework;
      }
      if (meta.requiresTestPicker) {
        if (!formData.scanAll && formData.testId) parameters.testId = formData.testId;
        if (formData.errorMessage) parameters.errorMessage = formData.errorMessage;
      }
      const request = {
        agentType: formData.agentType,
        goalType: formData.goalType,
        parameters,
        maxIterations: parseInt(formData.maxIterations),
        maxAICost: parseFloat(formData.maxAICost),
        approvalTimeoutSeconds: parseInt(formData.approvalTimeoutSeconds),
      };
      const response = await agentService.startAgent(request);
      if (onAgentStarted) onAgentStarted(response);
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to start agent. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  const filteredTests = availableTests.filter((t) => {
    const q = testSearchQuery.toLowerCase();
    return !q || (t.name && t.name.toLowerCase().includes(q));
  });

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Start New Agent</h2>
          <button className="close-button" onClick={onClose}>×</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && <div className="error-message">{error}</div>}

            {/* Agent Type */}
            <div className="form-group">
              <label htmlFor="agentType">Agent Type <span className="required">*</span></label>
              <select
                id="agentType" name="agentType"
                value={formData.agentType} onChange={handleInputChange}
                className={validationErrors.agentType ? 'error' : ''}
              >
                <option value="PLAYWRIGHT_TEST_GENERATOR">🤖 Playwright Test Generator</option>
                <option value="FLAKY_TEST_FIXER">🔧 Flaky Test Fixer</option>
                <option value="SELF_HEALING_TEST_FIXER">💊 Self-Healing Test Fixer</option>
                <option value="TEST_FAILURE_ANALYZER">🔬 Test Failure Analyzer</option>
                <option value="QUALITY_MONITOR">📊 Quality Monitor</option>
              </select>
              {validationErrors.agentType && <span className="field-error">{validationErrors.agentType}</span>}
            </div>

            {/* Description */}
            {meta.description && <div className="agent-description">ℹ️ {meta.description}</div>}

            {/* JIRA fields */}
            {meta.requiresJiraKey && (
              <>
                <div className="form-group">
                  <label htmlFor="jiraKey">JIRA Story Key <span className="required">*</span></label>
                  <input
                    type="text" id="jiraKey" name="jiraKey"
                    value={formData.jiraKey} onChange={handleInputChange}
                    placeholder="e.g., PROJ-123"
                    className={validationErrors.jiraKey ? 'error' : ''}
                    disabled={loading}
                  />
                  {validationErrors.jiraKey && <span className="field-error">{validationErrors.jiraKey}</span>}
                </div>
                <div className="form-group">
                  <label htmlFor="framework">Framework</label>
                  <select id="framework" name="framework" value={formData.framework} onChange={handleInputChange} disabled={loading}>
                    <option value="PLAYWRIGHT">🎭 Playwright</option>
                    <option value="CUCUMBER">🥒 Cucumber</option>
                    <option value="TESTNG">🧪 TestNG</option>
                  </select>
                </div>
              </>
            )}

            {/* Test Picker */}
            {meta.requiresTestPicker && (
              <div className="form-group">
                <label>Target Tests</label>

                <div className="scan-all-toggle">
                  <label className="toggle-label">
                    <input type="checkbox" name="scanAll" checked={formData.scanAll} onChange={handleInputChange} disabled={loading} />
                    <span>Scan All Active Tests ({availableTests.length} tests in DB)</span>
                  </label>
                  <p className="toggle-hint">
                    {formData.scanAll
                      ? '✅ Agent will iterate through ALL active tests automatically.'
                      : '👇 Pick a specific test to target below.'}
                  </p>
                </div>

                {!formData.scanAll && (
                  <div className="test-picker">
                    <input
                      type="text" className="test-search"
                      placeholder="Search tests by name..."
                      value={testSearchQuery}
                      onChange={(e) => setTestSearchQuery(e.target.value)}
                      disabled={loading}
                    />
                    {testsLoading ? (
                      <div className="test-picker-loading">Loading tests...</div>
                    ) : filteredTests.length === 0 ? (
                      <div className="test-picker-empty">
                        No active tests found. {testSearchQuery ? 'Try a different search.' : 'Create tests first via the Tests API.'}
                      </div>
                    ) : (
                      <div className="test-picker-list">
                        {filteredTests.map((test) => (
                          <label key={test.id} className={`test-picker-item ${formData.testId === test.id ? 'selected' : ''}`}>
                            <input
                              type="radio" name="testId" value={test.id}
                              checked={formData.testId === test.id}
                              onChange={handleInputChange} disabled={loading}
                            />
                            <div className="test-picker-info">
                              <span className="test-name">{test.name}</span>
                              <div className="test-meta">
                                {test.framework && <span className="badge">{test.framework}</span>}
                                {test.priority && <span className="badge priority">{test.priority}</span>}
                                {test.description && <span className="test-desc">{test.description}</span>}
                              </div>
                            </div>
                          </label>
                        ))}
                      </div>
                    )}
                    {validationErrors.testId && <span className="field-error">{validationErrors.testId}</span>}
                  </div>
                )}

                {formData.agentType === 'SELF_HEALING_TEST_FIXER' && !formData.scanAll && (
                  <div className="form-group" style={{ marginTop: 12 }}>
                    <label htmlFor="errorMessage">Known Error Message (Optional)</label>
                    <textarea
                      id="errorMessage" name="errorMessage"
                      value={formData.errorMessage} onChange={handleInputChange}
                      placeholder="e.g., Element not found: #login-btn — helps the agent target the right locator"
                      rows="2" disabled={loading}
                    />
                  </div>
                )}
              </div>
            )}

            {/* Advanced Options */}
            <div className="advanced-section">
              <button type="button" className="advanced-toggle" onClick={() => setShowAdvanced(!showAdvanced)}>
                {showAdvanced ? '▼' : '▶'} Advanced Options
              </button>
              {showAdvanced && (
                <div className="advanced-fields">
                  <div className="form-group">
                    <label htmlFor="maxIterations">Max Iterations</label>
                    <input type="number" id="maxIterations" name="maxIterations"
                      value={formData.maxIterations} onChange={handleInputChange}
                      min="1" max="50" className={validationErrors.maxIterations ? 'error' : ''} />
                    {validationErrors.maxIterations && <span className="field-error">{validationErrors.maxIterations}</span>}
                  </div>
                  <div className="form-group">
                    <label htmlFor="maxAICost">Max AI Cost ($)</label>
                    <input type="number" id="maxAICost" name="maxAICost"
                      value={formData.maxAICost} onChange={handleInputChange}
                      min="0.1" max="100" step="0.1" className={validationErrors.maxAICost ? 'error' : ''} />
                    {validationErrors.maxAICost && <span className="field-error">{validationErrors.maxAICost}</span>}
                  </div>
                  <div className="form-group">
                    <label htmlFor="approvalTimeoutSeconds">Approval Timeout (seconds)</label>
                    <input type="number" id="approvalTimeoutSeconds" name="approvalTimeoutSeconds"
                      value={formData.approvalTimeoutSeconds} onChange={handleInputChange}
                      min="60" max="86400" />
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Starting...' : '▶ Start Agent'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default StartAgentModal;