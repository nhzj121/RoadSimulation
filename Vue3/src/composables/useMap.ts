// composables/useMap.ts
import { ref, onMounted, onUnmounted } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'

export function useMap(containerId, options = {}) {
    const map = ref(null)
    const AMap = ref(null)
    const isMapReady = ref(false)

    const defaultOptions = {
        viewMode: '3D',
        zoom: 11,
        center: [104.066158, 30.657150],
        ...options
    }

    const initMap = async () => {
        try {
            window._AMapSecurityConfig = {
                securityJsCode: "9df38c185c95fa1dbf78a1082b64f668",
            }

            const AMapInstance = await AMapLoader.load({
                key: "e0ea478e44e417b4c2fc9a54126debaa",
                version: "2.0",
                plugins: ["AMap.Scale", "AMap.Driving", "AMap.PlaceSearch"]
            })

            AMap.value = AMapInstance
            map.value = new AMapInstance.Map(containerId, defaultOptions)
            isMapReady.value = true

            return { map: map.value, AMap: AMapInstance }
        } catch (error) {
            console.error('地图初始化失败:', error)
            throw error
        }
    }

    const destroyMap = () => {
        if (map.value) {
            map.value.destroy()
            map.value = null
            isMapReady.value = false
        }
    }

    const addDrivingRoute = (points, policy = 'LEAST_TIME') => {
        if (!map.value || !AMap.value) return null

        const driving = new AMap.value.Driving({
            map: map.value,
            policy: AMap.value.DrivingPolicy[policy] || AMap.value.DrivingPolicy.LEAST_TIME,
        })

        driving.search(points, (status, result) => {
            if (status === 'complete') {
                console.log('绘制驾车路线完成')
            } else {
                console.error('获取驾车数据失败：' + result)
            }
        })

        return driving
    }

    onMounted(() => {
        // 自动初始化
        if (containerId) {
            initMap()
        }
    })

    onUnmounted(() => {
        destroyMap()
    })

    return {
        map,
        AMap,
        isMapReady,
        initMap,
        destroyMap,
        addDrivingRoute
    }
}