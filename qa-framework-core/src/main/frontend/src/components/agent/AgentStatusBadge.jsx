import React from 'react';
import { getStatusColor, getStatusDisplayName } from '../../utils/AgentHelpers';
import './AgentStatusBadge.css';

/**
 * Agent Status Badge Component
 *
 * Displays agent execution status with appropriate styling.
 *
 * Props:
 * - status: Agent execution status (RUNNING, SUCCEEDED, FAILED, etc.)
 * - size: Badge size ('small', 'medium', 'large') - default: 'medium'
 */
const AgentStatusBadge = ({ status, size = 'medium' }) => {
  const colorClass = getStatusColor(status);
  const displayName = getStatusDisplayName(status);

  return (
    <span className={`agent-status-badge ${colorClass} size-${size}`}>
      {status === 'RUNNING' && <span className="status-indicator running"></span>}
      {displayName}
    </span>
  );
};

export default AgentStatusBadge;