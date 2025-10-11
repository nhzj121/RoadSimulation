<!-- components/SearchPanel.vue -->
<template>
  <div class="search-panel">
    <div style="display: flex; align-items: center; margin-bottom: 10px;">
      <el-input
          v-model="searchText"
          placeholder="请输入搜索关键字"
          style="margin-right: 10px;"
      />
      <el-button @click="handleSearch">搜索</el-button>
    </div>

    <div style="display: flex; gap: 10px; margin-bottom: 10px;">
      <el-input v-model="startName" placeholder="起始地点" />
      <el-input v-model="endName" placeholder="目的地点" />
      <el-select v-model="routeRule" placeholder="选择策略">
        <el-option
            v-for="item in routeRules"
            :key="item.value"
            :label="item.label"
            :value="item.value"
        />
      </el-select>
      <el-button @click="calculateRoute">路径规划</el-button>
    </div>

    <!-- 路径规划结果 -->
    <div
        v-if="routeArr.length > 0"
        class="route-results"
    >
      <h4>路线规划</h4>
      <div
          v-for="(item, index) in routeArr"
          :key="index"
          class="route-step"
      >
        {{ item.instruction }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, watch } from 'vue'
import { ElMessage } from 'element-plus'

interface MapContext {
  map: any
  AMap: any
}

interface RouteStep {
  instruction: string
}

const mapContext = inject<{ value: MapContext }>('mapContext')

const searchText = ref('')
const startName = ref('')
const endName = ref('')
const routeRule = ref(0)
const routeArr = ref<RouteStep[]>([])

const routeRules = [
  { value: 0, label: '速度优先' },
  { value: 1, label: '费用优先' },
  { value: 2, label: '距离优先' },
  { value: 41, label: '躲避拥堵＋少收费' },
  { value: 32, label: '默认' },
]

// 搜索功能
const handleSearch = () => {
  if (!mapContext?.value || !searchText.value.trim()) {
    ElMessage.warning('请输入搜索关键字')
    return
  }

  const { map, AMap } = mapContext.value
  const placeSearch = new AMap.PlaceSearch({
    map: map,
    city: '成都',
    pageSize: 20,
    citylimit: false,
    autoFitView: true,
  })

  placeSearch.search(searchText.value)
  ElMessage.success('开始搜索')
}

// 路径规划功能
const calculateRoute = () => {
  if (!mapContext?.value || !startName.value || !endName.value) {
    ElMessage.warning('请输入起始地和目的地')
    return
  }

  const { map, AMap } = mapContext.value
  const driving = new AMap.Driving({
    map,
    policy: routeRule.value
  })

  const points = [
    { keyword: startName.value, city: '兰州' },
    { keyword: endName.value, city: '兰州' }
  ]

  driving.search(points, (status: string, result: any) => {
    if (status === 'complete') {
      routeArr.value = result.routes[0].steps
      ElMessage.success('路径规划完成')
    } else {
      ElMessage.error('路径规划失败')
    }
  })
}

// 监听路由规则变化
watch(routeRule, (newVal) => {
  console.log('路由策略改为:', newVal)
})

defineExpose({
  search: handleSearch,
  calculateRoute,
  clearRoute: () => {
    routeArr.value = []
    startName.value = ''
    endName.value = ''
  }
})
</script>

<style scoped>
.search-panel {
  position: absolute;
  top: 10px;
  left: 100px;
  z-index: 999;
  background: white;
  padding: 10px;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.1);
  min-width: 500px;
}

.route-results {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
  margin-top: 10px;
}

.route-step {
  padding: 5px 0;
  border-bottom: 1px solid #f0f0f0;
  font-size: 12px;
}

.route-step:last-child {
  border-bottom: none;
}
</style>