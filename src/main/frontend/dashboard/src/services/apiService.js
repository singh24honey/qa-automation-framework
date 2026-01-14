import axios from 'axios';

const API_BASE_URL = '/api/v1';

// Get API key from localStorage
const getApiKey = () => {
  return localStorage.getItem('apiKey') || '';
};

// Configure axios defaults
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add API key to every request
api.interceptors.request.use((config) => {
  const apiKey = getApiKey();
  if (apiKey) {
    config.headers['X-API-Key'] = apiKey;
  }
  return config;
});

const apiService = {
  // Get complete dashboard report
  getDashboard: async (startDate, endDate) => {
    try {
      const params = {};
      if (startDate) params.startDate = startDate;
      if (endDate) params.endDate = endDate;

      const response = await api.get('/reports/dashboard', { params });
      return response.data.data;
    } catch (error) {
      console.error('Error fetching dashboard:', error);
      throw error;
    }
  },

  // Get execution stats
  getStats: async (startDate, endDate) => {
    try {
      const params = {};
      if (startDate) params.startDate = startDate;
      if (endDate) params.endDate = endDate;

      const response = await api.get('/reports/stats', { params });
      return response.data.data;
    } catch (error) {
      console.error('Error fetching stats:', error);
      throw error;
    }
  },

  // Get execution trends
  getTrends: async (startDate, endDate) => {
    try {
      const params = {};
      if (startDate) params.startDate = startDate;
      if (endDate) params.endDate = endDate;

      const response = await api.get('/reports/trends', { params });
      return response.data.data;
    } catch (error) {
      console.error('Error fetching trends:', error);
      throw error;
    }
  },

  // Get browser stats
  getBrowserStats: async (startDate, endDate) => {
    try {
      const params = {};
      if (startDate) params.startDate = startDate;
      if (endDate) params.endDate = endDate;

      const response = await api.get('/reports/browser-stats', { params });
      return response.data.data;
    } catch (error) {
      console.error('Error fetching browser stats:', error);
      throw error;
    }
  },

  // Set API key
  setApiKey: (apiKey) => {
    localStorage.setItem('apiKey', apiKey);
  },

  // Clear API key
  clearApiKey: () => {
    localStorage.removeItem('apiKey');
  }
};

export default apiService;