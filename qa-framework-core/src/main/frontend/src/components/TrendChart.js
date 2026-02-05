import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { format, parseISO } from 'date-fns';
import './TrendChart.css';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

const TrendChart = ({ trends }) => {
  if (!trends || trends.length === 0) {
    return (
      <div className="chart-container">
        <h3 className="chart-title">Execution Trends</h3>
        <p className="chart-empty">No trend data available</p>
      </div>
    );
  }

  // Format dates for display
  const data = {
    labels: trends.map(t => {
      // Handle both string dates and LocalDate objects
      const dateStr = typeof t.date === 'string' ? t.date : t.date.toString();
      try {
        return format(parseISO(dateStr), 'MMM dd');
      } catch (e) {
        return dateStr;
      }
    }),
    datasets: [
      {
        label: 'Passed',
        data: trends.map(t => t.passed || 0),
        borderColor: 'rgb(16, 185, 129)',
        backgroundColor: 'rgba(16, 185, 129, 0.1)',
        fill: true,
        tension: 0.4
      },
      {
        label: 'Failed',
        data: trends.map(t => t.failed || 0),
        borderColor: 'rgb(239, 68, 68)',
        backgroundColor: 'rgba(239, 68, 68, 0.1)',
        fill: true,
        tension: 0.4
      }
    ]
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
      },
      title: {
        display: false
      },
      tooltip: {
        mode: 'index',
        intersect: false,
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          precision: 0
        }
      }
    }
  };

  return (
    <div className="chart-container">
      <h3 className="chart-title">Execution Trends</h3>
      <div className="chart-wrapper">
        <Line data={data} options={options} />
      </div>
    </div>
  );
};

export default TrendChart;