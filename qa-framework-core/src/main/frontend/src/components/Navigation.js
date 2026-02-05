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

                <Link to="/approvals" className={`nav-link ${isActive('/approvals')}`}>
                    <span className="link-icon">✅</span>
                    <span className="link-text">Pending Approvals</span>
                </Link>

                <Link to="/approval-stats" className={`nav-link ${isActive('/approval-stats')}`}>
                    <span className="link-icon">📈</span>
                    <span className="link-text">Approval Stats</span>
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