import axios from 'axios';

const API_BASE_URL = '/api/v1';

const getApiKey = () => localStorage.getItem('apiKey');

const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json'
    }
});

apiClient.interceptors.request.use(config => {
    const apiKey = getApiKey();
    if (apiKey) {
        config.headers['X-API-Key'] = apiKey;
    }
    return config;
});

/**
 * Git Configuration Service
 */
const gitService = {
    /**
     * Get all Git configurations
     */
    getAllConfigurations: async () => {
        const response = await apiClient.get('/git/configurations');
        return response.data;
    },

    /**
     * Get active Git configurations
     */
    getActiveConfigurations: async () => {
        const response = await apiClient.get('/git/configurations/active');
        return response.data;
    },

    /**
     * Get default Git configuration
     */
    getDefaultConfiguration: async () => {
        const response = await apiClient.get('/git/configurations/default');
        return response.data;
    },

    /**
     * Get Git configuration by ID
     */
    getConfigurationById: async (id) => {
        const response = await apiClient.get('/git/configurations/${id}');
        return response.data;
    },

    /**
     * Create new Git configuration
     */
    createConfiguration: async (config) => {
        const response = await apiClient.post('/git/configurations', config);
        return response.data.data;
    },

    /**
     * Update Git configuration
     */
    updateConfiguration: async (id, config) => {
        const response = await apiClient.put('/git/configurations/${id}', config);
        return response.data.data;
    },

    /**
     * Delete Git configuration
     */
    deleteConfiguration: async (id) => {
        const response = await apiClient.delete('/git/configurations/${id}');
        return response.data;
    },

    /**
     * Validate Git configuration
     */
    validateConfiguration: async (id) => {
        const response = await apiClient.post('/git/configurations/${id}/validate');
        return response.data.data;
    },

    /**
     * Toggle active status
     */
    toggleActive: async (id, active) => {
        const response = await apiClient.patch('/git/configurations/${id}/active, null, {
            params: { active }
        });
        return response.data.data;
    }
};

export default gitService;