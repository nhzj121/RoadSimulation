<!-- components/POIManager.vue -->
<template>
  <div class="poi-manager">
    <h4>POI管理</h4>

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

    <!-- 显示控制 -->
    <div class="display-controls">
      <div class="cluster-controls">
        <el-switch
            v-model="clusterEnabled"
            @change="toggleCluster"
            active-text="点聚合"
            inactive-text="单独显示"
        />
        <el-button @click="refreshDisplay" size="small" style="margin-left: 10px;">
          刷新显示
        </el-button>
      </div>

      <!-- 点聚合高级设置（可选） -->
      <div v-if="clusterEnabled" class="cluster-advanced">
        <el-collapse>
          <el-collapse-item title="聚合设置">
            <div class="cluster-settings">
              <div class="setting-item">
                <span>聚合网格大小:</span>
                <el-slider
                    v-model="clusterConfig.gridSize"
                    :min="40"
                    :max="120"
                    :step="10"
                    @change="refreshCluster"
                />
                <span>{{ clusterConfig.gridSize }}px</span>
              </div>
              <div class="setting-item">
                <span>最小聚合数量:</span>
                <el-slider
                    v-model="clusterConfig.minClusterSize"
                    :min="2"
                    :max="10"
                    @change="refreshCluster"
                />
                <span>{{ clusterConfig.minClusterSize }}</span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <!-- 快速分类操作 -->
    <div class="quick-actions">
      <el-button size="small" @click="showAllCategories">显示所有</el-button>
      <el-button size="small" @click="hideAllCategories">隐藏所有</el-button>
      <el-button size="small" @click="clearAllData">清空数据</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, inject, onMounted, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {poiApi, POIFromDB} from "../api/poiApi";
import factoryIcon from '@/assets/icons/factory.png';
import warehouseIcon from '@/assets/icons/warehouse.png';
import gasStationIcon from '@/assets/icons/gas-station.png';
import maintenanceIcon from '@/assets/icons/maintenance-center.png';
import restAreaIcon from '@/assets/icons/rest-area.png';
import transportIcon from '@/assets/icons/distribution-center.png';
import MaxMerge from '@/assets/merge/max.png';
import MiddleMerge from '@/assets/merge/middle.png';
import MinMerge from '@/assets/merge/min.png';

interface MapContext {
  map: any
  AMap: any
}

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

// 修复：使用正确的类型定义
const mapContext = inject<{ value: MapContext }>('mapContext')

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

// 替换原有的简单分类
const poiCategories = detailedPoiCategories;

const searchProgress = ref({
  total: 0,
  completed: 0,
  currentCategory: '',
  currentKeyword: ''
})

// 修复：移除在初始化时直接使用 AMap 的代码
const clusterConfig = ref({
  gridSize: 150,           // 聚合计算时网格的像素大小
  maxZoom: 20,            // 最大聚合级别，大于此级别不聚合
  minClusterSize: 2,      // 最小聚合数量
  styles: [               // 聚合簇样式配置 - 移除直接的 AMap 引用
    {
      url: MinMerge,
      size: { width: 40, height: 40 }, // 使用普通对象而不是 AMap.Size
      color: '#fa0303',
      background: '#ff6b6b',
      borderColor: '#fff',
      borderRadius: 20,
      borderWidth: 2,
      fontSize: 12
    },
    {
      url: MiddleMerge,
      size: { width: 50, height: 50 },
      color: '#fa5656',
      background: '#4ecdc4',
      borderColor: '#fff',
      borderRadius: 25,
      borderWidth: 3,
      fontSize: 13
    },
    {
      url: MaxMerge,
      size: { width: 60, height: 60 },
      color: '#f1c0c0',
      background: '#45b7d1',
      borderColor: '#fff',
      borderRadius: 30,
      borderWidth: 4,
      fontSize: 14
    }
  ]
})

// 点聚合实例
let markerClusterer: any = null

const isSearching = ref(false)
const clusterEnabled = ref(false)

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
  if (!mapContext?.value) {
    ElMessage.warning('地图未初始化')
    return
  }

  loadingData.value = true
  loadProgress.value = 0
  const startTime = Date.now()
  showTypeMappingWarning.value = false

  try {
    ElMessage.info('开始从数据库加载POI数据...')

    // 模拟进度更新
    const progressInterval = setInterval(() => {
      if (loadProgress.value < 90) {
        loadProgress.value += 10
      }
    }, 200)

    // 调用API获取数据
    const poisFromDB = await poiApi.getAll()

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

    const result = await poiApi.batchSave(poisToSave)

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

// 按类型加载数据
const loadDataByType = async (type: string): Promise<void> => {
  try {
    loadingData.value = true

    // 映射前端分类到后端类型
    const backendType = typeMapping[type as keyof typeof typeMapping]
    if (!backendType) {
      ElMessage.warning(`未找到类型 ${type} 的后端映射`)
      return
    }

    const poisFromDB = await poiApi.getByType(backendType)

    if (poisFromDB && poisFromDB.length > 0) {
      const convertedPOIs = convertDBDataToFrontend(poisFromDB)

      // 更新特定分类的数据
      poiData.value[type as keyof typeof poiData.value] = convertedPOIs
      updateMapDisplay()

      ElMessage.success(`加载了 ${convertedPOIs.length} 个${getCategoryLabel(type)}数据`)
    } else {
      ElMessage.info(`数据库中没有${getCategoryLabel(type)}数据`)
    }

  } catch (error) {
    console.error(`加载${type}数据失败:`, error)
    ElMessage.error(`加载${getCategoryLabel(type)}数据失败`)
  } finally {
    loadingData.value = false
  }
}

// 获取分类标签
const getCategoryLabel = (categoryName: string): string => {
  const category = poiCategories.value.find(cat => cat.name === categoryName)
  return category?.label || categoryName
}

// 数据管理功能
const manageData = async (): Promise<void> => {
  try {
    const { value: action } = await ElMessageBox.prompt(
        '请输入操作:\n1. 清空数据库\n2. 导出所有数据\n3. 统计信息',
        '数据管理',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPattern: /^[123]$/,
          inputErrorMessage: '请输入1、2或3'
        }
    )

    switch (action) {
      case '1':
        await clearDatabase()
        break
      case '2':
        await exportAllData()
        break
      case '3':
        await showStatistics()
        break
    }
  } catch (error) {
    // 用户取消输入
  }
}

// 清空数据库
const clearDatabase = async (): Promise<void> => {
  try {
    await ElMessageBox.confirm(
        '确定要清空数据库中的所有POI数据吗？此操作不可恢复！',
        '确认清空',
        {
          confirmButtonText: '确定清空',
          cancelButtonText: '取消',
          type: 'error',
          confirmButtonClass: 'el-button--danger'
        }
    )

    // 这里需要调用后端的清空接口
    // 注意：您的POIController中没有提供清空所有数据的接口
    // 您可能需要在后端添加这个功能
    ElMessage.warning('清空数据库功能需要后端支持，请联系开发人员')

  } catch {
    ElMessage.info('已取消清空操作')
  }
}

// 导出所有数据
const exportAllData = async (): Promise<void> => {
  try {
    const allData = await poiApi.getAll()
    const dataStr = JSON.stringify(allData, null, 2)
    const dataBlob = new Blob([dataStr], { type: 'application/json' })

    const link = document.createElement('a')
    link.href = URL.createObjectURL(dataBlob)
    link.download = `poi_database_export_${new Date().getTime()}.json`
    link.click()

    ElMessage.success('数据库数据导出成功')
  } catch (error) {
    console.error('导出数据库数据失败:', error)
    ElMessage.error('导出数据库数据失败')
  }
}

// 显示统计信息
const showStatistics = async (): Promise<void> => {
  try {
    const allData = await poiApi.getAll()

    const typeCount: Record<string, number> = {}
    allData.forEach(poi => {
      typeCount[poi.type] = (typeCount[poi.type] || 0) + 1
    })

    const statsText = Object.entries(typeCount)
        .map(([type, count]) => `${type}: ${count}个`)
        .join('\n')

    ElMessageBox.alert(
        `数据库统计信息:\n\n总记录数: ${allData.length}\n\n类型分布:\n${statsText}`,
        '统计信息',
        {
          confirmButtonText: '确定'
        }
    )
  } catch (error) {
    console.error('获取统计信息失败:', error)
    ElMessage.error('获取统计信息失败')
  }
}

// 分页搜索函数
const searchByKeywordWithPagination = async (keyword: string, categoryName: string): Promise<POI[]> => {
  return new Promise(async (resolve) => {
    if (!mapContext?.value) {
      console.error('地图上下文未就绪');
      resolve([]);
      return;
    }

    const { AMap } = mapContext.value;
    let pageIndex = 1;
    const pageSize = 50;
    let hasMoreData = true;
    let allResults: POI[] = [];

    while (hasMoreData && pageIndex <= 5) { // 限制最多5页
      try {
        const pois = await searchSinglePage(keyword, categoryName, pageIndex, pageSize);

        if (pois.length > 0) {
          allResults.push(...pois);
          console.log(`搜索 "${keyword}" 第${pageIndex}页找到 ${pois.length} 个${categoryName}`);

          if (pois.length < pageSize) {
            hasMoreData = false;
          } else {
            pageIndex++;
            // 添加延迟避免请求过快
            await new Promise(resolve => setTimeout(resolve, 500));
          }
        } else {
          hasMoreData = false;
        }
      } catch (error) {
        console.error(`搜索 "${keyword}" 第${pageIndex}页失败:`, error);
        hasMoreData = false;
      }
    }

    console.log(`搜索 "${keyword}" 完成，共找到 ${allResults.length} 个${categoryName}`);
    resolve(allResults);
  });
};

// 单页搜索实现
const searchSinglePage = (keyword: string, categoryName: string, pageIndex: number, pageSize: number): Promise<POI[]> => {
  return new Promise((resolve) => {
    if (!mapContext?.value) {
      resolve([]);
      return;
    }

    const { AMap } = mapContext.value;

    const placeSearch = new AMap.PlaceSearch({
      city: '成都市',
      citylimit: true,
      pageSize: pageSize,
      pageIndex: pageIndex,
      extensions: 'all'
    });

    placeSearch.searchInBounds(keyword, chengduPlainPolygon, function(status: string, result: any) {
      if (status === 'complete' && result.poiList && result.poiList.pois) {
        const categoryConfig = poiCategories.value.find(cat => cat.label === categoryName);
        const categoryKey = categoryConfig ? categoryConfig.name : categoryName;
        const pois: POI[] = result.poiList.pois.map((poi: any) => ({
          id: poi.id,
          name: poi.name,
          type: categoryName,
          location: poi.location,
          address: poi.address,
          tel: poi.tel || '',
          category: categoryKey // 确保包含category
        }));
        resolve(pois);
      } else {
        console.warn(`搜索 "${keyword}" 第${pageIndex}页状态: ${status}`);
        resolve([]);
      }
    });
  });
};

// 智能批量搜索POI
const smartBatchPOISearch = async (): Promise<void> => {
  if (!mapContext?.value) {
    ElMessage.warning('地图未初始化');
    return;
  }

  isSearching.value = true;
  const allPOIs: POI[] = [];

  // 计算总任务数用于进度显示
  const totalTasks = poiCategories.value.reduce((sum, category) => sum + category.keywords.length, 0);
  let completedTasks = 0;

  searchProgress.value = {
    total: totalTasks,
    completed: completedTasks,
    currentCategory: '',
    currentKeyword: ''
  };

  try {
    // 按优先级排序搜索
    const prioritizedCategories = [...poiCategories.value].sort((a, b) => {
      if (a.name === 'gasStation' || a.name === 'restArea') return -1;
      if (b.name === 'gasStation' || b.name === 'restArea') return 1;
      return 0;
    });

    for (const category of prioritizedCategories) {
      if (!category.visible) continue;

      console.log(`开始搜索分类: ${category.label}`);

      for (const keyword of category.keywords) {
        searchProgress.value.currentCategory = category.label;
        searchProgress.value.currentKeyword = keyword;

        const results = await searchByKeywordWithPagination(keyword, category.name);
        allPOIs.push(...results);

        completedTasks++;
        searchProgress.value.completed = completedTasks;

        // 控制并发，避免请求过快
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }

    // 处理搜索结果
    const uniquePOIs = removeDuplicatePOIs(allPOIs);
    classifyPOIData(uniquePOIs);

    ElMessage.success(`POI搜索完成，共找到 ${uniquePOIs.length} 个地点`);
    updateMapDisplay();

  } catch (error) {
    console.error('POI搜索失败:', error);
    ElMessage.error('POI搜索失败');
  } finally {
    isSearching.value = false;
    searchProgress.value.currentCategory = '';
    searchProgress.value.currentKeyword = '';
  }
};

// 去重函数
const removeDuplicatePOIs = (pois: POI[]): POI[] => {
  const seen = new Map();
  return pois.filter(poi => {
    const key = `${poi.name}-${poi.location.lng.toFixed(6)}-${poi.location.lat.toFixed(6)}`;
    if (seen.has(key)) {
      return false;
    }
    seen.set(key, true);
    return true;
  });
};

// 分类存储POI数据
const classifyPOIData = (pois: POI[]): void => {
  console.group('🔍 POI数据分类过程')

  // 先清空现有数据
  Object.keys(poiData.value).forEach(key => {
    const oldCount = poiData.value[key as keyof typeof poiData.value].length
    if (oldCount > 0) {
      console.log(`清空分类 ${key}: 原有 ${oldCount} 条数据`)
    }
    poiData.value[key as keyof typeof poiData.value] = []
  })

  console.log(`开始分类 ${pois.length} 个POI数据`)

  // 按分类存储
  let classifiedCount = 0
  let unclassifiedCount = 0

  pois.forEach(poi => {
    const categoryKey = poi.category // 这里应该是前端的分类名，如 'factory', 'warehouse' 等

    if (categoryKey && poiData.value[categoryKey as keyof typeof poiData.value] !== undefined) {
      poiData.value[categoryKey as keyof typeof poiData.value].push(poi)
      classifiedCount++
      console.log(`✅ 分类成功: "${poi.name}" -> ${categoryKey}`)
    } else {
      unclassifiedCount++
      console.warn(`❌ 分类失败: "${poi.name}" - 分类键: "${categoryKey}"`)
      console.log('  可用分类键:', Object.keys(poiData.value))
    }
  })

  // 输出详细的统计信息
  console.log('📊 POI分类统计结果:')
  poiCategories.value.forEach(category => {
    const count = poiData.value[category.name as keyof typeof poiData.value].length
    console.log(`  ${category.label} (${category.name}): ${count} 个`)
  })

  console.log(`总计: 已分类 ${classifiedCount} 个, 未分类 ${unclassifiedCount} 个`)
  console.groupEnd()
}

// 更新地图显示
const updateMapDisplay = (): void => {
  console.log('开始更新地图显示...');

  // 清除现有标记
  clearAllMarkers();

  if (clusterEnabled.value) {
    initMarkerCluster();
  } else {
    addIndividualMarkers();
  }
};

// 添加单独标记 - 修复图标使用问题
// 增强添加单独标记函数
const addIndividualMarkers = (): void => {
  if (!mapContext?.value) {
    console.error('❌ 地图上下文未就绪')
    return
  }

  const { map, AMap } = mapContext.value
  let totalMarkers = 0

  console.group('📍 创建单独标记')

  poiCategories.value.forEach(category => {
    if (!category.visible) {
      console.log(`⏭️ 跳过隐藏分类: ${category.label}`)
      return
    }

    const pois = poiData.value[category.name as keyof typeof poiData.value]
    const iconConfig = poiIcons[category.label as keyof typeof poiIcons]

    if (!iconConfig) {
      console.warn(`❌ 未找到分类 ${category.label} 的图标配置`)
      return
    }

    console.log(`📁 处理分类 ${category.label}: ${pois.length} 个POI`)

    pois.forEach((poi, index) => {
      try {
        // 使用图片图标创建标记
        const icon = new AMap.Icon({
          image: iconConfig.url,
          size: new AMap.Size(iconConfig.size[0], iconConfig.size[1]),
          imageSize: new AMap.Size(iconConfig.size[0], iconConfig.size[1]),
          anchor: iconConfig.anchor as any
        })

        // 创建标记
        const marker = new AMap.Marker({
          position: [poi.location.lng, poi.location.lat],
          title: `${poi.name} - ${category.label}`,
          icon: icon,
          offset: new AMap.Pixel(-iconConfig.size[0] / 2, -iconConfig.size[1]),
          extData: poi
        })

        // 添加点击事件
        marker.on('click', () => {
          console.log(`🖱️ 点击标记: ${poi.name}`)
          showPOIInfoWindow(poi, marker.getPosition())
        })

        map.add(marker)
        totalMarkers++

        if (index < 3) { // 只打印前3个标记的详细信息，避免日志过多
          console.log(`  ✅ 创建标记: ${poi.name} (${poi.location.lng}, ${poi.location.lat})`)
        }
      } catch (error) {
        console.error(`❌ 创建标记失败: ${poi.name}`, error)
      }
    })
  })

  console.log(`🎉 标记创建完成: 共 ${totalMarkers} 个标记`)
  ElMessage.success(`已显示 ${totalMarkers} 个地点`)
  console.groupEnd()
}

// 显示POI信息窗口
const showPOIInfoWindow = (poi: POI, position: any): void => {
  if (!mapContext?.value) return;

  const { map, AMap } = mapContext.value;

  const infoContent = `
    <div class="poi-info-window">
      <div class="poi-header">
        <h4>${poi.name}</h4>
        <span class="poi-category">${poi.category}</span>
      </div>
      <div class="poi-details">
        <p><strong>地址:</strong> ${poi.address}</p>
        ${poi.tel ? `<p><strong>电话:</strong> ${poi.tel}</p>` : ''}
        <p><strong>坐标:</strong> ${poi.location.lng.toFixed(6)}, ${poi.location.lat.toFixed(6)}</p>
      </div>
    </div>
  `;

  const infoWindow = new AMap.InfoWindow({
    content: infoContent,
    offset: new AMap.Pixel(0, -30)
  });

  infoWindow.open(map, position);
};

const checkPluginAvailability = (): boolean => {
  if (!mapContext?.value) {
    console.error('地图上下文未就绪')
    return false
  }

  const { AMap } = mapContext.value

  console.group('🔧 插件可用性检查')
  console.log('AMap:', AMap ? '已加载' : '未加载')
  console.log('MarkerClusterer:', AMap?.MarkerClusterer ? '可用' : '不可用')

  if (AMap?.MarkerClusterer) {
    console.log('✅ 所有插件可用')
    console.groupEnd()
    return true
  } else {
    console.error('❌ MarkerClusterer 插件不可用')
    console.log('可用插件:', Object.keys(AMap).filter(key => key.startsWith('AMap.')))
    console.groupEnd()
    ElMessage.error('点聚合插件未正确加载')
    return false
  }
}


// 初始化点聚合
// 增强初始化点聚合函数
const initMarkerCluster = (): void => {
  if (!mapContext?.value) {
    console.error('地图上下文未就绪')
    return
  }

  const { map, AMap } = mapContext.value
  const currentZoom = map.getZoom()
  const center = map.getCenter()
  console.log('🗺️ 地图状态:', { zoom: currentZoom, center })

  // 清除现有聚合
  if (markerClusterer) {
    markerClusterer.clearMarkers()
    markerClusterer.setMap(null)
    markerClusterer = null
  }

  console.group('🔗 初始化点聚合')

  // 首先检查插件可用性
  if (!checkPluginAvailability()) {
    console.groupEnd()
    return
  }
  // 创建标记数组
  const markers: any[] = []
  let totalMarkers = 0

  // 检查标记位置分布
  if (markers.length > 0) {
    const firstMarker = markers[0]
    const lastMarker = markers[markers.length - 1]
    console.log('📍 标记位置范围:', {
      first: firstMarker.getPosition(),
      last: lastMarker.getPosition()
    })
  }

  // 创建聚合器之前验证配置
  console.log('⚙️ 聚合配置:', {
    gridSize: clusterConfig.value.gridSize,
    maxZoom: clusterConfig.value.maxZoom,
    minClusterSize: clusterConfig.value.minClusterSize,
    markersCount: markers.length
  })

  // 收集所有可见分类的标记
  poiCategories.value.forEach(category => {
    if (!category.visible) {
      console.log(`⏭️ 跳过隐藏分类: ${category.label}`)
      return
    }

    const categoryPOIs = poiData.value[category.name] || []
    const iconConfig = poiIcons[category.label as keyof typeof poiIcons]

    if (!iconConfig) {
      console.warn(`❌ 未找到分类 ${category.label} 的图标配置`)
      return
    }

    console.log(`📁 处理分类 ${category.label}: ${categoryPOIs.length} 个POI`)

    categoryPOIs.forEach((poi, index) => {
      try {
        // 创建图标
        const icon = new AMap.Icon({
          image: iconConfig.url,
          size: new AMap.Size(iconConfig.size[0], iconConfig.size[1]),
          imageSize: new AMap.Size(iconConfig.size[0], iconConfig.size[1]),
          anchor: iconConfig.anchor as any
        })

        // 创建标记
        const marker = new AMap.Marker({
          position: [poi.location.lng, poi.location.lat],
          title: `${poi.name} - ${category.label}`,
          icon: icon,
          offset: new AMap.Pixel(-iconConfig.size[0] / 2, -iconConfig.size[1]),
          extData: {
            ...poi,
            category: category.label,
            originalCategory: category.name
          }
        })

        // 添加点击事件
        marker.on('click', () => {
          console.log(`🖱️ 点击标记: ${poi.name}`)
          showPOIInfoWindow(poi, marker.getPosition())
        })

        markers.push(marker)
        totalMarkers++

      } catch (error) {
        console.error(`❌ 创建标记失败: ${poi.name}`, error)
      }
    })
  })

  console.log(`📊 准备聚合 ${totalMarkers} 个标记`)

  if (markers.length === 0) {
    console.warn('⚠️ 没有标记可聚合')
    console.groupEnd()
    return
  }

  try {
    // 创建自定义聚合样式渲染函数
    const renderClusterMarker = (context: any) => {
      const count = context.count
      const factor = Math.pow(context.count / totalMarkers, 1 / 5)
      const size = Math.max(30, Math.min(60, 30 + factor * 30))

      // 根据数量选择样式
      let styleIndex = 0
      if (count > 10) styleIndex = 1
      if (count > 50) styleIndex = 2

      const style = clusterConfig.value.styles[styleIndex]

      // 创建自定义DOM元素
      const div = document.createElement('div')
      div.className = 'custom-cluster-marker'
      div.innerHTML = `
        <div class="cluster-inner">
          <span class="cluster-count">${count}</span>
        </div>
      `

      // 应用样式
      const innerEl = div.querySelector('.cluster-inner') as HTMLElement
      if (innerEl) {
        innerEl.style.cssText = `
          width: ${size}px;
          height: ${size}px;
          background: ${style.background};
          border: ${style.borderWidth}px solid ${style.borderColor};
          border-radius: ${style.borderRadius}px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: ${style.color};
          font-size: ${style.fontSize}px;
          font-weight: bold;
          box-shadow: 0 2px 6px rgba(0,0,0,0.3);
          cursor: pointer;
        `
      }

      context.marker.setContent(div)
      // 修复：使用 AMap.Pixel 来设置偏移
      context.marker.setOffset(new AMap.Pixel(-size / 2, -size / 2))

      // 添加聚合簇点击事件
      context.marker.on('click', (event: any) => {
        console.log(`🖱️ 点击聚合簇: ${count} 个地点`)
        showClusterInfoWindow(context, event.target.getPosition())
      })
    }

    // 创建点聚合实例
    markerClusterer = new AMap.MarkerClusterer(map, markers, {
      gridSize: clusterConfig.value.gridSize,
      maxZoom: clusterConfig.value.maxZoom,
      minClusterSize: clusterConfig.value.minClusterSize,
      //renderClusterMarker: renderClusterMarker,
      // 可选：设置聚合算法
      averageCenter: true
    })

    console.log('✅ 点聚合初始化完成')
    ElMessage.success(`点聚合已启用，聚合了 ${totalMarkers} 个标记`)

    // 立即检查聚合状态
    setTimeout(() => {
      console.log('📊 聚合器状态:', {
        clustersCount: markerClusterer.getClustersCount(),
        markersCount: markerClusterer.getClustersCount().length,
        gridSize: markerClusterer.getGridSize()
      })

    }, 100)

  } catch (error) {
    console.error('❌ 点聚合初始化失败:', error)
    ElMessage.error('点聚合初始化失败，将使用单独标记模式')
    // 降级到单独标记模式
    clusterEnabled.value = false
    addIndividualMarkers()
  }

  console.groupEnd()
}

// 显示聚合簇信息窗口
const showClusterInfoWindow = (context: any, position: any): void => {
  if (!mapContext?.value) return

  const { map, AMap } = mapContext.value
  const markers = context.markers || []
  const count = context.count

  // 统计聚合簇中的分类分布
  const categoryStats: Record<string, number> = {}
  markers.forEach((marker: any) => {
    const category = marker.getExtData()?.category || '未知'
    categoryStats[category] = (categoryStats[category] || 0) + 1
  })

  // 生成分类统计HTML
  const statsHTML = Object.entries(categoryStats)
      .map(([category, count]) => `<div class="cluster-category">${category}: ${count}个</div>`)
      .join('')

  const infoContent = `
    <div class="cluster-info-window">
      <div class="cluster-header">
        <h4>聚合簇信息</h4>
        <span class="cluster-total">共 ${count} 个地点</span>
      </div>
      <div class="cluster-stats">
        <h5>分类分布:</h5>
        ${statsHTML}
      </div>
      <div class="cluster-actions">
        <button class="cluster-zoom-btn" onclick="window.vueComponent.zoomToCluster(${position.lng}, ${position.lat})">
          放大查看
        </button>
      </div>
    </div>
  `

  const infoWindow = new AMap.InfoWindow({
    content: infoContent,
    offset: new AMap.Pixel(0, -20),
    closeWhenClickMap: true
  })

  infoWindow.open(map, position)
}

// 放大到聚合簇
const zoomToCluster = (lng: number, lat: number): void => {
  if (!mapContext?.value) return

  const { map } = mapContext.value
  map.setZoomAndCenter(15, [lng, lat]) // 放大到15级并居中
}

// 切换聚合模式
// 增强切换聚合模式
const toggleCluster = (enabled: boolean): void => {
  clusterEnabled.value = enabled

  if (enabled) {
    console.log('🔗 启用点聚合模式')
    initMarkerCluster()

    // 监控聚合状态
    const checkInterval = setInterval(() => {
      if (markerClusterer) {
        const clusterCount = markerClusterer.getClustersCount()
        console.log(`📊 当前聚合簇数量: ${clusterCount}`)

        if (clusterCount > 0) {
          clearInterval(checkInterval)
          console.log('✅ 聚合成功，检测到聚合簇')
        }
      }
    }, 500)

    // 5秒后停止检查
    setTimeout(() => clearInterval(checkInterval), 5000)
  } else {
    console.log('📍 禁用点聚合模式，显示单独标记')
    // 清除点聚合
    if (markerClusterer) {
      markerClusterer.setMap(null)
      markerClusterer = null
    }
    // 显示单独标记
    addIndividualMarkers()
  }

  ElMessage.info(`已${enabled ? '启用' : '禁用'}点聚合`)
}

// 刷新点聚合
const refreshCluster = (): void => {
  if (clusterEnabled.value) {
    console.log('🔄 刷新点聚合配置')
    initMarkerCluster()
  }
}

// 刷新显示
const refreshDisplay = (): void => {
  updateMapDisplay();
  ElMessage.info('地图显示已刷新');
};

// 获取分类数量
const getCategoryCount = (categoryName: string): number => {
  return poiData.value[categoryName as keyof typeof poiData.value]?.length || 0;
};

// 计算总POI数量
const totalPOICount = computed(() => {
  return Object.values(poiData.value).reduce((sum, pois) => sum + pois.length, 0);
});

// 分类显示切换
const onCategoryVisibilityChange = (category: POICategory): void => {
  console.log(`切换 ${category.label} 可见性:`, category.visible);
  updateMapDisplay();
};

// 快速操作
const showAllCategories = (): void => {
  poiCategories.value.forEach(cat => cat.visible = true);
  updateMapDisplay();
  ElMessage.success('已显示所有分类');
};

const hideAllCategories = (): void => {
  poiCategories.value.forEach(cat => cat.visible = false);
  updateMapDisplay();
  ElMessage.info('已隐藏所有分类');
};

const clearAllData = (): void => {
  Object.keys(poiData.value).forEach(key => {
    poiData.value[key as keyof typeof poiData.value] = [];
  });
  clearAllMarkers();
  ElMessage.info('已清空所有数据');
};

// 清除所有标记
const clearAllMarkers = (): void => {
  if (!mapContext?.value) return;

  const { map } = mapContext.value;
  // 清除所有覆盖物
  map.clearMap();
};

// 导出数据
const exportPOIData = () => {
  const allPOIs = Object.values(poiData.value).flat()
  const dataStr = JSON.stringify(allPOIs, null, 2)
  const dataBlob = new Blob([dataStr], { type: 'application/json' })

  const link = document.createElement('a')
  link.href = URL.createObjectURL(dataBlob)
  link.download = `poi_data_${new Date().getTime()}.json`
  link.click()

  ElMessage.success('数据导出成功')
}

onMounted(() => {
  // 组件挂载后的初始化
  // 将 zoomToCluster 方法挂载到 window 上，以便在信息窗口中使用
  (window as any).vueComponent = {
    zoomToCluster
  }
})

defineExpose({
  smartBatchPOISearch,
  loadDataFromBackend,
  loadDataByType,
  manageData,
  exportPOIData,
  saveToBackend,
  getPOIData: () => poiData.value,
  getDataStats: () => dataStats.value,
  // 点聚合相关方法
  enableCluster: () => toggleCluster(true),
  disableCluster: () => toggleCluster(false),
  refreshCluster,
  getClusterConfig: () => clusterConfig.value
})
</script>

<style scoped>
/* 样式保持不变 */
.poi-manager {
  position: absolute;
  top: 130px;
  left: 20px;
  z-index: 999;
  background: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  min-width: 280px;
  max-width: 320px;
  max-height: 80vh;
  overflow-y: auto;
}

.search-progress {
  margin-bottom: 15px;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 6px;
  border-left: 4px solid #409eff;
}

.category-controls {
  margin: 15px 0;
}

.category-controls h5 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 14px;
}

.category-list {
  max-height: 200px;
  overflow-y: auto;
}

.category-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0;
  padding: 4px 0;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 15px 0;
}

.action-buttons .el-button {
  width: 100%;
}

.display-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 15px 0;
  padding: 10px 0;
  border-top: 1px solid #e4e7ed;
}

.quick-actions {
  display: flex;
  gap: 5px;
  justify-content: space-between;
}

.quick-actions .el-button {
  flex: 1;
}

/* 信息窗口样式 */
:deep(.poi-info-window) {
  min-width: 250px;
  max-width: 300px;
}

:deep(.poi-header) {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

:deep(.poi-header h4) {
  margin: 0;
  font-size: 16px;
  color: #333;
  flex: 1;
}

:deep(.poi-category) {
  background: #409eff;
  color: white;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  white-space: nowrap;
}

:deep(.poi-details) {
  font-size: 14px;
  color: #666;
}

:deep(.poi-details p) {
  margin: 4px 0;
}

.type-mapping-warning {
  margin-bottom: 15px;
}

.loading-data {
  margin-bottom: 15px;
  padding: 10px;
  background: #fff3cd;
  border-radius: 6px;
  border-left: 4px solid #ffc107;
}

.data-stats {
  margin: 15px 0;
  padding: 10px;
  background: #e7f3ff;
  border-radius: 6px;
  border-left: 4px solid #1890ff;
}

.data-stats h5 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 14px;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.stat-label {
  color: #666;
}

.stat-value {
  color: #333;
  font-weight: bold;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 15px 0;
}

.action-buttons .el-button {
  width: 100%;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .poi-manager {
    min-width: 250px;
    max-width: 280px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}

/* 点聚合样式 */
.cluster-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.cluster-advanced {
  margin-top: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
}

.cluster-settings {
  padding: 10px 0;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.setting-item span:first-child {
  min-width: 120px;
  font-size: 12px;
  color: #666;
}

.setting-item .el-slider {
  flex: 1;
  margin: 0 10px;
}

/* 聚合簇信息窗口样式 */
:deep(.cluster-info-window) {
  min-width: 200px;
  max-width: 280px;
}

:deep(.cluster-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

:deep(.cluster-header h4) {
  margin: 0;
  font-size: 16px;
  color: #333;
}

:deep(.cluster-total) {
  background: #409eff;
  color: white;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
}

:deep(.cluster-stats h5) {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: #666;
}

:deep(.cluster-category) {
  display: block;
  padding: 4px 0;
  font-size: 13px;
  color: #333;
  border-bottom: 1px dashed #f0f0f0;
}

:deep(.cluster-category:last-child) {
  border-bottom: none;
}

:deep(.cluster-actions) {
  margin-top: 10px;
  text-align: center;
}

:deep(.cluster-zoom-btn) {
  background: #409eff;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

:deep(.cluster-zoom-btn:hover) {
  background: #66b1ff;
}

/* 自定义聚合簇标记样式 */
:deep(.custom-cluster-marker) {
  animation: cluster-appear 0.3s ease-out;
}

@keyframes cluster-appear {
  from {
    transform: scale(0.8);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>