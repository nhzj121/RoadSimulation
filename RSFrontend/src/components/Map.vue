<!-- Map.vue -->
<template>
  <div class="map-page">
    <!-- 功能控制面板 -->
    <div class="feature-controls">
      <h3>功能控制</h3>
      <el-checkbox-group v-model="enabledFeatures">
        <el-checkbox label="search">搜索面板</el-checkbox>
<!--        <el-checkbox label="drawing">绘制工具</el-checkbox>-->
        <el-checkbox label="poi">POI管理</el-checkbox>
      </el-checkbox-group>
    </div>

    <!-- 基础地图 -->
    <BaseMap
        ref="baseMapRef"
        :center="mapConfig.center"
        :zoom="mapConfig.zoom"
        @map-loaded="onMapLoaded"
    >
      <!-- 动态加载的功能组件 -->
      <SearchPanel v-if="features.search" />
<!--      <DrawingTools v-if="features.drawing" />-->
      <POIManager v-if="features.poi" ref="poiManagerRef" />
    </BaseMap>

    <!-- 加载状态显示 -->
    <div v-if="mapLoading" class="map-loading">
      <el-alert title="地图加载中..." type="info" :closable="false" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import BaseMap from './BaseMap.vue'
import SearchPanel from './SearchPanel.vue'
import DrawingTools from './DrawingTools.vue'
import POIManager from './POIManager.vue'

// 地图配置
const mapConfig = {
  center: [104.06585, 30.657361] as [number, number],
  zoom: 12
}

// 功能开关
const enabledFeatures = ref(['search'/*, 'drawing'*/, 'poi'])

// 计算属性，将数组转换为对象便于v-if使用
const features = computed(() => ({
  search: enabledFeatures.value.includes('search'),
  // drawing: enabledFeatures.value.includes('drawing'),
  poi: enabledFeatures.value.includes('poi')
}))

// 组件引用
const baseMapRef = ref<InstanceType<typeof BaseMap>>()
const poiManagerRef = ref<InstanceType<typeof POIManager>>()

// 地图加载完成回调
const onMapLoaded = (mapContext: any) => {
  console.log('主组件: 地图加载完成', mapContext)

  // 可以在这里执行地图加载后的初始化操作
  // 例如：自动开始POI搜索
  setTimeout(() => {
    if (features.value.poi && poiManagerRef.value) {
      // poiManagerRef.value.startPOISearch()
    }
  }, 2000)
}

// 暴露方法给外部（如果需要）
defineExpose({
  getMap: () => baseMapRef.value?.getMap(),
  startPOISearch: () => poiManagerRef.value?.startPOISearch(),
  toggleFeature: (feature: string, enabled: boolean) => {
    if (enabled) {
      if (!enabledFeatures.value.includes(feature)) {
        enabledFeatures.value.push(feature)
      }
    } else {
      enabledFeatures.value = enabledFeatures.value.filter(f => f !== feature)
    }
  }
})

onMounted(() => {
  console.log('Map.vue 已挂载，启用功能:', enabledFeatures.value)
})
</script>

<style scoped>
/* 修复全屏样式 */
.map-page {
  position: fixed; /* 改为 fixed 定位确保全屏 */
  top: 0;
  left: 0;
  width: 100vw; /* 使用视口宽度 */
  height: 100vh; /* 使用视口高度 */
  margin: 0;
  padding: 0;
  overflow: hidden; /* 防止滚动条 */
}

/* 功能控制面板 - 保持原有样式但确保在正确层级 */
.feature-controls {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1000;
  background: white;
  padding: 15px;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.1);
  min-width: 200px;
}

.feature-controls h3 {
  margin: 0 0 10px 0;
  font-size: 16px;
  color: #333;
}

.map-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 999;
}

:deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>