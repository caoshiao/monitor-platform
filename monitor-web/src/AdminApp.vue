<template>
  <div class="app-shell admin-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark brand-mark--admin"><i class="el-icon-setting" /></span>
        <span><b>MONITOR</b><small> ADMIN</small></span>
      </div>
      <div class="page-identity"><i class="el-icon-lock" /> 监控平台后台管理</div>
      <div class="topbar-meta">ADMIN CONSOLE</div>
    </header>
    <main class="page-wrap"><admin /></main>
  </div>
</template>
<script>
import Admin from './views/Admin.vue'
export default { components: { Admin } }
</script>
