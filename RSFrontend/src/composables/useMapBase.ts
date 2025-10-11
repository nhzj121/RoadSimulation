// composables/useMapBase.ts
import { ref, onUnmounted } from 'vue'

export function useMapBase() {
    const map = ref<any>(null)
    const AMap = ref<any>(null)

    // 基础地图方法
    const setCenter = (lng: number, lat: number) => {
        map.value?.setCenter([lng, lat])
    }

    const setZoom = (zoom: number) => {
        map.value?.setZoom(zoom)
    }

    onUnmounted(() => {
        map.value?.destroy()
    })

    return {
        map,
        AMap,
        setCenter,
        setZoom
    }
}
