// types/map.ts
import {Component, defineAsyncComponent} from "vue";

export interface MapFeature {
    name: string
    component: Component
    enabled: boolean
    position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right'
}

export interface MapConfig {
    center: [number, number]
    zoom: number
    features: MapFeature[]
}

// config/mapConfig.ts
export const defaultMapConfig: MapConfig = {
    center: [104.06585, 30.657361],
    zoom: 12,
    features: [
        {
            name: 'poiSearch',
            component: defineAsyncComponent(() => import('../components/SearchPanel.vue')),
            enabled: true,
            position: 'top-left'
        },
        {
            name: 'routePlanning',
            component: defineAsyncComponent(() => import('../components/RoutePlanning.vue')),
            enabled: false,
            position: 'top-left'
        }
    ]
}