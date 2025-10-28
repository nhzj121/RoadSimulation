<!-- POIManager.vue -->
<template>
  <div class="poi-manager">
    <el-container class="page-container" direction="vertical">
      <!-- 导航栏，保持与主页面一致 -->
      <el-header class="header-navbar">
        <div class="navbar-content left-aligned">
          <h2 class="navbar-title" @click="goBack">物流运输仿真系统</h2>
          <div class="navbar-menu">
            <ElButton text>POI管理</ElButton>
            <ElButton text>帮助文档</ElButton>
          </div>
        </div>
      </el-header>

      <el-container>
        <el-aside width="300px" class="side-panel">
          <h2 class="side-panel-h2">POI管理</h2>
          <!-- POI管理侧边栏内容 -->
          <el-card shadow="never" class="box-card">
            <template #header>
              <div class="card-header">
                <span>POI类型</span>
              </div>
            </template>
            <div class="poi-type-list">
              <el-checkbox-group v-model="selectedTypes">
                <div v-for="type in poiTypes" :key="type.id" class="poi-type-item">
                  <el-checkbox :label="type.id">{{ type.name }}</el-checkbox>
                  <span class="poi-count">({{ type.count }})</span>
                </div>
              </el-checkbox-group>
            </div>
          </el-card>

          <el-card shadow="never" class="box-card">
            <template #header>
              <div class="card-header">
                <span>操作</span>
              </div>
            </template>
            <div class="action-buttons">
              <!--ToDo 所需功能的控制处-->
            </div>
          </el-card>
        </el-aside>

        <el-main>
          <!-- POI点展示的地图界面 -->

        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

// 返回主页面
const goBack = () => {
  router.push('/')
}

// POI数据
const poiTypes = ref([
  { id: 'factory', name: '工厂', count: 12 },
  { id: 'parking', name: '停车场', count: 8 },
  { id: 'gas', name: '加油站', count: 5 },
  { id: 'service', name: '保养站', count: 3 }
])

const selectedTypes = ref<string[]>(['factory', 'parking', 'gas', 'service'])

onMounted(() => {
  console.log('POI管理页面加载完成')
})
</script>

<style scoped>
.poi-manager {
  height: 100vh;
  width: 100vw;
}

.page-container {
  height: 100%;
}

/* 复用MapContainer的导航栏样式 */
.header-navbar {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 60px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.navbar-content {
  display: flex;
  align-items: center;
  width: 100%;
}

.navbar-content.left-aligned {
  justify-content: flex-start;
  gap: 40px;
}

.navbar-title {
  margin: 0;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
  white-space: nowrap;
}

.navbar-menu {
  display: flex;
  gap: 10px;
}

/* 侧边栏样式 */
.side-panel {
  background-color: #f7f8fa;
  padding: 10px;
  border-right: 1px solid #e6e6e6;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.side-panel-h2{
  color: black;
}

.box-card {
  border: none;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.poi-type-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.poi-type-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.poi-count {
  font-size: 12px;
  color: #909399;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

:deep(.el-card__header) {
  padding: 10px 15px;
  border-bottom: none;
}

:deep(.el-card__body) {
  padding: 15px;
}
</style>