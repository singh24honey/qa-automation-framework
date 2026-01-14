import React from 'react';
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend
} from 'chart.js';
import { Pie } from 'react-chartjs-2';
import './BrowserChart.css';

ChartJS.register(ArcElement, Tooltip, Legend);

const BrowserChart = ({ browserStats }) => {
  if (!browserStats || browserStats.length === 0) {
    return (
      <div className="chart-container">
        <h3 className="chart-title">Browser Distribution</h3>
        <p className="chart-empty">No browser data available</p>
      </div>
    );
  }

  const data = {
    labels: browserStats.map(b => b.browser || 'Unknown'),
    datasets: [
      {
        label: 'Executions',
        data: browserStats.map(b => b.totalExecutions || 0),
        backgroundColor: [
          'rgba(59, 130, 246, 0.8)',
          'rgba(16, 185, 129, 0.8)',
          'rgba(245, 158, 11, 0.8)',
          'rgba(139, 92, 246, 0.8)',
          'rgba(239, 68, 68, 0.8)',
        ],
        borderColor: [
          'rgb(59, 130, 246)',
          'rgb(16, 185, 129)',
          'rgb(245, 158, 11)',
          'rgb(139, 92, 246)',
          'rgb(239, 68, 68)',
        ],
        borderWidth: 2,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right',
      },
      tooltip: {
        callbacks: {
          label: function(context) {
            const label = context.label || '';
            const value = context.parsed || 0;
            const browser = browserStats.find(b => b.browser === label);
            const passRate = browser?.passRate || 0;
            return `${label}: ${value} (${passRate}% pass rate)`;
          }
        }
      }
    }
  };

  return (
    <div className="chart-container">
      <h3 className="chart-title">Browser Distribution</h3>
      <div className="chart-wrapper">
        <Pie data={data} options={options} />
      </div>
    </div>
  );
};

export default BrowserChart;