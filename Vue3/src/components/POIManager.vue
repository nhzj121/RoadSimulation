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
          <!-- 类型映射提示 -->
          <div v-if="showTypeMappingWarning" class="type-mapping-warning">
            <el-alert
                title="类型映射提示"
                type="warning"
                description="检测到前后端类型不一致，正在自动映射..."
                show-icon
                :closable="false"
            />
          </div>

          <!-- 搜索进度 -->
          <div v-if="searchProgress.total > 0" class="search-progress">
            <p><strong>当前搜索:</strong> {{ searchProgress.currentCategory }} - {{ searchProgress.currentKeyword }}</p>
            <p><strong>进度:</strong> {{ searchProgress.completed }} / {{ searchProgress.total }}</p>
            <el-progress
                :percentage="Math.round((searchProgress.completed / searchProgress.total) * 100)"
                :show-text="true"
            />
          </div>

          <!-- 数据加载状态 -->
          <div v-if="loadingData" class="loading-data">
            <el-alert title="正在从数据库加载数据..." type="info" :closable="false" />
            <el-progress :percentage="loadProgress" :show-text="true" />
          </div>

          <!-- 分类统计和控制 -->
          <div class="category-controls">
            <h5>分类控制 (共 {{ totalPOICount }} 个地点)</h5>
            <div class="category-list">
              <div
                  v-for="category in poiCategories"
                  :key="category.name"
                  class="category-item"
              >
                <el-checkbox
                    v-model="category.visible"
                    @change="onCategoryVisibilityChange(category)"
                >
                  {{ category.label }} ({{ getCategoryCount(category.name) }})
                </el-checkbox>
                <el-tag size="small" :type="getCategoryCount(category.name) > 0 ? 'success' : 'info'">
                  {{ getCategoryCount(category.name) }}
                </el-tag>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="action-buttons">
            <el-button
                @click="smartBatchPOISearch"
                type="primary"
                :loading="isSearching"
                :disabled="!mapContext"
            >
              {{ isSearching ? '搜索中...' : '开始POI搜索' }}
            </el-button>

            <!-- 添加加载数据按钮 -->
            <el-button
                @click="loadDataFromBackend"
                type="warning"
                :loading="loadingData"
                :disabled="!mapContext"
            >
              {{ loadingData ? '加载中...' : '从数据库加载' }}
            </el-button>

            <el-button @click="exportPOIData" :disabled="totalPOICount === 0">
              导出数据
            </el-button>
            <el-button @click="saveToBackend" type="success" :disabled="totalPOICount === 0">
              保存到数据库
            </el-button>
          </div>

          <!-- 数据信息统计 -->
          <div v-if="dataStats.total > 0" class="data-stats">
            <h5>数据统计</h5>
            <div class="stats-grid">
              <div class="stat-item">
                <span class="stat-label">总数:</span>
                <span class="stat-value">{{ dataStats.total }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">加载时间:</span>
                <span class="stat-value">{{ dataStats.loadTime }}ms</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">来源:</span>
                <span class="stat-value">{{ dataStats.source }}</span>
              </div>
            </div>
          </div>

          <!-- 快速分类操作 -->
          <div class="quick-actions">
            <el-button size="small" @click="showAllCategories">显示所有</el-button>
            <el-button size="small" @click="hideAllCategories">隐藏所有</el-button>
            <el-button size="small" @click="clearAllData">清空数据</el-button>
          </div>
        </el-aside>

        <el-main>
          <!-- POI点展示的地图界面 -->

        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import {ref, inject, onMounted, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {poiManagerApi, POIFromDB} from "../api/poiManagerApi";
import factoryIcon from '@/assets/icons/factory.png';
import warehouseIcon from '@/assets/icons/warehouse.png';
import gasStationIcon from '@/assets/icons/gas-station.png';
import maintenanceIcon from '@/assets/icons/maintenance-center.png';
import restAreaIcon from '@/assets/icons/rest-area.png';
import transportIcon from '@/assets/icons/distribution-center.png';
import { useRouter } from 'vue-router'

/*
  VueRouter的相关配置
 */
const router = useRouter()
// 返回主页面
const goBack = () => {
  router.push('/')
}

/*
  POI点的相关数据分类统计
 */
// 总共加载的POI点的数量
const totalPOICount = ref(0);

// 统一的POI接口定义
interface POI {
  id: string;
  name: string;
  type: string;
  location: { lng: number; lat: number };
  address: string;
  tel: string;
  category: string; // 确保包含category属性
}

interface POICategory {
  name: string;
  label: string;
  types: string[];
  keywords: string[];
  visible: boolean;
}

// POI数据状态
const poiData = ref<Record<string, POI[]>>({
  factory: [],
  warehouse: [],
  gasStation: [],
  maintenance: [],
  restArea: [],
  transport: []
})
// 详细的POI分类配置
const detailedPoiCategories = ref<POICategory[]>([
  {
    name: 'factory',
    label: '工厂',
    types: ['170300'],
    keywords: ['工厂'],//, '工业园', '加工厂'
    visible: true
  },
  {
    name: 'warehouse',
    label: '仓库',
    types: ['070501'],
    keywords: ['仓库'],//, '物流园', '仓储'
    visible: true
  },
  {
    name: 'gasStation',
    label: '加油站',
    types: ['010100'],
    keywords: ['加油站'],//, '中国石油', '中国石化'
    visible: true
  },
  {
    name: 'maintenance',
    label: '维修中心',
    types: ['035000'],
    keywords: ['货车维修'],
    visible: true
  },
  {
    name: 'restArea',
    label: '休息区',
    types: ['180300'],
    keywords: ['休息区'],//'服务区',
    visible: true
  },
  {
    name: 'transport',
    label: '运输中心',
    types: ['070500', '150107', '150210'],
    keywords: ['配送中心'],//, '物流'
    visible: true
  }
]);
const poiCategories = detailedPoiCategories;

// 搜索过程的表示变量
const searchProgress = ref({
  total: 0,
  completed: 0,
  currentCategory: '',
  currentKeyword: ''
})
// 是否处于搜索状态的判断变量
const isSearching = ref(false)

/*
关于POI点搜索展示的定义
 */
// 成都平原搜索区域
const chengduPlainPolygon = [
  [103.566708, 31.019274], [103.7000, 31.1000],
  [104.8000, 31.1000], [104.8000, 30.3000],
  [103.848084, 30.076928], [103.463537, 30.174276]
];
// 图标配置映射 - 修复color属性问题
const poiIcons = {
  '工厂': {
    url: factoryIcon,
    size: [22, 22],
    anchor: 'bottom-center',
    color: '#FF6B6B'
  },
  '仓库': {
    url: warehouseIcon,
    size: [22, 22],
    anchor: 'bottom-center',
    color: '#4ECDC4'
  },
  '加油站': {
    url: gasStationIcon,
    size: [22, 22],
    anchor: 'bottom-center',
    color: '#FFD166'
  },
  '维修中心': {
    url: maintenanceIcon,
    size: [22, 22],
    anchor: 'bottom-center',
    color: '#06D6A0'
  },
  '休息区': {
    url: restAreaIcon,
    size: [22, 22],
    anchor: 'bottom-center',
    color: '#8f11b2'
  },
  '运输中心': {
    url: transportIcon,
    size: [22, 22],
    anchor: 'bottom-center',
    color: '#073B4C'
  }
};

// 添加数据加载相关状态
const loadingData = ref(false)
const loadProgress = ref(0)
const dataStats = ref({
  total: 0,
  loadTime: 0,
  source: ''
})

// 类型映射警告
const showTypeMappingWarning = ref(false)
// 前后端类型映射
const typeMapping = {
  // 前端分类 -> 后端枚举
  'factory': 'FACTORY',
  'warehouse': 'WAREHOUSE',
  'gasStation': 'GAS_STATION',
  'maintenance': 'MAINTENANCE_CENTER',
  'restArea': 'REST_AREA',
  'transport': 'DISTRIBUTION_CENTER'
} as const;
// 反向映射：后端枚举 -> 前端分类
const reverseTypeMapping = {
  'FACTORY': 'factory',
  'WAREHOUSE': 'warehouse',
  'GAS_STATION': 'gasStation',
  'MAINTENANCE_CENTER': 'maintenance',
  'REST_AREA': 'restArea',
  'DISTRIBUTION_CENTER': 'transport'
} as const;

// 从后端加载数据
const loadDataFromBackend = async (): Promise<void> => {
  // 如果地图还没有初始化，进行报错提示
  if (!mapContext?.value) {
    ElMessage.warning('地图未初始化')
    return
  }
  // 更新当前状态
  loadingData.value = true
  loadProgress.value = 0
  const startTime = Date.now()
  showTypeMappingWarning.value = false
  // 开始进行加载操作
  try {
    ElMessage.info('开始从数据库加载POI数据...')
    // 模拟进度更新
    const progressInterval = setInterval(() => {
      if (loadProgress.value < 90) {
        loadProgress.value += 10
      }
    }, 200)
    // 调用API获取数据
    const poisFromDB = await poiManagerApi.getAll()

    clearInterval(progressInterval)
    loadProgress.value = 100

    if (poisFromDB && poisFromDB.length > 0) {
      // 转换数据结构以匹配前端格式
      const convertedPOIs = convertDBDataToFrontend(poisFromDB)
      // 分类存储数据
      classifyPOIData(convertedPOIs)
      // 更新地图显示
      updateMapDisplay()
      // 更新统计信息
      const endTime = Date.now()
      dataStats.value = {
        total: convertedPOIs.length,
        loadTime: endTime - startTime,
        source: '数据库'
      }
      // 显示加载结果
      ElMessage.success(`成功加载 ${convertedPOIs.length} 个POI数据`)
      // 检查是否有类型映射问题
      const hasMappingIssues = checkTypeMapping(poisFromDB)
      if (hasMappingIssues) {
        showTypeMappingWarning.value = true
      }
    } else {
      ElMessage.info('数据库中没有POI数据')
    }
  } catch (error) {
    console.error('加载POI数据失败:', error)
    ElMessage.error('加载POI数据失败，请检查网络连接')
  } finally {
    loadingData.value = false
    loadProgress.value = 0
  }
}
// 检查类型映射问题
const checkTypeMapping = (dbData: POIFromDB[]): boolean => {
  const unmappedTypes = new Set<string>()
  dbData.forEach(poi => {
    if (!reverseTypeMapping[poi.type as keyof typeof reverseTypeMapping]) {
      unmappedTypes.add(poi.type)
    }
  })
  if (unmappedTypes.size > 0) {
    console.warn('发现未映射的POI类型:', Array.from(unmappedTypes))
    return true
  }
  return false
}
// 转换数据库数据为前端格式
const convertDBDataToFrontend = (dbData: POIFromDB[]): POI[] => {
  console.group('🔄 数据转换过程')
  console.log(`开始转换 ${dbData.length} 条数据库记录`)

  const convertedPOIs = dbData.map((item, index) => {
    // 标准化类型处理
    let frontendCategory = 'unknown'
    const normalizedType = item.type.toUpperCase().trim()

    console.log(`[${index}] 转换: "${item.name}" - 后端类型: "${item.type}"`)

    // 尝试直接映射
    if (reverseTypeMapping[normalizedType as keyof typeof reverseTypeMapping]) {
      frontendCategory = reverseTypeMapping[normalizedType as keyof typeof reverseTypeMapping]
      console.log(`  ✅ 类型映射: ${normalizedType} -> ${frontendCategory}`)
    }
    // 处理可能的变体
    else if (normalizedType === 'GASSTATION') {
      frontendCategory = 'gasStation'
      console.log(`  🔄 变体映射: ${normalizedType} -> ${frontendCategory}`)
    } else if (normalizedType === 'RESTAREA') {
      frontendCategory = 'restArea'
      console.log(`  🔄 变体映射: ${normalizedType} -> ${frontendCategory}`)
    }
    // 如果还是未知类型
    else {
      console.warn(`  ⚠️ 未知POI类型: "${item.type}"，映射到unknown分类`)
    }

    const converted = {
      id: item.id.toString(),
      name: item.name,
      type: item.type,
      location: {
        lng: item.longitude,
        lat: item.latitude
      },
      address: item.address,
      tel: item.tel || '',
      category: frontendCategory
    }

    console.log(`  转换结果: category = "${frontendCategory}"`)
    return converted
  })

  console.log(`转换完成: ${convertedPOIs.length} 条记录`)
  console.groupEnd()

  return convertedPOIs
}

// 转换前端数据为数据库格式
const convertFrontendDataToDB = (frontendData: POI[]): POIFromDB[] => {
  return frontendData.map(poi => {
    // 映射类型到后端枚举
    const backendType = typeMapping[poi.category as keyof typeof typeMapping] || 'UNKNOWN'

    return {
      id: poi.id,
      name: poi.name,
      type: backendType,
      longitude: poi.location.lng,
      latitude: poi.location.lat,
      address: poi.address,
      tel: poi.tel
    }
  })
}
// 增强保存到后端功能
const saveToBackend = async (): Promise<void> => {
  try {
    const allPOIs = Object.values(poiData.value).flat()
    if (allPOIs.length === 0) {
      ElMessage.warning('没有数据可保存')
      return
    }
    // 转换数据结构以匹配后端格式
    const poisToSave = convertFrontendDataToDB(allPOIs)
    // 确认保存操作
    try {
      await ElMessageBox.confirm(
          `确定要保存 ${allPOIs.length} 个POI数据到数据库吗？`,
          '确认保存',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
      )
    } catch {
      ElMessage.info('已取消保存操作')
      return
    }

    ElMessage.info('正在保存数据...')

    const result = await poiManagerApi.batchSave(poisToSave)

    if (result.success) {
      ElMessage.success(result.message || 'POI数据保存成功')
    } else {
      ElMessage.error(`保存失败: ${result.message}`)
    }
  } catch (error) {
    console.error('保存POI数据时发生错误:', error)
    ElMessage.error('保存POI数据时发生错误: ' + (error as Error).message)
  }
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