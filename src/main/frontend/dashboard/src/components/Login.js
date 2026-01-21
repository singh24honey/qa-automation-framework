import React, { useState } from 'react';
import './Login.css';

const Login = ({ onLogin }) => {
    const [apiKey, setApiKey] = useState('');
    const [error, setError] = useState('');

    const handleSubmit = (e) => {
        e.preventDefault();

        if (!apiKey.trim()) {
            setError('Please enter an API key');
            return;
        }

        // Store API key and notify parent
        localStorage.setItem('apiKey', apiKey.trim());
        onLogin(apiKey.trim());
    };

    return (
        <div className="login-container">
            <div className="login-box">
                <h1>QA Dashboard</h1>
                <p>Enter your API key to continue</p>

                <form onSubmit={handleSubmit}>
                    <input
                        type="text"
                        placeholder="Enter API Key"
                        value={apiKey}
                        onChange={(e) => {
                            setApiKey(e.target.value);
                            setError('');
                        }}
                        className="api-key-input"
                        autoFocus
                    />

                    {error && <p className="error-message">{error}</p>}

                    <button type="submit" className="btn-login">
                        Login
                    </button>
                </form>

                <div className="login-help">
                    <p className="help-text">
                        Don't have an API key? Create one using the backend API.
                    </p>
                </div>
            </div>
        </div>
    );
};

export default Login;