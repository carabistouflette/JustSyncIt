<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: {
    type: Object,
    required: true,
    default: () => ({})
  },
  loading: Boolean
})

const chartData = computed(() => {
  const entries = Object.entries(props.data)
    .sort((a, b) => b[1] - a[1]) // Sort by count descending
    .slice(0, 6) // Top 6

  const total = entries.reduce((sum, [_, count]) => sum + count, 0)
  const other = Object.values(props.data).reduce((sum, count) => sum + count, 0) - total
  
  if (other > 0) {
    entries.push(['other', other])
  }

  const grandTotal = total + (other > 0 ? other : 0)
  
  if (grandTotal === 0) return []

  let currentAngle = 0
  const colors = [
    '#6366f1', '#ec4899', '#8b5cf6', '#10b981', '#f59e0b', '#3b82f6', '#94a3b8'
  ]
  
  return entries.map(([label, value], index) => {
    const percentage = (value / grandTotal) * 100
    const angle = (value / grandTotal) * 360
    const segment = {
      label,
      value,
      percentage,
      color: colors[index % colors.length],
      startAngle: currentAngle,
      endAngle: currentAngle + angle
    }
    currentAngle += angle
    return segment
  })
})

const conicGradient = computed(() => {
  if (!chartData.value.length) return 'none'
  const stops = chartData.value.map(segment => `${segment.color} 0 ${segment.endAngle}deg`)
  // Valid conic-gradient syntax: color start-angle end-angle, color start-angle end-angle...
  // Simplified: Using a cumulative approach usually requires specific syntax
  // Let's use simpler mapped string: "color 0deg 10deg, color2 10deg 50deg..."
  
  return 'conic-gradient(' + chartData.value.map(s => 
    `${s.color} ${s.startAngle}deg ${s.endAngle}deg`
  ).join(', ') + ')'
})

</script>

<template>
  <div class="chart-container">
    <div v-if="loading" class="chart-loading">
        <div class="spinner"></div>
    </div>
    <div v-else-if="!chartData.length" class="chart-empty">
        No Data
    </div>
    <div v-else class="chart-content">
      <div class="pie-chart" :style="{ background: conicGradient }">
        <div class="chart-hole"></div>
      </div>
      
      <div class="chart-legend">
        <div v-for="segment in chartData" :key="segment.label" class="legend-item">
          <span class="color-dot" :style="{ background: segment.color }"></span>
          <span class="label">{{ segment.label }}</span>
          <span class="value">{{ segment.percentage.toFixed(1) }}%</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chart-container {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.chart-content {
  display: flex;
  align-items: center;
  gap: 2rem;
  width: 100%;
}

.pie-chart {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  position: relative;
  flex-shrink: 0;
  box-shadow: 0 0 20px rgba(0,0,0,0.2);
}

.chart-hole {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 70%;
  height: 70%;
  background: var(--bg-primary);
  border-radius: 50%;
}

.chart-legend {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: 0.75rem;
}

.legend-item {
  display: flex;
  align-items: center;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.color-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 0.5rem;
}

.label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 0.5rem;
}

.value {
  font-weight: 600;
  color: var(--text-primary);
}

.chart-loading, .chart-empty {
  color: var(--text-muted);
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
