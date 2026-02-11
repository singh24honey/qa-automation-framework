import axios from 'axios';

const API_BASE_URL = '/api/v1';

// Get API key from localStorage (same as existing apiService)
const getApiKey = () => localStorage.getItem('apiKey');

// Create axios instance with auth header
const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json'
    }
});

// Add API key to all requests
apiClient.interceptors.request.use(config => {
    const apiKey = getApiKey();
    if (apiKey) {
        config.headers['X-API-Key'] = apiKey;
    }
    return config;
});

/**
 * Approval Service
 * Handles all approval-related API calls
 */
const approvalService = {
    /**
     * Get all pending approval requests
     */
    getPendingApprovals: async () => {
        const response = await apiClient.get('/approvals/pending');
        return response.data.data;
    },

    /**
     * Get approval request by ID
     */
    getApprovalById: async (id) => {
        const response = await apiClient.get(`/approvals/${id}`);
        return response.data.data;
    },

    /**
     * Get requests by requester
     */
    getMyRequests: async (requesterId) => {
        const response = await apiClient.get(`/approvals/requester/${requesterId}`);
        return response.data.data;
    },

    /**
     * Get requests reviewed by me
     */
    getMyReviews: async (reviewerId) => {
        const response = await apiClient.get(`/approvals/reviewer/${reviewerId}`);
        return response.data.data;
    },

    /**
     * Approve a request
     */
    approveRequest: async (id, decision) => {
        const response = await apiClient.post(`/approvals/${id}/approve`, decision);
        return response.data.data;
    },

    /**
     * Reject a request
     */
    rejectRequest: async (id, decision) => {
        const response = await apiClient.post(`/approvals/${id}/reject`, decision);
        return response.data.data;
    },

    /**
     * Cancel a request
     */
    cancelRequest: async (id, userId) => {
        const response = await apiClient.delete(`/approvals/${id}`, {
            headers: {
                'X-User-Id': userId
            }
        });
        return response.data.data;
    },

    /**
     * Get approval statistics
     */
    getStatistics: async () => {
        const response = await apiClient.get('/approvals/statistics');
        return response.data.data;
    },

    /**
     * Create approval request (called from AI generation)
     */
    createApprovalRequest: async (request) => {
        const response = await apiClient.post('/approvals', request);
        return response.data.data;
    },


        triggerGitCommit: async (approvalId) => {
            const response = await apiClient.post(`/approvals/${approvalId}/trigger-git`);
            return response.data.data;
        },

        /**
         * Retry a failed Git operation
         */
        retryGitOperation: async (approvalId) => {
            const response = await apiClient.post(`/approvals/${approvalId}/retry-git`);
            return response.data.data;
        }
};

export default approvalService;