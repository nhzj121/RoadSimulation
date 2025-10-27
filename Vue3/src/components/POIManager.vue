<!-- POIManager.vue -->
<template>
  <div class="poi-manager">
    <el-container class="page-container" direction="vertical">
      <!-- 导航栏，保持与主页面一致 -->
      <el-header class="header-navbar">
        <div class="navbar-content left-aligned">
          <h2 class="navbar-title">POI点管理</h2>
          <div class="navbar-menu">
            <ElButton text @click="goBack">返回主页面</ElButton>
            <ElButton text>帮助文档</ElButton>
          </div>
        </div>
      </el-header>

      <el-container>
        <el-aside width="300px" class="side-panel">
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
              <el-button type="primary" @click="addPOI">添加POI</el-button>
              <el-button @click="batchDelete">批量删除</el-button>
            </div>
          </el-card>
        </el-aside>

        <el-main>
          <!-- POI列表表格 -->
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>POI列表</span>
                <el-input v-model="searchKeyword" placeholder="搜索POI..." style="width: 200px;" clearable>
                  <template #append>
                    <el-button icon="search" />
                  </template>
                </el-input>
              </div>
            </template>

            <el-table :data="filteredPOIList" style="width: 100%">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="name" label="名称" />
              <el-table-column prop="type" label="类型">
                <template #default="scope">
                  <el-tag :type="getTypeTag(scope.row.type)">{{ getTypeName(scope.row.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="address" label="地址" />
              <el-table-column prop="createdAt" label="创建时间" />
              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button size="small" @click="editPOI(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="deletePOI(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
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

const poiList = ref([
  { id: 1, name: '成都工厂A', type: 'factory', address: '成都市高新区', createdAt: '2024-01-15' },
  { id: 2, name: '城东停车场', type: 'parking', address: '成都市成华区', createdAt: '2024-01-10' },
  { id: 3, name: '中石油加油站', type: 'gas', address: '成都市金牛区', createdAt: '2024-01-08' },
  { id: 4, name: '快速保养中心', type: 'service', address: '成都市武侯区', createdAt: '2024-01-05' }
])

const searchKeyword = ref('')

// 计算属性：筛选后的POI列表
const filteredPOIList = computed(() => {
  return poiList.value.filter(poi => {
    const matchesType = selectedTypes.value.includes(poi.type)
    const matchesKeyword = !searchKeyword.value ||
        poi.name.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
        poi.address.toLowerCase().includes(searchKeyword.value.toLowerCase())
    return matchesType && matchesKeyword
  })
})

// 方法
const getTypeTag = (type: string) => {
  const tagMap: { [key: string]: string } = {
    factory: 'primary',
    parking: 'success',
    gas: 'warning',
    service: 'info'
  }
  return tagMap[type] || 'default'
}

const getTypeName = (type: string) => {
  const typeObj = poiTypes.value.find(t => t.id === type)
  return typeObj ? typeObj.name : type
}

const addPOI = () => {
  ElMessage.info('打开添加POI对话框')
  // 这里可以打开对话框或跳转到添加页面
}

const editPOI = (poi: any) => {
  ElMessage.info(`编辑POI: ${poi.name}`)
}

const deletePOI = (poi: any) => {
  ElMessageBox.confirm(`确定要删除POI "${poi.name}" 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    ElMessage.success('删除成功')
  })
}

const batchDelete = () => {
  ElMessage.info('批量删除功能')
}

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