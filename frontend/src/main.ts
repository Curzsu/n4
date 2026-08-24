import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus is loaded on demand (see vite.config.ts). These pieces are
// used programmatically / as directives, so their styles are imported here:
import { ElLoading } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import router from './router'
import App from './App.vue'
import './styles/global.scss'
import { setupPermissionDirective } from './directives/permission'

const app = createApp(App)
app.use(createPinia())
app.use(router)
setupPermissionDirective(app)
// Registers the v-loading directive (and the loading service).
app.use(ElLoading)
app.mount('#app')
