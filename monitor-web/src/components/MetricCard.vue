<template>
  <div class="metric-card">
    <div class="metric-card__head"><span>{{ label }}</span><i :class="icon" /></div>
    <div class="metric-card__value" :class="tone">{{ value }}<em>{{ unit }}</em></div>
    <div v-if="progress !== null" class="metric-card__bar"><span :class="tone" :style="{ width: `${Math.min(Math.max(progress, 0), 100)}%` }" /></div>
  </div>
</template>
<script>
export default { props: { label: String, value: [String, Number], unit: String, icon: { type: String, default: 'el-icon-data-line' }, progress: { type: Number, default: null }, tone: { type: String, default: 'blue' } } }
</script>
