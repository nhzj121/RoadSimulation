// router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import MapContainer from '@/components/MapContainer.vue'
import POIManager from '@/components/POIManager.vue'
import PressureTest from "@/components/PressureTest.vue";

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
    },
    {
        path: '/pressure-test',
        name: 'PressureTest',
        component: PressureTest
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router