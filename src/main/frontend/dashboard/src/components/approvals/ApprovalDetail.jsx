import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import approvalService from '../../services/approvalService';
import CodeViewer from './CodeViewer';
import StatusBadge from './StatusBadge';
import ApprovalModal from './ApprovalModal';
import GitStatusBadge from './GitStatusBadge'; // ✅ NEW
import './ApprovalDetail.css';

const ApprovalDetail = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [approval, setApproval] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [showApproveModal, setShowApproveModal] = useState(false);
    const [showRejectModal, setShowRejectModal] = useState(false);
    const [processing, setProcessing] = useState(false);

    const loadApprovalDetails = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const data = await approvalService.getApprovalById(id);
            setApproval(data);
        } catch (err) {
            console.error('Error loading approval details:', err);
            setError('Failed to load approval details');
        } finally {
            setLoading(false);
        }
    }, [id]);

    const handleRetryGit = async (approvalId) => {
            try {
                setProcessing(true);
                await approvalService.retryGitOperation(approvalId);
                await loadApprovalDetails(); // Refresh
            } catch (err) {
                console.error('Error retrying Git operation:', err);
                alert('Failed to retry Git operation: ' + (err.response?.data?.message || err.message));
            } finally {
                setProcessing(false);
            }
        };


    useEffect(() => {
        loadApprovalDetails();
    }, [loadApprovalDetails]);

    // ... rest of the component stays the same
    const handleApprove = async (decision) => {
        try {
            setProcessing(true);
            await approvalService.approveRequest(id, decision);
            setShowApproveModal(false);
            await loadApprovalDetails();
        } catch (err) {
            console.error('Error approving request:', err);
            alert('Failed to approve request');
        } finally {
            setProcessing(false);
        }
    };

    const handleManualGitTrigger = async (approvalId) => {
            if (!window.confirm('Trigger Git commit for this approval?')) {
                return;
            }

            try {
                setProcessing(true);
                await approvalService.triggerGitCommit(approvalId);
                await loadApprovalDetails(); // Refresh
            } catch (err) {
                console.error('Error triggering Git commit:', err);
                alert('Failed to trigger Git commit: ' + (err.response?.data?.message || err.message));
            } finally {
                setProcessing(false);
            }
        };

    const handleReject = async (decision) => {
        try {
            setProcessing(true);
            await approvalService.rejectRequest(id, decision);
            setShowRejectModal(false);
            await loadApprovalDetails();
        } catch (err) {
            console.error('Error rejecting request:', err);
            alert('Failed to reject request');
        } finally {
            setProcessing(false);
        }
    };

    const handleBack = () => {
        navigate('/approvals');
    };

    if (loading) {
        return <div className="approval-detail loading">Loading...</div>;
    }

    if (error) {
        return (
            <div className="approval-detail error">
                <p>{error}</p>
                <button onClick={loadApprovalDetails}>Retry</button>
            </div>
        );
    }

    if (!approval) {
        return <div className="approval-detail">Approval not found</div>;
    }

    const isPending = approval.status === 'PENDING_APPROVAL';

    return (
        <div className="approval-detail">
            <div className="detail-header">
                <button onClick={handleBack} className="btn-back">
                    ← Back to List
                </button>
                <div className="header-info">
                    <h2>{approval.testName || 'Untitled Test'}</h2>
                    <StatusBadge status={approval.status} />
                </div>
            </div>

            <div className="metadata-grid">
                <div className="metadata-card">
                    <h3>Request Information</h3>
                    <div className="metadata-row">
                        <span className="label">Type:</span>
                        <span className="value">{approval.requestType}</span>
                    </div>
                    <div className="metadata-row">
                        <span className="label">Framework:</span>
                        <span className="value">{approval.testFramework}</span>
                    </div>
                    <div className="metadata-row">
                        <span className="label">Language:</span>
                        <span className="value">{approval.testLanguage}</span>
                    </div>
                </div>

                <div className="metadata-card">
                    <h3>Requester</h3>
                    <div className="metadata-row">
                        <span className="label">Name:</span>
                        <span className="value">{approval.requestedByName}</span>
                    </div>
                    <div className="metadata-row">
                        <span className="label">Created:</span>
                        <span className="value">
                            {format(new Date(approval.createdAt), 'PPpp')}
                        </span>
                    </div>
                </div>
            </div>

            <div className="code-section">
                <CodeViewer
                    code={approval.generatedContent}
                    language={approval.testLanguage?.toLowerCase() || 'java'}
                    title="Generated Test Code"
                />
            </div>

            {isPending && (
                <div className="action-buttons">
                    <button
                        onClick={() => setShowApproveModal(true)}
                        className="btn-approve"
                        disabled={processing}
                    >
                        ✓ Approve
                    </button>
                    <button
                        onClick={() => setShowRejectModal(true)}
                        className="btn-reject"
                        disabled={processing}
                    >
                        ✗ Reject
                    </button>
                </div>
            )}

            {showApproveModal && (
                <ApprovalModal
                    type="approve"
                    onConfirm={handleApprove}
                    onCancel={() => setShowApproveModal(false)}
                    processing={processing}
                />
            )}

            {showRejectModal && (
                <ApprovalModal
                    type="reject"
                    onConfirm={handleReject}
                    onCancel={() => setShowRejectModal(false)}
                    processing={processing}
                />
            )}
        </div>
    );
};

export default ApprovalDetail;