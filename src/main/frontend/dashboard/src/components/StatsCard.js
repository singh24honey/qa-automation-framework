import React from 'react';
import './StatsCard.css';

const StatsCard = ({ title, value, subtitle, icon, color }) => {
  return (
    <div className={`stats-card stats-card-${color}`}>
      <div className="stats-card-header">
        <div className="stats-card-icon">{icon}</div>
        <h3 className="stats-card-title">{title}</h3>
      </div>
      <div className="stats-card-body">
        <p className="stats-card-value">{value}</p>
        {subtitle && <p className="stats-card-subtitle">{subtitle}</p>}
      </div>
    </div>
  );
};

export default StatsCard;