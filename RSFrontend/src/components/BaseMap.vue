<!-- components/BaseMap.vue - 修复版本 -->
<template>
  <div class="map-container">
    <div id="amap" ref="mapContainer"></div>
    <slot></slot>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, provide, nextTick } from 'vue'
import AMapLoader from "@amap/amap-jsapi-loader"
import { ElMessage } from 'element-plus'

interface MapContext {
  map: any
  AMap: any
}

const props = defineProps<{
  center?: [number, number]
  zoom?: number
  plugins?: string[]
}>()

const emit = defineEmits<{
  'map-loaded': [context: MapContext]
  'map-error': [error: any]
}>()

const mapContainer = ref<HTMLElement>()
const mapContext = ref<MapContext>()
const isLoading = ref(false)

// 错误消息处理函数
function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  } else if (typeof error === 'string') {
    return error
  } else {
    return '未知错误'
  }
}

// 提供地图上下文给子组件使用
provide('mapContext', mapContext)

onMounted(async () => {
  // 等待DOM完全渲染
  await nextTick()
  await initMap()
})

async function initMap() {
  if (isLoading.value) return
  isLoading.value = true

  // 确保安全配置
  if (!window._AMapSecurityConfig) {
    window._AMapSecurityConfig = {
      securityJsCode: "7d397a0f0f35945a7565712f031a3872",
    };
  }

  try {
    console.log('开始加载高德地图...')

    const AMap = await AMapLoader.load({
      key: '9dd28cbcc8e274814fa5e5ca2ebea1be', // 请确保这个key有效
      version: '2.0',
      plugins: props.plugins || [
        'AMap.ToolBar', 'AMap.Scale', 'AMap.Geolocation',
        'AMap.PlaceSearch', 'AMap.Geocoder', 'AMap.HawkEye',
        'AMap.MapType', 'AMap.MouseTool', 'AMap.AutoComplete',
        'AMap.Driving', 'AMap.MarkerClusterer'
      ],
    })

    console.log('高德地图API加载成功')

    // 检查容器是否存在
    if (!mapContainer.value) {
      throw new Error('地图容器未找到')
    }

    const map = new AMap.Map('amap', {
      zoom: props.zoom || 12,
      center: props.center || [104.06585, 30.657361],
      viewMode: "3D",
    })

    console.log('地图实例创建成功')

    // 添加基础控件
    map.addControl(new AMap.ToolBar({ position: 'LT' }))
    map.addControl(new AMap.Scale())
    map.addControl(new AMap.HawkEye())
    map.addControl(new AMap.MapType())

    mapContext.value = { map, AMap }
    emit('map-loaded', mapContext.value)

    console.log('BaseMap: 地图初始化完成')
    ElMessage.success('地图加载成功')

  } catch (error) {
    console.error('BaseMap: 地图初始化失败', error)
    emit('map-error', error)
    ElMessage.error(`地图加载失败: ${getErrorMessage(error)}`)
  } finally {
    isLoading.value = false
  }
}

onUnmounted(() => {
  if (mapContext.value?.map) {
    mapContext.value.map.destroy()
    console.log('BaseMap: 地图已销毁')
  }
})

// 暴露方法给父组件
defineExpose({
  getMap: () => mapContext.value?.map,
  getAMap: () => mapContext.value?.AMap,
  setCenter: (lng: number, lat: number) => {
    mapContext.value?.map?.setCenter([lng, lat])
  },
  setZoom: (zoom: number) => {
    mapContext.value?.map?.setZoom(zoom)
  },
  reloadMap: initMap // 提供重新加载方法
})
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100vh;
  position: relative;
}

#amap {
  width: 100%;
  height: 100%;
}
</style>