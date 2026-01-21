import React, { useState } from 'react';
import './ApprovalModal.css';

/**
 * Modal for Approve/Reject actions
 */
const ApprovalModal = ({ type, onConfirm, onCancel, processing }) => {
    const [reviewerId] = useState(localStorage.getItem('userId') || '');
    const [reviewerName] = useState(localStorage.getItem('userName') || 'Unknown Reviewer');
    const [reviewerEmail] = useState(localStorage.getItem('userEmail') || '');
    const [notes, setNotes] = useState('');
    const [rejectionReason, setRejectionReason] = useState('');

    const isApprove = type === 'approve';

    const handleSubmit = (e) => {
        e.preventDefault();

        // Validation
        if (!isApprove && !rejectionReason.trim()) {
            alert('Please provide a rejection reason');
            return;
        }

        // Build decision object
        const decision = {
            reviewerId: reviewerId || undefined,
            reviewerName: reviewerName,
            reviewerEmail: reviewerEmail || undefined,
            notes: notes.trim() || undefined,
            rejectionReason: isApprove ? undefined : rejectionReason.trim()
        };

        onConfirm(decision);
    };

    return (
        <div className="modal-overlay" onClick={onCancel}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isApprove ? '✓ Approve Test' : '✗ Reject Test'}</h2>
                    <button className="btn-close" onClick={onCancel} type="button">
                        ×
                    </button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <p className="modal-description">
                            {isApprove
                                ? 'You are about to approve this AI-generated test. It will be available for execution.'
                                : 'You are about to reject this AI-generated test. The requester will be notified.'}
                        </p>

                        {!isApprove && (
                            <div className="form-group">
                                <label htmlFor="rejectionReason">
                                    Rejection Reason <span className="required">*</span>
                                </label>
                                <textarea
                                    id="rejectionReason"
                                    value={rejectionReason}
                                    onChange={(e) => setRejectionReason(e.target.value)}
                                    placeholder="Explain why this test is being rejected..."
                                    rows="4"
                                    required={!isApprove}
                                    disabled={processing}
                                    className="textarea-input"
                                />
                                <p className="field-hint">
                                    Be specific about what needs to be improved or why this test cannot be approved.
                                </p>
                            </div>
                        )}

                        <div className="form-group">
                            <label htmlFor="notes">
                                Additional Notes <span className="optional">(optional)</span>
                            </label>
                            <textarea
                                id="notes"
                                value={notes}
                                onChange={(e) => setNotes(e.target.value)}
                                placeholder="Add any additional comments..."
                                rows="3"
                                disabled={processing}
                                className="textarea-input"
                            />
                        </div>

                        <div className="reviewer-info">
                            <h4>Reviewer Information</h4>
                            <div className="info-row">
                                <span className="info-label">Name:</span>
                                <span className="info-value">{reviewerName}</span>
                            </div>
                            {reviewerEmail && (
                                <div className="info-row">
                                    <span className="info-label">Email:</span>
                                    <span className="info-value">{reviewerEmail}</span>
                                </div>
                            )}
                            <p className="info-hint">
                                This information will be recorded with your decision.
                            </p>
                        </div>
                    </div>

                    <div className="modal-footer">
                        <button
                            type="button"
                            onClick={onCancel}
                            className="btn-cancel"
                            disabled={processing}
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className={isApprove ? 'btn-confirm-approve' : 'btn-confirm-reject'}
                            disabled={processing}
                        >
                            {processing
                                ? '⏳ Processing...'
                                : (isApprove ? '✓ Approve' : '✗ Reject')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default ApprovalModal;