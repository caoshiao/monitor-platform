import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import AdminApp from './AdminApp.vue'
import './styles/theme.css'

Vue.use(ElementUI)
Vue.config.productionTip = false

new Vue({ render: h => h(AdminApp) }).$mount('#app')
