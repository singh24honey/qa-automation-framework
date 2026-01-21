import React from 'react';
import './StatusBadge.css';

/**
 * Status Badge Component
 */
const StatusBadge = ({ status }) => {
    const getStatusClass = () => {
        switch (status) {
            case 'PENDING_APPROVAL':
                return 'status-pending';
            case 'APPROVED':
                return 'status-approved';
            case 'REJECTED':
                return 'status-rejected';
            case 'EXPIRED':
                return 'status-expired';
            case 'CANCELLED':
                return 'status-cancelled';
            default:
                return 'status-default';
        }
    };

    const getStatusText = () => {
        switch (status) {
            case 'PENDING_APPROVAL':
                return 'Pending';
            case 'APPROVED':
                return 'Approved';
            case 'REJECTED':
                return 'Rejected';
            case 'EXPIRED':
                return 'Expired';
            case 'CANCELLED':
                return 'Cancelled';
            default:
                return status;
        }
    };

    return (
        <span className={`status-badge ${getStatusClass()}`}>
            {getStatusText()}
        </span>
    );
};

export default StatusBadge;