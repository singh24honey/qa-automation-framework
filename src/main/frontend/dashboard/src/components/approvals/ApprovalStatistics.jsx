import React, { useState, useEffect } from 'react';
import approvalService from '../../services/approvalService';
import './ApprovalStatistics.css';

/**
 * Approval Statistics Dashboard
 */
const ApprovalStatistics = () => {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        loadStatistics();
    }, []);

    const loadStatistics = async () => {
        try {
            setLoading(true);
            setError(null);
            const data = await approvalService.getStatistics();
            setStats(data);
        } catch (err) {
            console.error('Error loading statistics:', err);
            setError('Failed to load statistics');
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return (
            <div className="approval-statistics">
                <div className="loading-container">
                    <div className="spinner"></div>
                    <p>Loading statistics...</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="approval-statistics">
                <div className="error-container">
                    <p className="error-message">{error}</p>
                    <button onClick={loadStatistics} className="btn-retry">
                        🔄 Retry
                    </button>
                </div>
            </div>
        );
    }

    if (!stats) {
        return null;
    }

    return (
        <div className="approval-statistics">
            <div className="stats-header">
                <div>
                    <h2>Approval Workflow Statistics</h2>
                    <p className="stats-subtitle">Overview of approval requests and review metrics</p>
                </div>
                <button onClick={loadStatistics} className="btn-refresh-stats">
                    🔄 Refresh
                </button>
            </div>

            <div className="stats-grid">
                {/* Total Requests */}
                <div className="stat-card total">
                    <div className="stat-icon">📊</div>
                    <div className="stat-content">
                        <div className="stat-value">{stats.totalRequests}</div>
                        <div className="stat-label">Total Requests</div>
                    </div>
                </div>

                {/* Pending */}
                <div className="stat-card pending">
                    <div className="stat-icon">⏳</div>
                    <div className="stat-content">
                        <div className="stat-value">{stats.pendingRequests}</div>
                        <div className="stat-label">Pending Review</div>
                        {stats.pendingRequests > 0 && (
                            <div className="stat-detail">Needs attention</div>
                        )}
                    </div>
                </div>

                {/* Approved */}
                <div className="stat-card approved">
                    <div className="stat-icon">✅</div>
                    <div className="stat-content">
                        <div className="stat-value">{stats.approvedRequests}</div>
                        <div className="stat-label">Approved</div>
                    </div>
                </div>

                {/* Rejected */}
                <div className="stat-card rejected">
                    <div className="stat-icon">❌</div>
                    <div className="stat-content">
                        <div className="stat-value">{stats.rejectedRequests}</div>
                        <div className="stat-label">Rejected</div>
                    </div>
                </div>

                {/* Approval Rate */}
                <div className="stat-card rate">
                    <div className="stat-icon">📈</div>
                    <div className="stat-content">
                        <div className="stat-value">{stats.approvalRate?.toFixed(1)}%</div>
                        <div className="stat-label">Approval Rate</div>
                        <div className="stat-detail">
                            {stats.approvalRate >= 80 ? 'Excellent' :
                             stats.approvalRate >= 60 ? 'Good' : 'Needs Improvement'}
                        </div>
                    </div>
                </div>

                {/* Average Review Time */}
                <div className="stat-card time">
                    <div className="stat-icon">⏱️</div>
                    <div className="stat-content">
                        <div className="stat-value">
                            {stats.avgReviewTimeMinutes < 60
                                ? `${stats.avgReviewTimeMinutes}m`
                                : `${(stats.avgReviewTimeMinutes / 60).toFixed(1)}h`}
                        </div>
                        <div className="stat-label">Avg Review Time</div>
                        <div className="stat-detail">
                            {stats.avgReviewTimeMinutes < 30 ? 'Fast' :
                             stats.avgReviewTimeMinutes < 120 ? 'Normal' : 'Slow'}
                        </div>
                    </div>
                </div>

                {/* Oldest Pending */}
                {stats.oldestPendingAgeHours > 0 && (
                    <div className="stat-card oldest">
                        <div className="stat-icon">🕐</div>
                        <div className="stat-content">
                            <div className="stat-value">
                                {stats.oldestPendingAgeHours < 24
                                    ? `${stats.oldestPendingAgeHours}h`
                                    : `${Math.floor(stats.oldestPendingAgeHours / 24)}d`}
                            </div>
                            <div className="stat-label">Oldest Pending</div>
                            {stats.oldestPendingAgeHours > 48 && (
                                <div className="stat-detail urgent">Urgent!</div>
                            )}
                        </div>
                    </div>
                )}

                {/* Expired */}
                <div className="stat-card expired">
                    <div className="stat-icon">⌛</div>
                    <div className="stat-content">
                        <div className="stat-value">{stats.expiredRequests}</div>
                        <div className="stat-label">Expired</div>
                    </div>
                </div>

                {/* Cancelled */}
                {stats.cancelledRequests > 0 && (
                    <div className="stat-card cancelled">
                        <div className="stat-icon">🚫</div>
                        <div className="stat-content">
                            <div className="stat-value">{stats.cancelledRequests}</div>
                            <div className="stat-label">Cancelled</div>
                        </div>
                    </div>
                )}
            </div>

            {/* Insights Section */}
            <div className="stats-insights">
                <h3>📊 Insights</h3>
                <div className="insights-grid">
                    <div className="insight-card">
                        <div className="insight-title">Review Efficiency</div>
                        <div className="insight-content">
                            {stats.avgReviewTimeMinutes < 60
                                ? '✅ Reviews are being processed quickly'
                                : '⚠️ Consider increasing reviewer bandwidth'}
                        </div>
                    </div>

                    <div className="insight-card">
                        <div className="insight-title">Quality Check</div>
                        <div className="insight-content">
                            {stats.approvalRate >= 70
                                ? '✅ AI-generated tests meet quality standards'
                                : '⚠️ Consider improving AI prompts or quality checks'}
                        </div>
                    </div>

                    {stats.pendingRequests > 5 && (
                        <div className="insight-card warning">
                            <div className="insight-title">Action Required</div>
                            <div className="insight-content">
                                ⚠️ {stats.pendingRequests} pending approvals need review
                            </div>
                        </div>
                    )}

                    {stats.oldestPendingAgeHours > 48 && (
                        <div className="insight-card urgent">
                            <div className="insight-title">Urgent</div>
                            <div className="insight-content">
                                🚨 Oldest request is {Math.floor(stats.oldestPendingAgeHours / 24)} days old!
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ApprovalStatistics;