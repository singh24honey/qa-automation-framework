import axios from 'axios';

const API_BASE_URL = '/api/v1/ai-tests';

// Get API key from localStorage
const getApiKey = () => {
  return localStorage.getItem('apiKey') || '';
};

// Configure axios instance
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

const aiTestGenService = {
  /**
   * Generate test from JIRA story
   *
   * @param {Object} request - Test generation request
   * @param {string} request.jiraStoryKey - JIRA story key (e.g., "PROJ-123")
   * @param {string} request.framework - PLAYWRIGHT, CUCUMBER, TESTNG
   * @param {string} request.userPrompt - Optional custom prompt
   * @param {string} request.targetBrowser - CHROME, FIREFOX, SAFARI
   * @param {number} request.timeout - Timeout in milliseconds
   * @returns {Promise<Object>} TestGenerationResponse
   */
  generateTest: async (request) => {
    try {
      const response = await api.post('/generate', request);
      console.log('Test generated:', response.data);
      return response.data;
    } catch (error) {
      console.error('Error generating test:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Generate tests for multiple JIRA stories (batch)
   *
   * @param {Array<Object>} requests - Array of test generation requests
   * @returns {Promise<Array>} List of TestGenerationResponse
   */
  generateBatchTests: async (requests) => {
    try {
      const response = await api.post('/generate/batch', requests);
      console.log('Batch tests generated:', response.data);
      return response.data;
    } catch (error) {
      console.error('Error generating batch tests:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Get all tests pending review
   *
   * @returns {Promise<Array>} List of AIGeneratedTest
   */
  getPendingTests: async () => {
    try {
      const response = await api.get('/pending-reviews');
      return response.data;
    } catch (error) {
      console.error('Error fetching pending tests:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Approve or reject a generated test
   *
   * @param {Object} request - Approval request
   * @param {string} request.testId - Test ID
   * @param {boolean} request.approved - Approval decision
   * @param {string} request.reviewerName - Reviewer name
   * @param {string} request.comments - Optional comments
   * @returns {Promise<Object>} TestApprovalResponse
   */
  approveTest: async (request) => {
    try {
      const response = await api.post('/approve', request);
      console.log('Test approval processed:', response.data);
      return response.data;
    } catch (error) {
      console.error('Error approving test:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Batch approve/reject multiple tests
   *
   * @param {Array<Object>} requests - Array of approval requests
   * @returns {Promise<Array>} List of TestApprovalResponse
   */
  batchApprove: async (requests) => {
    try {
      const response = await api.post('/approve/batch', requests);
      console.log('Batch approval processed:', response.data);
      return response.data;
    } catch (error) {
      console.error('Error batch approving tests:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Get tests reviewed by specific reviewer
   *
   * @param {string} reviewerName - Reviewer name
   * @returns {Promise<Array>} List of AIGeneratedTest
   */
  getTestsByReviewer: async (reviewerName) => {
    try {
      const response = await api.get(`/reviewer/${reviewerName}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching tests by reviewer:', error);
      throw error.response?.data || error;
    }
  },
};

export default aiTestGenService;