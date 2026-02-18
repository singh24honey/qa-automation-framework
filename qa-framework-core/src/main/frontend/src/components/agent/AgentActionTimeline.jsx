import React, { useState, useEffect } from 'react';
import agentService from '../../services/AgentService';
import {
  getActionTypeDisplayName,
  getActionTypeIcon,
  getActionStatusIcon,
  formatCost,
  truncateText,
  safeParseJSON,
} from '../../utils/AgentHelpers';
import { formatDurationMs, formatRelativeTime } from '../../utils/Formatters';
import './AgentActionTimeline.css';

/**
 * Agent Action Timeline Component
 *
 * Displays a chronological timeline of agent actions.
 *
 * Props:
 * - executionId: Agent execution ID
 * - autoRefresh: Boolean to enable auto-refresh (default: true)
 */
const AgentActionTimeline = ({ executionId, autoRefresh = true }) => {
  const [actions, setActions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [expandedActions, setExpandedActions] = useState(new Set());

  useEffect(() => {
    loadActions();

    if (autoRefresh) {
      const interval = setInterval(loadActions, 3000);
      return () => clearInterval(interval);
    }
  }, [executionId, autoRefresh]);

  const loadActions = async () => {
    try {
      const data = await agentService.getAgentActions(executionId);
      setActions(data);
      setError(null);
    } catch (err) {
      console.error('Error loading actions:', err);
      setError('Failed to load action timeline');
    } finally {
      setLoading(false);
    }
  };

  const toggleAction = (actionId) => {
    const newExpanded = new Set(expandedActions);
    if (newExpanded.has(actionId)) {
      newExpanded.delete(actionId);
    } else {
      newExpanded.add(actionId);
    }
    setExpandedActions(newExpanded);
  };

  if (loading) {
    return <div className="timeline-loading">Loading action timeline...</div>;
  }

  if (error) {
    return <div className="timeline-error">{error}</div>;
  }

  if (actions.length === 0) {
    return <div className="timeline-empty">No actions recorded yet</div>;
  }

  return (
    <div className="agent-action-timeline">
      <h3 className="timeline-title">Action Timeline ({actions.length} actions)</h3>

      <div className="timeline-list">
        {actions.map((action, index) => {
          const isExpanded = expandedActions.has(action.id);
          const inputData = safeParseJSON(action.actionInput);
          const outputData = safeParseJSON(action.actionOutput);

          return (
            <div key={action.id} className="timeline-item">
              {/* Timeline connector */}
              {index < actions.length - 1 && <div className="timeline-connector"></div>}

              {/* Action icon */}
              <div className={`timeline-icon ${action.success ? 'success' : 'error'}`}>
                <span className="action-icon">{getActionTypeIcon(action.actionType)}</span>
                <span className="status-icon">
                  {getActionStatusIcon(action.success, action.requiredApproval)}
                </span>
              </div>

              {/* Action content */}
              <div className="timeline-content">
                <div className="action-header">
                  <div className="action-info">
                    <h4 className="action-type">
                      {index + 1}. {getActionTypeDisplayName(action.actionType)}
                    </h4>
                    <div className="action-meta">
                      <span className="iteration">Iteration {action.iteration}</span>
                      <span className="duration">{formatDurationMs(action.durationMs)}</span>
                      {action.aiCost > 0 && (
                        <span className="cost">{formatCost(action.aiCost)}</span>
                      )}
                      <span className="timestamp">{formatRelativeTime(action.timestamp)}</span>
                    </div>
                  </div>
                  {(inputData || outputData || action.errorMessage) && (
                    <button
                      className="expand-button"
                      onClick={() => toggleAction(action.id)}
                    >
                      {isExpanded ? '▼' : '▶'}
                    </button>
                  )}
                </div>

                {/* Action summary */}
                {!action.success && action.errorMessage && (
                  <div className="action-error">
                    ❌ {action.errorMessage}
                  </div>
                )}

                {action.requiredApproval && (
                  <div className="action-approval">
                    ⏳ Waiting for approval
                    {action.approvalRequestId && (
                      <span className="approval-id"> (ID: {action.approvalRequestId})</span>
                    )}
                  </div>
                )}

                {action.success && !action.requiredApproval && (
                  <div className="action-success">
                    ✓ Completed successfully
                  </div>
                )}

                {/* Expanded details */}
                {isExpanded && (
                  <div className="action-details">
                    {inputData && (
                      <div className="detail-section">
                        <h5>Input:</h5>
                        <pre className="detail-content">
                          {JSON.stringify(inputData, null, 2)}
                        </pre>
                      </div>
                    )}

                    {outputData && (
                      <div className="detail-section">
                        <h5>Output:</h5>
                        <pre className="detail-content">
                          {JSON.stringify(outputData, null, 2)}
                        </pre>
                      </div>
                    )}

                    {action.errorMessage && (
                      <div className="detail-section error">
                        <h5>Error:</h5>
                        <pre className="detail-content">{action.errorMessage}</pre>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default AgentActionTimeline;