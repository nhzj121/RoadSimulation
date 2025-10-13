<!-- RoutePlanning.vue -->
<template>
  <div class="route-planning-panel">
    <el-card class="planning-card">
      <template #header>
        <div class="card-header">
          <span>路线规划</span>
          <el-button v-if="currentRoute" type="danger" size="small" @click="clearRoute">清除路线</el-button>
        </div>
      </template>

      <div class="input-group">
        <el-input
            v-model="startName"
            placeholder="起始地点"
            :prefix-icon="Location"
            clearable
        />
        <el-input
            v-model="endName"
            placeholder="目的地点"
            :prefix-icon="Aim"
            clearable
        />
      </div>

      <el-select
          v-model="routeRule"
          placeholder="选择策略"
          class="strategy-select"
      >
        <el-option
            v-for="item in routeRules"
            :key="item.value"
            :label="item.label"
            :value="item.value"
        />
      </el-select>

      <el-button
          type="primary"
          @click="calculateRoute"
          :loading="loading"
          class="plan-button"
      >
        {{ loading ? '规划中...' : '开始规划' }}
      </el-button>

      <!-- 路线信息显示 -->
      <div v-if="routeInfo" class="route-info">
        <el-divider />
        <h4>路线信息</h4>
        <div class="info-item">
          <span class="label">距离：</span>
          <span class="value">{{ routeInfo.distance }}公里</span>
        </div>
        <div class="info-item">
          <span class="label">时间：</span>
          <span class="value">{{ routeInfo.duration }}分钟</span>
        </div>
        <div class="info-item">
          <span class="label">费用：</span>
          <span class="value">{{ routeInfo.cost }}元</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, Ref, onUnmounted } from 'vue'
import { Location, Aim } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

interface MapContext {
  map: any
  AMap: any
}

interface RouteInfo {
  distance: string
  duration: string
  cost: string
  steps: Array<{
    instruction: string
    distance: string
    duration: string
    road: string
  }>
  path: number[][] // 路线坐标点
}

interface RouteResponse {
  success: boolean
  data?: {
    distance: string
    duration: string
    cost: string
    steps: Array<{
      instruction: string
      distance: string
      duration: string
      road: string
    }>
    path: number[][]
  }
  message?: string
}

const mapContext = inject('mapContext') as Ref<MapContext | undefined>
const startName = ref('')
const endName = ref('')
const routeRule = ref<number>(0)
const loading = ref(false)
const currentRoute = ref<any>(null)
const routeInfo = ref<RouteInfo | null>(null)

const routeRules = [
  { value: 0, label: '速度优先' },
  { value: 1, label: '费用优先' },
  { value: 2, label: '距离优先' },
  { value: 41, label: '躲避拥堵＋少收费' },
  { value: 32, label: '默认' },
]

// 调用后端路线规划接口
const calculateRoute = async () => {
  if (!mapContext?.value) {
    ElMessage.error('地图上下文未找到')
    return
  }

  if (!startName.value.trim() || !endName.value.trim()) {
    ElMessage.warning('请输入起始地点和目的地点')
    return
  }

  loading.value = true
  routeInfo.value = null

  try {
    // 调用后端路线规划API
    const response = await fetch('/api/route/plan', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        start: startName.value.trim(),
        end: endName.value.trim(),
        strategy: routeRule.value
      })
    })

    const result: RouteResponse = await response.json()

    if (result.success && result.data) {
      routeInfo.value = result.data
      drawRouteOnMap(result.data.path)
      ElMessage.success('路线规划成功')
    } else {
      ElMessage.error(result.message || '路线规划失败')
    }
  } catch (error) {
    console.error('路线规划请求失败:', error)
    ElMessage.error('路线规划请求失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

// 在地图上绘制路线
const drawRouteOnMap = (path: number[][]) => {
  if (!mapContext?.value) return

  const { map, AMap } = mapContext.value

  // 清除之前的路线
  clearRoute()

  // 创建折线对象
  const polyline = new AMap.Polyline({
    path: path,
    strokeColor: '#409EFF', // 蓝色线条
    strokeWeight: 6,
    strokeOpacity: 0.8,
    strokeStyle: 'solid',
    lineJoin: 'round'
  })

  // 将折线添加到地图
  map.add([polyline])

  // 自适应地图视野
  map.setFitView([polyline])

  // 保存当前路线对象以便清除
  currentRoute.value = polyline

  // 添加起点和终点标记
  addRouteMarkers(path[0], path[path.length - 1])
}

// 添加起点终点标记
const addRouteMarkers = (start: number[], end: number[]) => {
  if (!mapContext?.value) return

  const { map, AMap } = mapContext.value

  // 起点标记
  const startMarker = new AMap.Marker({
    position: start,
    icon: new AMap.Icon({
      size: new AMap.Size(25, 34),
      image: 'https://webapi.amap.com/theme/v1.3/markers/n/start.png'
    }),
    offset: new AMap.Pixel(-12, -34)
  })

  // 终点标记
  const endMarker = new AMap.Marker({
    position: end,
    icon: new AMap.Icon({
      size: new AMap.Size(25, 34),
      image: 'https://webapi.amap.com/theme/v1.3/markers/n/end.png'
    }),
    offset: new AMap.Pixel(-12, -34)
  })

  map.add([startMarker, endMarker])
}

const clearRoute = () => {
  if (mapContext?.value && currentRoute.value) {
    const { map } = mapContext.value
    map.remove([currentRoute.value])
    currentRoute.value = null
    routeInfo.value = null

    // 清除所有覆盖物（包括标记）
    map.clearMap()
    ElMessage.info('已清除路线')
  }
}

// 组件卸载时清除路线
onUnmounted(() => {
  clearRoute()
})

defineExpose({
  calculateRoute,
  clearRoute
})
</script>

<style scoped>
.route-planning-panel {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 1000;
  width: 320px;
}

.planning-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.strategy-select {
  width: 100%;
  margin-bottom: 16px;
}

.plan-button {
  width: 100%;
}

.route-info {
  margin-top: 16px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 4px 0;
}

.info-item .label {
  color: #666;
  font-weight: 500;
}

.info-item .value {
  color: #333;
  font-weight: 600;
}

:deep(.el-card__header) {
  padding: 12px 16px;
}

:deep(.el-divider) {
  margin: 16px 0;
}
</style>