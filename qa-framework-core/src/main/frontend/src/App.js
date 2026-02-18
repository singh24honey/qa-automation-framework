import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/Login';
import Dashboard from './components/Dashboard';
import PendingApprovals from './components/approvals/PendingApprovals';
import ApprovalDetail from './components/approvals/ApprovalDetail';
import ApprovalStatistics from './components/approvals/ApprovalStatistics';
import AgentDashboard from './components/agent/AgentDashboard';
import AITestGeneration from './components/ai-test-gen/AITestGeneration';
import Navigation from './components/Navigation';
import './App.css';

function App() {
    const [isAuthenticated, setIsAuthenticated] = useState(
        () => localStorage.getItem('apiKey') !== null
    );

    const handleLogin = (apiKey) => {
        localStorage.setItem('apiKey', apiKey);
        setIsAuthenticated(true);
    };

    const handleLogout = () => {
        localStorage.removeItem('apiKey');
        localStorage.removeItem('userId');
        localStorage.removeItem('userName');
        localStorage.removeItem('userEmail');
        setIsAuthenticated(false);
    };

    if (!isAuthenticated) {
        return <Login onLogin={handleLogin} />;
    }

    return (
        <Router>
            <div className="app">
                <Navigation onLogout={handleLogout} />
                <div className="app-content">
                    <Routes>
                        <Route path="/" element={<Dashboard />} />

                        {/* ⭐ APPROVAL ROUTES */}
                        <Route path="/approvals" element={<PendingApprovals />} />
                        <Route path="/approvals/:id" element={<ApprovalDetail />} />
                        <Route path="/approval-stats" element={<ApprovalStatistics />} />

                        {/* ⭐ WEEK 17 DAY 3 - NEW ROUTES */}
                        <Route path="/agent" element={<AgentDashboard />} />
                        <Route path="/ai-test-gen" element={<AITestGeneration />} />

                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>
                </div>
            </div>
        </Router>
    );
}

export default App;