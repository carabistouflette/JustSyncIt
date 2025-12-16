<script setup>
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import { computed } from 'vue'

ChartJS.register(ArcElement, Tooltip, Legend)

const props = defineProps({
  filesProcessed: { type: Number, default: 0 },
  bytesProcessed: { type: Number, default: 0 }
})

const data = computed(() => ({
  labels: ['Processed', 'Remaining (Est)'],
  datasets: [{
    backgroundColor: ['#6366f1', '#1a1b2e'],
    borderColor: 'transparent',
    data: [props.filesProcessed, Math.max(0, 1000 - props.filesProcessed)] // Mock remaining for demo
  }]
}))

const options = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false }
  },
  cutout: '75%'
}
</script>

<template>
  <div class="chart-container">
    <Doughnut :data="data" :options="options" />
    <div class="chart-overlay">
      <div class="chart-value">{{ filesProcessed }}</div>
      <div class="chart-label">Files</div>
    </div>
  </div>
</template>

<style scoped>
.chart-container {
  position: relative;
  height: 200px;
  width: 100%;
}

.chart-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}

.chart-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
}

.chart-label {
  font-size: 0.85rem;
  color: var(--text-secondary);
}
</style>
