<!-- components/DrawingTools.vue -->
<template>
  <div class="drawing-tools">
    <h4>绘制工具</h4>
    <div class="tool-buttons">
      <el-button @click="drawPolygon">绘制多边形</el-button>
      <el-button @click="drawRect">绘制矩形</el-button>
      <el-button @click="drawCircle">绘制圆形</el-button>
      <el-button @click="clearAll" type="danger">清除</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

interface MapContext {
  map: any
  AMap: any
}

const mapContext = inject<{ value: MapContext }>('mapContext')
const mouseTool = ref<any>(null)

// 初始化绘制工具
const initDrawingTools = () => {
  if (!mapContext?.value) return

  const { map, AMap } = mapContext.value
  mouseTool.value = new AMap.MouseTool(map)
}

// 绘制多边形
const drawPolygon = () => {
  if (!mouseTool.value) initDrawingTools()

  mouseTool.value.polygon({
    strokeColor: "#FF33FF",
    strokeOpacity: 1,
    strokeWeight: 4,
    fillColor: '#1791fc',
    fillOpacity: 0.2,
    strokeStyle: "solid",
  })
  ElMessage.info('开始绘制多边形')
}

// 绘制矩形
const drawRect = () => {
  if (!mouseTool.value) initDrawingTools()

  mouseTool.value.rectangle({
    strokeColor: 'red',
    strokeOpacity: 0.5,
    strokeWeight: 2,
    fillColor: 'blue',
    fillOpacity: 0.2,
    strokeStyle: 'solid',
  })
  ElMessage.info('开始绘制矩形')
}

// 绘制圆形
const drawCircle = () => {
  if (!mouseTool.value) initDrawingTools()

  mouseTool.value.circle({
    strokeColor: "#FF33FF",
    strokeOpacity: 1,
    strokeWeight: 6,
    fillColor: '#1791fc',
    fillOpacity: 0.4,
    strokeStyle: 'dashed',
    strokeDasharray: [30, 10],
  })
  ElMessage.info('开始绘制圆形')
}

// 清除所有绘制
const clearAll = () => {
  if (mouseTool.value) {
    mouseTool.value.close(true)
    ElMessage.success('已清除所有绘制')
  }
}

onUnmounted(() => {
  if (mouseTool.value) {
    mouseTool.value.close(true)
  }
})

defineExpose({
  drawPolygon,
  drawRect,
  drawCircle,
  clearAll
})
</script>

<style scoped>
.drawing-tools {
  position: absolute;
  bottom: 20px;
  right: 20px;
  z-index: 999;
  background: white;
  padding: 15px;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.1);
  min-width: 200px;
}

.tool-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-buttons .el-button {
  width: 100%;
}
</style>