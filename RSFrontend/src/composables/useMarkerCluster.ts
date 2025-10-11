// composables/useMarkerCluster.ts
import {ref} from "vue";

export function useMarkerCluster(map: any, AMap: any) {
    const clusterEnabled = ref(true)
    let markerClusterer: any = null

    const initCluster = (markers: any[]) => {
        // 点聚合初始化逻辑
    }

    const toggleCluster = (enabled: boolean) => {
        clusterEnabled.value = enabled
        // 切换逻辑
    }

    return {
        clusterEnabled,
        initCluster,
        toggleCluster
    }
}