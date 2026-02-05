import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { formatDistanceToNow } from 'date-fns';
import approvalService from '../../services/approvalService';
import StatusBadge from './StatusBadge';
import './PendingApprovals.css';

/**
 * Pending Approvals List
 */
const PendingApprovals = () => {
    const [approvals, setApprovals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [sortBy, setSortBy] = useState('createdAt');
    const [sortOrder, setSortOrder] = useState('asc');
    const navigate = useNavigate();

    useEffect(() => {
        loadPendingApprovals();
    }, []);

    const loadPendingApprovals = async () => {
        try {
            setLoading(true);
            setError(null);
            const data = await approvalService.getPendingApprovals();
            setApprovals(data);
        } catch (err) {
            console.error('Error loading pending approvals:', err);
            setError('Failed to load pending approvals');
        } finally {
            setLoading(false);
        }
    };

    const handleSort = (field) => {
        if (sortBy === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortBy(field);
            setSortOrder('asc');
        }
    };

    const getSortedApprovals = () => {
        const sorted = [...approvals].sort((a, b) => {
            let aValue, bValue;

            if (sortBy === 'createdAt') {
                aValue = new Date(a.createdAt);
                bValue = new Date(b.createdAt);
            } else if (sortBy === 'expiresAt') {
                aValue = new Date(a.expiresAt);
                bValue = new Date(b.expiresAt);
            }

            if (sortOrder === 'asc') {
                return aValue - bValue;
            } else {
                return bValue - aValue;
            }
        });

        return sorted;
    };

    const handleViewDetails = (id) => {
        navigate(`/approvals/${id}`);
    };

    if (loading) {
        return (
            <div className="pending-approvals">
                <div className="loading">Loading pending approvals...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="pending-approvals">
                <div className="error">{error}</div>
                <button onClick={loadPendingApprovals} className="btn-retry">
                    Retry
                </button>
            </div>
        );
    }

    const sortedApprovals = getSortedApprovals();

    return (
        <div className="pending-approvals">
            <div className="pending-approvals-header">
                <h2>Pending Approvals</h2>
                <div className="header-actions">
                    <button onClick={loadPendingApprovals} className="btn-refresh">
                        🔄 Refresh
                    </button>
                    <span className="count-badge">{approvals.length} pending</span>
                </div>
            </div>

            {approvals.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-icon">✅</div>
                    <h3>No Pending Approvals</h3>
                    <p>All approval requests have been reviewed!</p>
                </div>
            ) : (
                <div className="approvals-table-container">
                    <table className="approvals-table">
                        <thead>
                            <tr>
                                <th>Test Name</th>
                                <th>Requested By</th>
                                <th
                                    onClick={() => handleSort('createdAt')}
                                    className="sortable"
                                >
                                    Created {sortBy === 'createdAt' && (sortOrder === 'asc' ? '↑' : '↓')}
                                </th>
                                <th
                                    onClick={() => handleSort('expiresAt')}
                                    className="sortable"
                                >
                                    Expires {sortBy === 'expiresAt' && (sortOrder === 'asc' ? '↑' : '↓')}
                                </th>
                                <th>Framework</th>
                                <th>Status</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sortedApprovals.map(approval => (
                                <tr key={approval.id} className="approval-row">
                                    <td>
                                        <div className="test-name">{approval.testName || 'Untitled Test'}</div>
                                        {approval.sanitizationApplied && (
                                            <span className="sanitized-badge" title="PII/Secrets Sanitized">
                                                🔒 Sanitized ({approval.redactionCount})
                                            </span>
                                        )}
                                    </td>
                                    <td>
                                        <div className="requester-info">
                                            <div className="requester-name">{approval.requestedByName}</div>
                                            <div className="requester-email">{approval.requestedByEmail}</div>
                                        </div>
                                    </td>
                                    <td>
                                        <span title={new Date(approval.createdAt).toLocaleString()}>
                                            {formatDistanceToNow(new Date(approval.createdAt), { addSuffix: true })}
                                        </span>
                                    </td>
                                    <td>
                                        <span
                                            className={approval.daysUntilExpiration < 2 ? 'expires-soon' : ''}
                                            title={new Date(approval.expiresAt).toLocaleString()}
                                        >
                                            {approval.daysUntilExpiration} days
                                        </span>
                                    </td>
                                    <td>
                                        <span className="framework-badge">
                                            {approval.testFramework} / {approval.testLanguage}
                                        </span>
                                    </td>
                                    <td>
                                        <StatusBadge status={approval.status} />
                                    </td>
                                    <td>
                                        <button
                                            onClick={() => handleViewDetails(approval.id)}
                                            className="btn-review"
                                        >
                                            Review
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default PendingApprovals;