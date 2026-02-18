import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import './Navigation.css';

const Navigation = ({ onLogout }) => {
    const location = useLocation();

    const isActive = (path) => {
        return location.pathname === path ? 'active' : '';
    };

    return (
        <nav className="navigation">
            <div className="nav-brand">
                <span className="brand-icon">🧪</span>
                <span className="brand-text">QA Automation Framework</span>
            </div>

            <div className="nav-links">
                <Link to="/" className={`nav-link ${isActive('/')}`}>
                    <span className="link-icon">📊</span>
                    <span className="link-text">Dashboard</span>
                </Link>

                {/* ⭐ WEEK 17 DAY 3 - NEW LINKS */}
                <Link to="/agent" className={`nav-link ${isActive('/agent')}`}>
                    <span className="link-icon">🤖</span>
                    <span className="link-text">Agents</span>
                </Link>

                <Link to="/ai-test-gen" className={`nav-link ${isActive('/ai-test-gen')}`}>
                    <span className="link-icon">⚡</span>
                    <span className="link-text">AI Test Gen</span>
                </Link>

                <Link to="/approvals" className={`nav-link ${isActive('/approvals')}`}>
                    <span className="link-icon">✅</span>
                    <span className="link-text">Approvals</span>
                </Link>

                <Link to="/approval-stats" className={`nav-link ${isActive('/approval-stats')}`}>
                    <span className="link-icon">📈</span>
                    <span className="link-text">Stats</span>
                </Link>
            </div>

            <button onClick={onLogout} className="btn-logout">
                <span className="logout-icon">🚪</span>
                <span className="logout-text">Logout</span>
            </button>
        </nav>
    );
};

export default Navigation;