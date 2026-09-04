<template>
  <div class="app-shell">
    <main class="page-wrap page-wrap--display">
      <display-home v-if="route === '/'" /><module-page v-else :module="route.slice(1)" />
    </main>
  </div>
</template>
<script>
import DisplayHome from './views/DisplayHome.vue'
import ModulePage from './views/ModulePage.vue'
const HOME_ROUTE = '/'
export default {
  components: { DisplayHome, ModulePage },
  data() {
    return { route: this.getCurrentRoute() }
  },
  mounted() {
    window.addEventListener('hashchange', this.handleRouteChange)
  },
  beforeDestroy() {
    window.removeEventListener('hashchange', this.handleRouteChange)
  },
  methods: {
    getCurrentRoute() {
      return window.location.hash.replace('#', '') || HOME_ROUTE
    },
    handleRouteChange() {
      this.route = this.getCurrentRoute()
    }
  }
}
</script>
