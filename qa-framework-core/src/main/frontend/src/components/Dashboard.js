import React, { useState, useEffect } from 'react';
import StatsCard from './StatsCard';
import TrendChart from './TrendChart';
import BrowserChart from './BrowserChart';
import apiService from '../services/apiService';
import { format, subDays } from 'date-fns';
import './Dashboard.css';

const Dashboard = () => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [dashboardData, setDashboardData] = useState(null);
  const [startDate, setStartDate] = useState(format(subDays(new Date(), 30), 'yyyy-MM-dd'));
  const [endDate, setEndDate] = useState(format(new Date(), 'yyyy-MM-dd'));

  const fetchDashboard = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await apiService.getDashboard(startDate, endDate);
      setDashboardData(data);
    } catch (err) {
      setError('Failed to load dashboard data. Please check your API key and try again.');
      console.error('Dashboard error:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboard();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [startDate, endDate]);

  const handleRefresh = () => {
    fetchDashboard();
  };

  if (loading) {
    return (
      <div className="dashboard-loading">
        <div className="spinner"></div>
        <p>Loading dashboard...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="dashboard-error">
        <p className="error-message">{error}</p>
        <button onClick={handleRefresh} className="btn-retry">Retry</button>
      </div>
    );
  }

  const stats = dashboardData?.overallStats || {};
  const trends = dashboardData?.trends || [];
  const browserStats = dashboardData?.browserStats || [];

  return (
    <div className="dashboard">
      {/* Header */}
      <div className="dashboard-header">
        <div>
          <h1 className="dashboard-title">QA Dashboard</h1>
          <p className="dashboard-subtitle">Test Execution Analytics</p>
        </div>
        <button onClick={handleRefresh} className="btn-refresh">
          🔄 Refresh
        </button>
      </div>

      {/* Date Range Filter */}
      <div className="date-filter">
        <div className="date-input-group">
          <label>Start Date:</label>
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className="date-input"
          />
        </div>
        <div className="date-input-group">
          <label>End Date:</label>
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="date-input"
          />
        </div>
        <button onClick={fetchDashboard} className="btn-apply">
          Apply
        </button>
      </div>

      {/* Stats Cards */}
      <div className="stats-grid">
        <StatsCard
          title="Total Executions"
          value={stats.totalExecutions || 0}
          icon="📊"
          color="blue"
        />
        <StatsCard
          title="Passed"
          value={stats.passedExecutions || 0}
          subtitle={`${stats.passRate || 0}% pass rate`}
          icon="✅"
          color="green"
        />
        <StatsCard
          title="Failed"
          value={stats.failedExecutions || 0}
          subtitle={`${stats.failRate || 0}% fail rate`}
          icon="❌"
          color="red"
        />
        <StatsCard
          title="Errors"
          value={stats.errorExecutions || 0}
          icon="⚠️"
          color="yellow"
        />
      </div>

      {/* Charts */}
      <div className="charts-grid">
        <div className="chart-full-width">
          <TrendChart trends={trends} />
        </div>
        <div className="chart-half-width">
          <BrowserChart browserStats={browserStats} />
        </div>
      </div>
    </div>
  );
};

export default Dashboard;