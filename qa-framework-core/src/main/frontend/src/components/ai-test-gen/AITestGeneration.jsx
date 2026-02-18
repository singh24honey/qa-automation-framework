import React, { useState, useEffect } from 'react';
import aiTestGenService from '../../services/AITestGenService';
import TestGenerationForm from './TestGenerationForm';
import GeneratedTestPreview from './GeneratedTestPreview';
import './AITestGeneration.css';

/**
 * AI Test Generation Page
 *
 * Main page for AI-powered test generation from JIRA stories.
 */
const AITestGeneration = () => {
  const [generating, setGenerating] = useState(false);
  const [generatedTest, setGeneratedTest] = useState(null);
  const [pendingTests, setPendingTests] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadPendingTests();
  }, []);

  const loadPendingTests = async () => {
    try {
      const tests = await aiTestGenService.getPendingTests();
      setPendingTests(tests);
    } catch (err) {
      console.error('Error loading pending tests:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async (request) => {
    setGenerating(true);
    setError(null);

    try {
      const response = await aiTestGenService.generateTest(request);
      setGeneratedTest(response);
      // Reload pending tests
      loadPendingTests();
    } catch (err) {
      console.error('Error generating test:', err);
      setError(err.message || 'Failed to generate test. Please try again.');
    } finally {
      setGenerating(false);
    }
  };

  const handleApprove = async (testId) => {
    try {
      const userName = localStorage.getItem('userName') || 'Unknown';
      await aiTestGenService.approveTest({
        testId,
        approved: true,
        reviewerName: userName,
        comments: 'Approved via AI Test Gen page',
      });

      alert('Test approved and sent for review!');
      setGeneratedTest(null);
      loadPendingTests();
    } catch (err) {
      console.error('Error approving test:', err);
      alert('Failed to approve test: ' + (err.message || 'Unknown error'));
    }
  };

  const handleRegenerate = () => {
    // Clear current test to show form again
    setGeneratedTest(null);
  };

  const handleDiscard = (testId) => {
    // For now, just clear the preview
    // In future, could call a delete API
    setGeneratedTest(null);
  };

  return (
    <div className="ai-test-generation">
      {/* Header */}
      <div className="page-header">
        <div className="header-content">
          <h1>AI Test Generation</h1>
          <p className="header-subtitle">
            Generate automated tests from JIRA stories using AI
          </p>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="error-banner">
          {error}
          <button onClick={() => setError(null)}>×</button>
        </div>
      )}

      {/* Main Content */}
      <div className="page-content">
        {/* Left Column - Generation Form */}
        <div className="content-left">
          <div className="section">
            <h2 className="section-title">Generate New Test</h2>
            <TestGenerationForm onGenerate={handleGenerate} loading={generating} />
          </div>

          {/* Pending Tests Section */}
          {!loading && pendingTests.length > 0 && (
            <div className="section">
              <h2 className="section-title">
                Recent Generations ({pendingTests.length})
              </h2>
              <div className="pending-tests-list">
                {pendingTests.slice(0, 5).map((test) => (
                  <div key={test.testId} className="pending-test-item">
                    <div className="test-item-info">
                      <span className="test-jira-key">{test.jiraStoryKey}</span>
                      <span className="test-framework">{test.framework}</span>
                      <span className="test-quality">Score: {test.qualityScore}/100</span>
                    </div>
                    <span className="test-status">{test.status}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right Column - Test Preview */}
        <div className="content-right">
          {generating && (
            <div className="generation-loading">
              <div className="loading-spinner"></div>
              <h3>Generating Test...</h3>
              <p>AI is analyzing the JIRA story and generating test code</p>
            </div>
          )}

          {!generating && generatedTest && (
            <GeneratedTestPreview
              test={generatedTest}
              onApprove={handleApprove}
              onRegenerate={handleRegenerate}
              onDiscard={handleDiscard}
            />
          )}

          {!generating && !generatedTest && (
            <div className="preview-placeholder">
              <div className="placeholder-icon">🎭</div>
              <h3>No Test Generated Yet</h3>
              <p>Fill in the form and click "Generate Test" to create a new automated test</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AITestGeneration;