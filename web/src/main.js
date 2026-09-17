import { createApp } from 'vue'
import './styles/main.css'
import App from './App.vue'
import router from './router'

// .use(router) 把路由装到整个应用上，之后 <RouterView /> 才能工作
createApp(App).use(router).mount('#app')
