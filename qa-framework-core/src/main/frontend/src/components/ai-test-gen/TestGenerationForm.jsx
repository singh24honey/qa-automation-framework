import React, { useState } from 'react';
import { isValidJiraKey } from '../../utils/AgentHelpers';
import './TestGenerationForm.css';

/**
 * Test Generation Form Component
 *
 * Form to configure and trigger AI test generation from JIRA stories.
 *
 * Props:
 * - onGenerate: Function called when form is submitted
 * - loading: Boolean indicating generation in progress
 */
const TestGenerationForm = ({ onGenerate, loading = false }) => {
  const [formData, setFormData] = useState({
    jiraStoryKey: '',
    framework: 'PLAYWRIGHT',
    testType: 'UI',
    userPrompt: '',
    targetBrowser: 'CHROME',
    timeout: 30000,
  });

  const [validationErrors, setValidationErrors] = useState({});

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
    // Clear validation error for this field
    if (validationErrors[name]) {
      setValidationErrors({ ...validationErrors, [name]: null });
    }
  };

  const validateForm = () => {
    const errors = {};

    if (!formData.jiraStoryKey) {
      errors.jiraStoryKey = 'JIRA story key is required';
    } else if (!isValidJiraKey(formData.jiraStoryKey)) {
      errors.jiraStoryKey = 'Invalid JIRA key format (e.g., PROJ-123)';
    }

    if (!formData.framework) {
      errors.framework = 'Framework is required';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    // Convert timeout to number
    const request = {
      ...formData,
      timeout: parseInt(formData.timeout),
    };

    onGenerate(request);
  };

  const handleReset = () => {
    setFormData({
      jiraStoryKey: '',
      framework: 'PLAYWRIGHT',
      userPrompt: '',
      targetBrowser: 'CHROME',
      timeout: 30000,
    });
    setValidationErrors({});
  };

  return (
    <form className="test-generation-form" onSubmit={handleSubmit}>
      <div className="form-grid">
        {/* JIRA Story Key */}
        <div className="form-group">
          <label htmlFor="jiraStoryKey">
            JIRA Story Key <span className="required">*</span>
          </label>
          <input
            type="text"
            id="jiraStoryKey"
            name="jiraStoryKey"
            value={formData.jiraStoryKey}
            onChange={handleInputChange}
            placeholder="e.g., PROJ-123"
            className={validationErrors.jiraStoryKey ? 'error' : ''}
            disabled={loading}
          />
          {validationErrors.jiraStoryKey && (
            <span className="field-error">{validationErrors.jiraStoryKey}</span>
          )}
          <span className="field-help">
            Enter the JIRA story key to generate tests from
          </span>
        </div>

        {/* Framework */}
        <div className="form-group">
          <label htmlFor="framework">
            Framework <span className="required">*</span>
          </label>
          <div className="radio-group">
            <label className="radio-label">
              <input
                type="radio"
                name="framework"
                value="PLAYWRIGHT"
                checked={formData.framework === 'PLAYWRIGHT'}
                onChange={handleInputChange}
                disabled={loading}
              />
              <span className="radio-text">
                <span className="radio-icon">🎭</span>
                Playwright
              </span>
            </label>
            <label className="radio-label">
              <input
                type="radio"
                name="framework"
                value="CUCUMBER"
                checked={formData.framework === 'CUCUMBER'}
                onChange={handleInputChange}
                disabled={loading}
              />
              <span className="radio-text">
                <span className="radio-icon">🥒</span>
                Cucumber
              </span>
            </label>
            <label className="radio-label">
              <input
                type="radio"
                name="framework"
                value="TESTNG"
                checked={formData.framework === 'TESTNG'}
                onChange={handleInputChange}
                disabled={loading}
              />
              <span className="radio-text">
                <span className="radio-icon">🧪</span>
                TestNG
              </span>
            </label>
          </div>
          {validationErrors.framework && (
            <span className="field-error">{validationErrors.framework}</span>
          )}
        </div>
       <div className="form-group">
         <label htmlFor="testType">
           Test Type <span className="required">*</span>
         </label>
         <select
           id="testType"
           name="testType"
           value={formData.testType}
           onChange={handleInputChange}
           disabled={loading}
         >
           <option value="UI">UI</option>
           <option value="API">API</option>
           <option value="INTEGRATION">Integration</option>
         </select>
       </div>
        {/* Custom Prompt */}
        <div className="form-group full-width">
          <label htmlFor="userPrompt">Custom Prompt (Optional)</label>
          <textarea
            id="userPrompt"
            name="userPrompt"
            value={formData.userPrompt}
            onChange={handleInputChange}
            placeholder="e.g., Focus on edge cases, Add error handling, Test authentication flows"
            rows="3"
            disabled={loading}
          />
          <span className="field-help">
            Provide additional instructions or focus areas for the AI
          </span>
        </div>

        {/* Target Browser */}
        <div className="form-group">
          <label htmlFor="targetBrowser">Target Browser</label>
          <select
            id="targetBrowser"
            name="targetBrowser"
            value={formData.targetBrowser}
            onChange={handleInputChange}
            disabled={loading}
          >
            <option value="CHROME">Chrome</option>
            <option value="FIREFOX">Firefox</option>
            <option value="SAFARI">Safari</option>
            <option value="EDGE">Edge</option>
          </select>
        </div>

        {/* Timeout */}
        <div className="form-group">
          <label htmlFor="timeout">Timeout (ms)</label>
          <input
            type="number"
            id="timeout"
            name="timeout"
            value={formData.timeout}
            onChange={handleInputChange}
            min="5000"
            max="120000"
            step="1000"
            disabled={loading}
          />
          <span className="field-help">Test execution timeout</span>
        </div>
      </div>

      {/* Form Actions */}
      <div className="form-actions">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={handleReset}
          disabled={loading}
        >
          Reset
        </button>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? (
            <>
              <span className="spinner-small"></span>
              Generating Test...
            </>
          ) : (
            <>⚡ Generate Test</>
          )}
        </button>
      </div>
    </form>
  );
};

export default TestGenerationForm;