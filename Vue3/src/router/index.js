// router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import MapContainer from '@/components/MapContainer.vue'
import POIManager from '@/components/POIManager.vue'
import ProcessingChainManager from '@/components/ProcessingChainManager.vue'
import { ElMessage } from 'element-plus'
import { createModeNavigationGuard } from '../navigation/modeNavigation'

const routes = [
    { path: '/sandbox/executions/:id/map', name: 'SandboxMap', component: () => import('@/views/SandboxMap.vue') },
    { path: '/sandbox/executions/:id/results', name: 'SandboxResults', component: () => import('@/views/SandboxResults.vue') },
    { path: '/sandbox/tasks', name: 'SandboxTasks', component: () => import('@/views/SandboxTasks.vue') },
    { path: '/sandbox/jobs/:id', name: 'SandboxJobProgress', component: () => import('@/views/SandboxTasks.vue') },
    {
        path: '/sandbox',
        name: 'SandboxConfiguration',
        component: () => import('@/views/SandboxConfiguration.vue')
    },
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
        path: '/processing-chains',
        name: 'ProcessingChainManager',
        component: ProcessingChainManager
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(createModeNavigationGuard(message => ElMessage.warning(message)))

export default router
