// router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import MapContainer from '@/components/MapContainer.vue'
import POIManager from '@/components/POIManager.vue'

const routes = [
    {
        path: '/',
        name: 'MapContainer',
        component: MapContainer
    },
    {
        path: '/poi-manager',
        name: 'POIManager',
        component: POIManager
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router