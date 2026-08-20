<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark"><i class="el-icon-data-analysis" /></span>
        <span><b>MONITOR</b><small> DISPLAY</small></span>
      </div>
      <div class="page-identity"><i class="el-icon-monitor" /> 实时监控展示中心</div>
      <div class="topbar-meta"><span class="pulse" /> LIVE</div>
    </header>
    <main class="page-wrap"><overview /></main>
  </div>
</template>
<script>
import Overview from './views/Overview.vue'
export default { components: { Overview } }
</script>
