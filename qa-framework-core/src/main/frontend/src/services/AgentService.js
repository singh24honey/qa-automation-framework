import axios from 'axios';

const API_BASE_URL = '/api/v1/agents';

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

const agentService = {
  /**
   * Start a new agent execution
   *
   * @param {Object} request - Agent start request
   * @param {string} request.agentType - PLAYWRIGHT_TEST_GENERATOR, FLAKY_TEST_FIXER, SELF_HEALING_TEST_FIXER, TEST_FAILURE_ANALYZER, QUALITY_MONITOR
   * @param {string} request.goalType - GENERATE_TEST, FIX_FLAKY_TEST, HEAL_BROKEN_TEST
   * @param {Object} request.parameters - Agent-specific parameters (jiraKey, framework, etc.)
   * @param {number} request.maxIterations - Max iterations (default: 20)
   * @param {number} request.maxAICost - Max AI cost in dollars (default: 5.0)
   * @param {number} request.approvalTimeoutSeconds - Approval timeout (default: 3600)
   * @returns {Promise<Object>} AgentExecutionResponse
   */
  startAgent: async (request) => {
    try {
      const response = await api.post('/start', request);
      console.log('Agent started:', response.data);
      return response.data;
    } catch (error) {
      console.error('Error starting agent:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Get all currently running agents
   *
   * @returns {Promise<Array>} List of AgentExecutionResponse
   */
  getRunningAgents: async () => {
    try {
      const response = await api.get('/running');
      return response.data;
    } catch (error) {
      console.error('Error fetching running agents:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Get specific agent execution details
   *
   * @param {string} executionId - Agent execution ID
   * @returns {Promise<Object>} AgentExecutionResponse
   */
  getAgentExecution: async (executionId) => {
    try {
      const response = await api.get(`/${executionId}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching agent execution:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Get action history for an agent execution
   *
   * @param {string} executionId - Agent execution ID
   * @returns {Promise<Array>} List of AgentActionResponse
   */
  getAgentActions: async (executionId) => {
    try {
      const response = await api.get(`/${executionId}/actions`);
      return response.data;
    } catch (error) {
      console.error('Error fetching agent actions:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Stop a running agent
   *
   * @param {string} executionId - Agent execution ID
   * @returns {Promise<void>}
   */
  stopAgent: async (executionId) => {
    try {
      await api.post(`/${executionId}/stop`);
      console.log('Agent stopped:', executionId);
    } catch (error) {
      console.error('Error stopping agent:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Get available agent types
   *
   * @returns {Promise<Array>} List of agent types
   */
  getAgentTypes: async () => {
    try {
      const response = await api.get('/types');
      return response.data;
    } catch (error) {
      console.error('Error fetching agent types:', error);
      throw error.response?.data || error;
    }
  },

  /**
   * Get all agent execution history, sorted by startedAt DESC.
   *
   * @param {string|null} status - Optional filter: RUNNING, SUCCEEDED, FAILED, STOPPED, TIMEOUT
   * @param {number} limit - Max results (default 50)
   * @returns {Promise<Array>} List of AgentExecutionResponse
   */
  getExecutionHistory: async (status = null, limit = 50) => {
    try {
      const params = { limit };
      if (status) params.status = status;
      const response = await api.get('/history', { params });
      return response.data;
    } catch (error) {
      console.error('Error fetching agent history:', error);
      throw error.response?.data || error;
    }
  },
};

export default agentService;

// Append history and export at module level handled in file — see below