import React, { useState } from 'react';
import './Login.css';

// POST /api/v1/auth/api-keys is a PUBLIC endpoint (no auth required)
// so we can call it directly from the login screen to bootstrap the demo
const API_BASE_URL = process.env.REACT_APP_API_URL || '';

const Login = ({ onLogin }) => {
    // Tab state: 'login' or 'create'
    const [activeTab, setActiveTab] = useState('login');

    // Login tab state
    const [apiKey, setApiKey] = useState('');
    const [loginError, setLoginError] = useState('');

    // Create tab state
    const [keyName, setKeyName] = useState('');
    const [keyDescription, setKeyDescription] = useState('');
    const [createError, setCreateError] = useState('');
    const [createSuccess, setCreateSuccess] = useState(null); // holds the created key value
    const [creating, setCreating] = useState(false);

    // ----------------------------------------------------------------
    // Login handler — validates the key against the backend before login
    // ----------------------------------------------------------------
    const handleLogin = async (e) => {
        e.preventDefault();
        setLoginError('');

        if (!apiKey.trim()) {
            setLoginError('Please enter an API key');
            return;
        }

        // Quick validation — hit /actuator/health with the key to confirm it works
        try {
            const resp = await fetch(`${API_BASE_URL}/actuator/health`);
            if (!resp.ok) {
                setLoginError('Cannot reach backend. Check your connection.');
                return;
            }
        } catch {
            setLoginError('Cannot reach backend. Is the server running?');
            return;
        }

        localStorage.setItem('apiKey', apiKey.trim());
        onLogin(apiKey.trim());
    };

    // ----------------------------------------------------------------
    // Create API key handler — calls the public POST endpoint
    // ----------------------------------------------------------------
    const handleCreate = async (e) => {
        e.preventDefault();
        setCreateError('');
        setCreateSuccess(null);

        if (!keyName.trim()) {
            setCreateError('Key name is required');
            return;
        }

        setCreating(true);
        try {
            const resp = await fetch(`${API_BASE_URL}/api/v1/auth/api-keys`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: keyName.trim(),
                    description: keyDescription.trim() || 'Created from QA Dashboard UI',
                }),
            });

            const body = await resp.json();

            if (!resp.ok) {
                setCreateError(body?.message || body?.error || 'Failed to create API key');
                return;
            }

            // The key value is only shown once — surface it prominently
            const createdKey = body?.data?.keyValue || body?.data?.key || body?.keyValue;
            setCreateSuccess(createdKey);

            // Clear the form
            setKeyName('');
            setKeyDescription('');
        } catch (err) {
            setCreateError('Cannot reach backend. Is the server running?');
        } finally {
            setCreating(false);
        }
    };

    // Auto-login with newly created key
    const handleUseCreatedKey = () => {
        if (createSuccess) {
            localStorage.setItem('apiKey', createSuccess);
            onLogin(createSuccess);
        }
    };

    const copyToClipboard = (text) => {
        navigator.clipboard.writeText(text).catch(() => {
            // fallback for older browsers
            const ta = document.createElement('textarea');
            ta.value = text;
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            document.body.removeChild(ta);
        });
    };

    return (
        <div className="login-container">
            <div className="login-box">
                <div className="login-header">
                    <span className="login-icon">🧪</span>
                    <h1>QA Automation Framework</h1>
                    <p className="login-subtitle">AI-Powered Test Generation Platform</p>
                </div>

                {/* Tab switcher */}
                <div className="login-tabs">
                    <button
                        className={`tab-btn ${activeTab === 'login' ? 'active' : ''}`}
                        onClick={() => { setActiveTab('login'); setLoginError(''); setCreateSuccess(null); }}
                    >
                        🔑 Login
                    </button>
                    <button
                        className={`tab-btn ${activeTab === 'create' ? 'active' : ''}`}
                        onClick={() => { setActiveTab('create'); setCreateError(''); }}
                    >
                        ✨ Create API Key
                    </button>
                </div>

                {/* ---- LOGIN TAB ---- */}
                {activeTab === 'login' && (
                    <form onSubmit={handleLogin} className="login-form">
                        <div className="form-group">
                            <label className="form-label">API Key</label>
                            <input
                                type="text"
                                placeholder="Paste your API key here"
                                value={apiKey}
                                onChange={(e) => {
                                    setApiKey(e.target.value);
                                    setLoginError('');
                                }}
                                className="api-key-input"
                                autoFocus
                            />
                        </div>

                        {loginError && <p className="error-message">⚠️ {loginError}</p>}

                        <button type="submit" className="btn-login">
                            Login →
                        </button>

                        <p className="help-text">
                            Don't have a key? Use the{' '}
                            <button
                                type="button"
                                className="link-btn"
                                onClick={() => setActiveTab('create')}
                            >
                                Create API Key
                            </button>{' '}
                            tab.
                        </p>
                    </form>
                )}

                {/* ---- CREATE API KEY TAB ---- */}
                {activeTab === 'create' && (
                    <form onSubmit={handleCreate} className="login-form">
                        {/* Success banner — shown after key is created */}
                        {createSuccess && (
                            <div className="success-banner">
                                <p className="success-title">✅ API Key Created!</p>
                                <p className="success-warn">⚠️ Copy this key now — it won't be shown again.</p>
                                <div className="key-display">
                                    <code className="key-value">{createSuccess}</code>
                                    <button
                                        type="button"
                                        className="btn-copy"
                                        onClick={() => copyToClipboard(createSuccess)}
                                    >
                                        📋 Copy
                                    </button>
                                </div>
                                <button
                                    type="button"
                                    className="btn-login btn-use-key"
                                    onClick={handleUseCreatedKey}
                                >
                                    Use This Key & Login →
                                </button>
                            </div>
                        )}

                        {!createSuccess && (
                            <>
                                <div className="form-group">
                                    <label className="form-label">Key Name <span className="required">*</span></label>
                                    <input
                                        type="text"
                                        placeholder="e.g. demo-key, my-local-key"
                                        value={keyName}
                                        onChange={(e) => { setKeyName(e.target.value); setCreateError(''); }}
                                        className="api-key-input"
                                        autoFocus
                                        required
                                    />
                                </div>

                                <div className="form-group">
                                    <label className="form-label">Description <span className="optional">(optional)</span></label>
                                    <input
                                        type="text"
                                        placeholder="What will you use this key for?"
                                        value={keyDescription}
                                        onChange={(e) => setKeyDescription(e.target.value)}
                                        className="api-key-input"
                                    />
                                </div>

                                {createError && <p className="error-message">⚠️ {createError}</p>}

                                <button type="submit" className="btn-login" disabled={creating}>
                                    {creating ? '⏳ Creating...' : 'Create API Key'}
                                </button>
                            </>
                        )}
                    </form>
                )}
            </div>
        </div>
    );
};

export default Login;