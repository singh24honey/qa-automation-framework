import React from 'react';
import CodeViewer from '../approvals/CodeViewer';
import { formatQualityStars, formatCurrency, formatRelativeTime } from '../../utils/Formatters';
import { getFrameworkIcon } from '../../utils/AgentHelpers';
import './GeneratedTestPreview.css';

/**
 * Generated Test Preview Component
 *
 * Displays generated test code with quality metrics and actions.
 *
 * Props:
 * - test: TestGenerationResponse object
 * - onApprove: Function to approve the test
 * - onRegenerate: Function to regenerate the test
 * - onDiscard: Function to discard the test
 * - loading: Boolean indicating action in progress
 */
const GeneratedTestPreview = ({
  test,
  onApprove,
  onRegenerate,
  onDiscard,
  loading = false,
}) => {
  if (!test) {
    return null;
  }

  const {
    testId,
    jiraStoryKey,
    framework,
    generatedCode,
    qualityScore,
    status,
    aiProvider,
    aiModel,
    aiCost,
    generatedAt,
    issues,
  } = test;

  const handleApprove = () => {
    if (window.confirm('Send this test for review and approval?')) {
      onApprove(testId);
    }
  };

  const handleDiscard = () => {
    if (window.confirm('Are you sure you want to discard this generated test?')) {
      onDiscard(testId);
    }
  };

  return (
    <div className="generated-test-preview">
      {/* Header */}
      <div className="preview-header">
        <div className="test-info">
          <h3 className="test-title">
            <span className="framework-icon">{getFrameworkIcon(framework)}</span>
            Generated Test: {jiraStoryKey}
          </h3>
          <div className="test-meta">
            <span className="meta-item">
              <span className="meta-label">Quality Score:</span>
              <span className="quality-score">
                {qualityScore}/100 {formatQualityStars(qualityScore)}
              </span>
            </span>
            <span className="meta-item">
              <span className="meta-label">Framework:</span>
              {framework}
            </span>
            <span className="meta-item">
              <span className="meta-label">AI Cost:</span>
              {formatCurrency(aiCost)}
            </span>
            <span className="meta-item">
              <span className="meta-label">Generated:</span>
              {formatRelativeTime(generatedAt)}
            </span>
          </div>
        </div>
        <div className="status-badge status-pending">
          {status}
        </div>
      </div>

      {/* Issues & Warnings */}
      {issues && issues.length > 0 && (
        <div className="issues-section">
          <h4>Issues & Warnings</h4>
          <div className="issues-list">
            {issues.map((issue, index) => (
              <div
                key={index}
                className={`issue-item severity-${issue.severity.toLowerCase()}`}
              >
                <span className="issue-icon">
                  {issue.severity === 'ERROR' && '❌'}
                  {issue.severity === 'WARNING' && '⚠️'}
                  {issue.severity === 'INFO' && 'ℹ️'}
                </span>
                <span className="issue-message">{issue.message}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Code Preview */}
      <div className="code-section">
        <h4>Generated Code</h4>
        <CodeViewer code={generatedCode} language={getLanguageForFramework(framework)} />
      </div>

      {/* AI Details */}
      <div className="ai-details">
        <span className="ai-detail">
          <span className="detail-label">AI Provider:</span>
          {formatAIProvider(aiProvider)}
        </span>
        <span className="ai-detail">
          <span className="detail-label">Model:</span>
          {aiModel}
        </span>
      </div>

      {/* Actions */}
      <div className="preview-actions">
        <button
          className="btn btn-danger"
          onClick={handleDiscard}
          disabled={loading}
        >
          🗑️ Discard
        </button>
        <button
          className="btn btn-secondary"
          onClick={onRegenerate}
          disabled={loading}
        >
          🔄 Regenerate
        </button>
        <button
          className="btn btn-success"
          onClick={handleApprove}
          disabled={loading}
        >
          ✓ Approve for Review
        </button>
      </div>
    </div>
  );
};

// Helper function to get language for CodeViewer
const getLanguageForFramework = (framework) => {
  const languageMap = {
    'PLAYWRIGHT': 'typescript',
    'CUCUMBER': 'gherkin',
    'TESTNG': 'java',
  };
  return languageMap[framework] || 'javascript';
};

// Helper function to format AI provider
const formatAIProvider = (provider) => {
  const providerMap = {
    'AWS_BEDROCK': 'AWS Bedrock',
    'OLLAMA': 'Ollama',
    'MOCK_AI': 'Mock AI',
  };
  return providerMap[provider] || provider;
};

export default GeneratedTestPreview;