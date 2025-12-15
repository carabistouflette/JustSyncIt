<script setup>
import { Bar } from 'vue-chartjs'
import { Chart as ChartJS, Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale } from 'chart.js'
import { computed } from 'vue'

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

const props = defineProps({
  history: { type: Array, default: () => [] }
})

const data = computed(() => ({
  labels: props.history.slice(0, 7).reverse().map(h => new Date(h.timestamp).toLocaleDateString()),
  datasets: [{
    label: 'Files Processed',
    backgroundColor: '#8b5cf6',
    borderRadius: 4,
    data: props.history.slice(0, 7).reverse().map(h => h.filesProcessed)
  }]
}))

const options = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: 'rgba(26, 27, 46, 0.9)',
      titleColor: '#fff',
      bodyColor: '#a4a6b3',
      padding: 10,
      cornerRadius: 8,
      displayColors: false
    }
  },
  scales: {
    x: {
      grid: { display: false, drawBorder: false },
      ticks: { color: '#6b6d7c', font: { size: 10 } }
    },
    y: {
      grid: { color: 'rgba(99, 102, 241, 0.1)', drawBorder: false },
      ticks: { color: '#6b6d7c', font: { size: 10 } }
    }
  }
}
</script>

<template>
  <div class="chart-wrapper">
    <Bar :data="data" :options="options" />
  </div>
</template>

<style scoped>
.chart-wrapper {
  height: 200px;
  width: 100%;
}
</style>
