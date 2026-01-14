import React, { useState, useEffect } from 'react';
import Dashboard from './components/Dashboard';
import apiService from './services/apiService';
import './App.css';

function App() {
  const [apiKey, setApiKey] = useState('');
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    // Check if API key exists in localStorage
    const storedKey = localStorage.getItem('apiKey');
    if (storedKey) {
      setApiKey(storedKey);
      setIsAuthenticated(true);
    }
  }, []);

  const handleLogin = (e) => {
    e.preventDefault();
    if (apiKey.trim()) {
      apiService.setApiKey(apiKey);
      setIsAuthenticated(true);
    }
  };

  const handleLogout = () => {
    apiService.clearApiKey();
    setApiKey('');
    setIsAuthenticated(false);
  };

  if (!isAuthenticated) {
    return (
      <div className="login-container">
        <div className="login-box">
          <h1>QA Dashboard</h1>
          <p>Enter your API key to continue</p>
          <form onSubmit={handleLogin}>
            <input
              type="text"
              placeholder="Enter API Key"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              className="api-key-input"
            />
            <button type="submit" className="btn-login">
              Login
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="App">
      <nav className="app-nav">
        <div className="nav-brand">QA Automation Framework</div>
        <button onClick={handleLogout} className="btn-logout">
          Logout
        </button>
      </nav>
      <Dashboard />
    </div>
  );
}

export default App;