import React, { useState, useEffect, useCallback } from 'react';
import agentService from '../../services/AgentService';
import AgentExecutionCard from './AgentExecutionCard';
import AgentActionTimeline from './AgentActionTimeline';
import StartAgentModal from './StartAgentModal';
import AgentStatusBadge from './AgentStatusBadge';
import {
  getAgentTypeDisplayName,
  getAgentTypeIcon,
  formatCost,
  formatDuration,
} from '../../utils/AgentHelpers';
import { formatRelativeTime, formatAbsoluteDateTime } from '../../utils/Formatters';
import './AgentDashboard.css';

const STATUS_FILTERS = ['ALL', 'RUNNING', 'WAITING_FOR_APPROVAL', 'SUCCEEDED', 'FAILED', 'STOPPED'];

const AgentDashboard = () => {
  const [runningAgents, setRunningAgents]       = useState([]);
  const [historyAgents, setHistoryAgents]       = useState([]);
  const [selectedExecution, setSelectedExecution] = useState(null);
  const [showStartModal, setShowStartModal]     = useState(false);
  const [loadingRunning, setLoadingRunning]     = useState(true);
  const [loadingHistory, setLoadingHistory]     = useState(true);
  const [error, setError]                       = useState(null);
  const [autoRefresh, setAutoRefresh]           = useState(true);
  const [historyFilter, setHistoryFilter]       = useState('ALL');

  // Sort by startedAt DESC — most recently triggered first
  const sortByRecent = (arr) =>
    [...arr].sort((a, b) => {
      const ta = a.startedAt ? new Date(a.startedAt).getTime() : 0;
      const tb = b.startedAt ? new Date(b.startedAt).getTime() : 0;
      return tb - ta;
    });

  const loadRunningAgents = useCallback(async () => {
    try {
      const data = await agentService.getRunningAgents();
      setRunningAgents(sortByRecent(data));
      setError(null);
    } catch (err) {
      setError('Failed to load running agents — check backend connection');
    } finally {
      setLoadingRunning(false);
    }
  }, []);

  const loadHistory = useCallback(async () => {
    try {
      const status = historyFilter === 'ALL' ? null : historyFilter;
      const data = await agentService.getExecutionHistory(status, 50);
      setHistoryAgents(sortByRecent(data));
    } catch (err) {
      console.error('Error loading history:', err);
    } finally {
      setLoadingHistory(false);
    }
  }, [historyFilter]);

  // Initial load
  useEffect(() => {
    loadRunningAgents();
    loadHistory();
  }, [loadRunningAgents, loadHistory]);

  // Auto-refresh running agents every 5s
  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(loadRunningAgents, 5000);
    return () => clearInterval(interval);
  }, [autoRefresh, loadRunningAgents]);

  const handleAgentStarted = (response) => {
    loadRunningAgents();
    loadHistory();
  };

  const handleStopAgent = async (executionId) => {
    try {
      await agentService.stopAgent(executionId);
      loadRunningAgents();
      loadHistory();
      if (selectedExecution?.executionId === executionId) setSelectedExecution(null);
    } catch (err) {
      alert('Failed to stop agent: ' + (err.message || 'Unknown error'));
    }
  };

  const handleViewDetails = (executionId) => {
    const all = [...runningAgents, ...historyAgents];
    const found = all.find((a) => (a.executionId || a.id) === executionId);
    if (found) setSelectedExecution(found);
  };

  return (
    <div className="agent-dashboard">
      {/* ── Header ── */}
      <div className="dashboard-header">
        <div className="header-content">
          <h1>🤖 Agent Dashboard</h1>
          <p className="header-subtitle">Monitor and manage AI agents — most recently triggered shown first</p>
        </div>
        <div className="header-actions">
          <label className="auto-refresh-toggle">
            <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} />
            Auto-refresh (5s)
          </label>
          <button className="btn btn-primary" onClick={() => setShowStartModal(true)}>
            + Start New Agent
          </button>
        </div>
      </div>

      {/* ── Error Banner ── */}
      {error && (
        <div className="error-banner">
          {error}
          <button onClick={() => setError(null)}>×</button>
        </div>
      )}

      {/* ── Running / Waiting Section ── */}
      <div className="dashboard-section">
        <div className="section-header">
          <h2>
            Active Agents
            {runningAgents.length > 0 && (
              <span className="section-count running-count">{runningAgents.length}</span>
            )}
          </h2>
          <button className="btn btn-secondary btn-sm" onClick={loadRunningAgents}>🔄 Refresh</button>
        </div>

        {loadingRunning ? (
          <div className="section-loading"><div className="spinner" /><p>Loading…</p></div>
        ) : runningAgents.length === 0 ? (
          <div className="section-empty">
            <div className="empty-icon">🤖</div>
            <h3>No Active Agents</h3>
            <p>All agents are idle. Click "Start New Agent" to begin.</p>
          </div>
        ) : (
          <div className="agent-list">
            {runningAgents.map((execution) => (
              <AgentExecutionCard
                key={execution.executionId || execution.id}
                execution={execution}
                onStop={handleStopAgent}
                onViewDetails={handleViewDetails}
                showActions
              />
            ))}
          </div>
        )}
      </div>

      {/* ── Action Timeline for selected execution ── */}
      {selectedExecution && (
        <div className="dashboard-section">
          <div className="section-header">
            <h2>Action Timeline — {getAgentTypeDisplayName(selectedExecution.agentType)}</h2>
            <button className="btn btn-secondary btn-sm" onClick={() => setSelectedExecution(null)}>
              ✕ Close
            </button>
          </div>
          <AgentExecutionCard
            execution={selectedExecution}
            onStop={handleStopAgent}
            onViewDetails={() => {}}
            showActions={false}
          />
          <AgentActionTimeline
            executionId={selectedExecution.executionId || selectedExecution.id}
            autoRefresh={autoRefresh && (selectedExecution.status === 'RUNNING')}
          />
        </div>
      )}

      {/* ── Execution History ── */}
      <div className="dashboard-section">
        <div className="section-header">
          <h2>
            Execution History
            {historyAgents.length > 0 && (
              <span className="section-count">{historyAgents.length}</span>
            )}
          </h2>
          <div className="section-header-right">
            {/* Status filter pills */}
            <div className="filter-pills">
              {STATUS_FILTERS.map((s) => (
                <button
                  key={s}
                  className={`filter-pill ${historyFilter === s ? 'active' : ''}`}
                  onClick={() => setHistoryFilter(s)}
                >
                  {s === 'ALL' ? 'All' : s.replace('_', ' ')}
                </button>
              ))}
            </div>
            <button className="btn btn-secondary btn-sm" onClick={loadHistory}>🔄</button>
          </div>
        </div>

        {loadingHistory ? (
          <div className="section-loading"><div className="spinner" /><p>Loading…</p></div>
        ) : historyAgents.length === 0 ? (
          <div className="section-empty">
            <div className="empty-icon">📋</div>
            <h3>No Executions Found</h3>
            <p>{historyFilter !== 'ALL' ? `No ${historyFilter} executions.` : 'No agent executions yet.'}</p>
          </div>
        ) : (
          <div className="history-table-wrapper">
            <table className="history-table">
              <thead>
                <tr>
                  <th>Agent</th>
                  <th>Status</th>
                  <th>Started</th>
                  <th>Duration</th>
                  <th>Iterations</th>
                  <th>AI Cost</th>
                  <th>Triggered By</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {historyAgents.map((execution) => {
                  const id = execution.executionId || execution.id;
                  const isSelected = selectedExecution && (selectedExecution.executionId || selectedExecution.id) === id;
                  return (
                    <tr key={id} className={isSelected ? 'selected-row' : ''}>
                      <td className="agent-type-cell">
                        <span className="agent-icon-sm">{getAgentTypeIcon(execution.agentType)}</span>
                        <span>{getAgentTypeDisplayName(execution.agentType)}</span>
                      </td>
                      <td><AgentStatusBadge status={execution.status} size="small" /></td>
                      <td className="date-cell" title={formatAbsoluteDateTime(execution.startedAt)}>
                        {formatRelativeTime(execution.startedAt)}
                      </td>
                      <td>{formatDuration(execution.durationSeconds)}</td>
                      <td>
                        {execution.currentIteration ?? 0}/{execution.maxIterations ?? '—'}
                      </td>
                      <td>{formatCost(execution.totalAICost)}</td>
                      <td className="triggered-cell">{execution.triggeredBy || 'System'}</td>
                      <td>
                        <button
                          className="btn-link"
                          onClick={() => handleViewDetails(id)}
                        >
                          {isSelected ? 'Hide' : 'Timeline'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Start Agent Modal ── */}
      <StartAgentModal
        isOpen={showStartModal}
        onClose={() => setShowStartModal(false)}
        onAgentStarted={handleAgentStarted}
      />
    </div>
  );
};

export default AgentDashboard;