import React from 'react';
import AgentStatusBadge from './AgentStatusBadge';
import {
  getAgentTypeDisplayName,
  getAgentTypeIcon,
  calculateProgress,
  formatCost,
  formatDuration,
} from '../../utils/AgentHelpers';
import { formatRelativeTime } from '../../utils/Formatters';
import './AgentExecutionCard.css';

/**
 * Agent Execution Card Component
 *
 * Displays a single agent execution with status, progress, and actions.
 *
 * Props:
 * - execution: AgentExecutionResponse object
 * - onStop: Function to stop the agent
 * - onViewDetails: Function to view agent details
 * - showActions: Boolean to show/hide action buttons
 */
const AgentExecutionCard = ({
  execution,
  onStop,
  onViewDetails,
  showActions = true,
}) => {
  const {
    executionId,
    agentType,
    status,
    goal,
    currentIteration,
    maxIterations,
    startedAt,
    triggeredBy,
    totalAICost,
    durationSeconds,
  } = execution;

  const progress = calculateProgress(currentIteration, maxIterations);
  const isRunning = status === 'RUNNING';

  const handleStop = () => {
    if (window.confirm('Are you sure you want to stop this agent?')) {
      onStop(executionId);
    }
  };

  return (
    <div className="agent-execution-card">
      {/* Header */}
      <div className="card-header">
        <div className="agent-info">
          <span className="agent-icon">{getAgentTypeIcon(agentType)}</span>
          <div>
            <h3 className="agent-type">{getAgentTypeDisplayName(agentType)}</h3>
           <div className="agent-goal">
             <div><strong>Goal:</strong> {goal?.goalType}</div>
             {goal?.successCriteria && (
               <div className="goal-success">
                 <small>{goal.successCriteria}</small>
               </div>
             )}
           </div>
          </div>
        </div>
        <AgentStatusBadge status={status} />
      </div>

      {/* Progress */}
      <div className="card-body">
        <div className="progress-section">
          <div className="progress-label">
            <span>Progress</span>
            <span className="progress-text">
              {currentIteration}/{maxIterations} iterations
            </span>
          </div>
          <div className="progress-bar">
            <div
              className="progress-fill"
              style={{ width: `${progress}%` }}
            ></div>
          </div>
          <div className="progress-percentage">{progress}%</div>
        </div>

        {/* Metrics */}
        <div className="metrics-grid">
          <div className="metric">
            <span className="metric-label">Cost</span>
            <span className="metric-value">{formatCost(totalAICost)}</span>
          </div>
          <div className="metric">
            <span className="metric-label">Duration</span>
            <span className="metric-value">
              {isRunning
                ? formatDuration(durationSeconds)
                : formatDuration(durationSeconds)}
            </span>
          </div>
          <div className="metric">
            <span className="metric-label">Triggered by</span>
            <span className="metric-value">{triggeredBy || 'System'}</span>
          </div>
          <div className="metric">
            <span className="metric-label">Started</span>
            <span className="metric-value">{formatRelativeTime(startedAt)}</span>
          </div>
        </div>
      </div>

      {/* Actions */}
      {showActions && (
        <div className="card-actions">
          <button
            className="btn btn-secondary"
            onClick={() => onViewDetails(executionId)}
          >
            View Details
          </button>
          {isRunning && (
            <button className="btn btn-danger" onClick={handleStop}>
              Stop Agent
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default AgentExecutionCard;