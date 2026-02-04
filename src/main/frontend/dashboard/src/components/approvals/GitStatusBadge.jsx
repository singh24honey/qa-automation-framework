import React from 'react';
import { ExternalLink, RefreshCw, GitBranch, CheckCircle, XCircle, Clock } from 'lucide-react';
import './GitStatusBadge.css';

const GitStatusBadge = ({ approval, onRetry, onManualTrigger }) => {
    // No Git operation triggered yet
    if (!approval.gitOperationTriggered) {
        return (
            <div className="git-status-container">
                <span className="git-badge badge-secondary">
                    <Clock size={14} />
                    No Git Operation
                </span>
                {approval.status === 'APPROVED' && (
                    <button
                        onClick={() => onManualTrigger(approval.id)}
                        className="btn-git-action btn-small"
                        title="Trigger Git commit manually"
                    >
                        <GitBranch size={14} />
                        Commit to Git
                    </button>
                )}
            </div>
        );
    }

    // Git operation triggered but pending
    if (approval.gitOperationSuccess === null || approval.gitOperationSuccess === undefined) {
        return (
            <div className="git-status-container">
                <span className="git-badge badge-warning">
                    <RefreshCw size={14} className="spinning" />
                    Git Pending
                </span>
            </div>
        );
    }

    // Git operation successful
    if (approval.gitOperationSuccess) {
        return (
            <div className="git-status-container git-success">
                <span className="git-badge badge-success">
                    <CheckCircle size={14} />
                    Git Success
                </span>

                {approval.gitBranch && (
                    <span className="git-info" title="Git branch">
                        <GitBranch size={14} />
                        {approval.gitBranch}
                    </span>
                )}

                {approval.gitPrUrl && (
                    <a
                        href={approval.gitPrUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="git-pr-link"
                        title="View Pull Request"
                    >
                        <ExternalLink size={14} />
                        View PR
                    </a>
                )}
            </div>
        );
    }

    // Git operation failed
    return (
        <div className="git-status-container git-failed">
            <span className="git-badge badge-danger">
                <XCircle size={14} />
                Git Failed
            </span>

            {approval.gitErrorMessage && (
                <span className="git-error-msg" title={approval.gitErrorMessage}>
                    {approval.gitErrorMessage.substring(0, 50)}...
                </span>
            )}

            <button
                onClick={() => onRetry(approval.id)}
                className="btn-git-action btn-small btn-retry"
                title="Retry Git operation"
            >
                <RefreshCw size={14} />
                Retry
            </button>
        </div>
    );
};

export default GitStatusBadge;