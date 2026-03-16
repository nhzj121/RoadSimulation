<!-- POIManager.vue -->
<template>
  <div class="poi-manager">
    <el-container class="page-container" direction="vertical">
      <!-- 导航栏 -->
      <el-header class="header-navbar">
        <div class="navbar-content left-aligned">
          <h2 class="navbar-title" @click="goBack">运输车辆调度优化与仿真系统</h2>
          <div class="navbar-menu">
            <ElButton text>POI管理</ElButton>
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
                :disabled="!map"
            >
              {{ isSearching ? '搜索中...' : '开始POI搜索' }}
            </el-button>

            <el-button
                @click="loadDataFromBackend"
                type="warning"
                :loading="loadingData"
                :disabled="!map"
            >
              {{ loadingData ? '加载中...' : '从数据库加载' }}
            </el-button>

            <el-button @click="exportPOIData" :disabled="totalPOICount === 0">
              导出数据
            </el-button>
            <el-button @click="saveToBackend" type="success" :disabled="totalPOICount === 0">
              保存到数据库
            </el-button>

            <el-button @click="testSaveWithSimpleData" type="info">测试保存</el-button>

            <!-- 文件上传 -->
            <div class="file-upload-section">
              <el-upload
                  class="upload-demo"
                  action=""
                  :auto-upload="false"
                  :show-file-list="false"
                  :on-change="handleFileUpload"
                  accept=".json"
              >
                <el-button type="primary" style="width: 100%; margin-bottom: 10px;">
                  <i class="el-icon-upload"></i>
                  上传POI数据文件
                </el-button>
              </el-upload>
              <p class="upload-tip">支持JSON格式的POI数据文件</p>
            </div>

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

<!--          <div>-->
<!--            <el-button @click="resetAutoIncrement" type="danger" style="margin-top: 10px;">-->
<!--              重置数据库ID（危险！）-->
<!--            </el-button>-->
<!--            <el-button @click="safeResetAutoIncrement" type="warning" style="margin-top: 5px;">-->
<!--              安全重置ID-->
<!--            </el-button>-->
<!--          </div>-->
        </el-aside>

        <el-main>
          <!-- POI点展示的地图界面 -->
          <div id="poi-map-container" ref="mapContainer"></div>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, onUnmounted, computed, nextTick} from 'vue'
import {ElButton, ElMessage, ElMessageBox} from 'element-plus'
import {poiManagerApi, POIFromDB} from "../api/poiManagerApi";
import AMapLoader from "@amap/amap-jsapi-loader";
import factoryIcon from '../../public/icons/factory.png';
import warehouseIcon from '../../public/icons/warehouse.png';
import gasStationIcon from '../../public/icons/gas-station.png';
import maintenanceIcon from '../../public/icons/maintenance-center.png';
import restAreaIcon from '../../public/icons/rest-area.png';
import transportIcon from '../../public/icons/distribution-center.png';
import testIcon from '../../public/icons/test.png';
import timberYardIcon from '../../public/icons/timber-yard.png';
import sawmillIcon from '../../public/icons/sawmill.png';
import boardFactoryIcon from '../../public/icons/board-factory.png';
import ironMineIcon from '../../public/icons/iron-mine.png';
import steelMillIcon from '../../public/icons/steel-mill.png';
import steelProcessingPlantIcon from '../../public/icons/steel-processing-plant.png';
import furnitureFactoryIcon from '../../public/icons/furniture-factory.png';
import tireManufacturingPlant from '../../public/icons/tire-manufacturing-plant.png';
import autoAssemblyPlant from '../../public/icons/auto-assembly-plant.png';
import { useRouter } from 'vue-router'

// VueRouter配置
const router = useRouter()
const goBack = () => {
  router.push('/')
}
const goToPressureTest = () => {
  router.push('./pressure-test')
}

// 地图相关变量
const mapContainer = ref<HTMLElement>()
const map = ref<any>(null)
const AMap = ref<any>(null)

// 全局变量的接口定义设置
declare global {
  interface Window {
    _AMapSecurityConfig: {
      securityJsCode: string;
    };
  }
}

// POI数据接口定义
interface POI {
  id: string;
  name: string;
  poiType: string;
  location: { lng: number; lat: number };
  address: string;
  tel: string;
  category: string;
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
  transport: [],
  timberYard: [],
  sawmill: [],
  boardFactory: [],
  ironMine: [],
  steelMill: [],
  steelProcessingPlant: [],
  furnitureFactory: [],
  rubberProcessingPlant: [],
  tireManufacturingPlant: [],
  autoAssemblyPlant: [],
  test: [],
})

// POI分类配置
const poiCategories = ref<POICategory[]>([
  {
    name: 'factory',
    label: '工厂',
    types: ['170300'],
    keywords: [],//'木材厂','家具厂','水泥'
    visible: true
  },
  {
    name: 'warehouse',
    label: '仓库',
    types: ['070501'],
    keywords: [],//'仓库', '物流园', '仓储','京东物流'
    visible: true
  },
  {
    name: 'gasStation',
    label: '加油站',
    types: ['010100'],
    keywords: ['中国石化'],//'加油站',
    visible: true
  },
  {
    name: 'maintenance',
    label: '维修中心',
    types: ['035000'],
    keywords: [],//'货车维修'
    visible: true
  },
  {
    name: 'restArea',
    label: '休息区',
    types: ['180300'],
    keywords: [],//'服务区','休息区'
    visible: true
  },
  {
    name: 'transport',
    label: '运输中心',
    types: ['070500', '150107', '150210'],
    keywords: [],//'配送中心', '物流'
    visible: true
  },
  {
    name: 'timberYard',
    label: '原木厂',
    types: [],
    keywords: ['林场'],
    visible: true
  },
  {
    name: 'sawmill',
    label: '锯木厂',
    types: ['170300'],
    keywords: ['木材加工'],
    visible: true
  },
  {
    name: 'boardFactory',
    label: '板材厂',
    types: [],
    keywords: ['板材'],
    visible: true
  },
  {
    name: 'ironMine',
    label: '铁矿厂',
    types: ['170209'], // 160100 偏向矿产
    keywords: ['铁矿','钢'],
    visible: true
  },
  {
    name: 'steelMill',
    label: '冶钢厂',
    types: ['170205'],
    keywords: ['钢厂'],
    visible: true
  },
  {
    name: 'steelProcessingPlant',
    label: '钢材加工厂',
    types: ['170300'],
    keywords: ['钢材加工'],
    visible: true
  },
  {
    name: 'furnitureFactory',
    label: '家具制造厂',
    types: ['170300'],
    keywords: ['家具厂'],
    visible: true
  },
  {
    name: 'rubberProcessingPlant',
    label: '橡胶加工厂',
    types: ['170300'],
    keywords: ['橡胶'],
    visible: true
  },
  {
    name: 'tireManufacturingPlant',
    label: '轮胎制造厂',
    types: ['170300'],
    keywords: ['轮胎制造', '轮胎厂'],
    visible: true
  },
  {
    name: 'autoAssemblyPlant',
    label: '汽车总装厂',
    types: ['170300'],
    keywords: ['汽车制造'],
    visible: true
  },
  {
    name: 'test',
    label: '测试',
    types: ['060705'],
    keywords: [],
    visible: true
  }
]);

// 搜索状态
const searchProgress = ref({
  total: 0,
  completed: 0,
  currentCategory: '',
  currentKeyword: ''
})
const isSearching = ref(false)

// 成都平原搜索区域
const chengduPlainPolygon = [
  [105.142653,31.953638],
  [105.754144,31.049152],
  [105.310195,29.575159],
  [102.521285,26.021473],
  [101.792754,26.001013],
  [100.74549,26.531822],
  [102.726817,31.722436]
];
// 103.566708,31.019274|103.700000,31.100000|104.800000,31.100000|104.800000,30.300000|103.848084,30.076928|103.463537,30.174276|103.566708,31.019274

// 图标配置
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
  },
  '测试': {
    url: testIcon,
    size: [22,22],
    anchor: 'bottom-center',
    color: '#ff0000'
  },
  '原木厂': { url: timberYardIcon, size: [22, 22], anchor: 'bottom-center', color: '#8B4513' },      // 棕色
  '锯木厂': { url: sawmillIcon, size: [22, 22], anchor: 'bottom-center', color: '#A0522D' },      // 赭色
  '板材厂': { url: boardFactoryIcon, size: [22, 22], anchor: 'bottom-center', color: '#DEB887' },      // 浅木色
  '铁矿厂': { url: ironMineIcon, size: [22, 22], anchor: 'bottom-center', color: '#708090' },      // 铁灰色
  '冶钢厂': { url: steelMillIcon, size: [22, 22], anchor: 'bottom-center', color: '#B22222' },      // 火红色
  '钢材加工厂': { url: steelProcessingPlantIcon, size: [22, 22], anchor: 'bottom-center', color: '#4682B4' },  // 钢蓝色
  '家具制造厂': { url: furnitureFactoryIcon, size: [22, 22], anchor: 'bottom-center', color: '#D2691E' },   // 橙棕色
  '橡胶加工厂': { url: factoryIcon, size: [22, 22], anchor: 'bottom-center', color: '#556B2F' },        // 暗橄榄绿
  '轮胎制造厂': { url: tireManufacturingPlant, size: [22, 22], anchor: 'bottom-center', color: '#2F4F4F' },        // 深石板灰 (偏黑，类似轮胎)
  '汽车总装厂': { url: autoAssemblyPlant, size: [22, 22], anchor: 'bottom-center', color: '#4169E1' }         // 皇家蓝 (科技工业感)
};

// 数据加载状态
const loadingData = ref(false)
const loadProgress = ref(0)
const dataStats = ref({
  total: 0,
  loadTime: 0,
  source: ''
})

// 类型映射
const showTypeMappingWarning = ref(false)
const typeMapping = {
  'factory': 'FACTORY',
  'warehouse': 'WAREHOUSE',
  'gasStation': 'GAS_STATION',
  'maintenance': 'MAINTENANCE_CENTER',
  'restArea': 'REST_AREA',
  'transport': 'DISTRIBUTION_CENTER',
  'test': 'TEST',
  'timberYard': 'TIMBER_YARD',
  'sawmill': 'SAWMILL',
  'boardFactory': 'BOARD_FACTORY',
  'ironMine': 'IRON_MINE',
  'steelMill': 'STEEL_MILL',
  'steelProcessingPlant': 'STEEL_PROCESSING_PLANT',
  'furnitureFactory': 'FURNITURE_FACTORY',
  'rubberProcessingPlant': 'RUBBER_PROCESSING_PLANT',
  'tireManufacturingPlant': 'TIRE_MANUFACTURING_PLANT',
  'autoAssemblyPlant': 'AUTO_ASSEMBLY_PLANT',
} as const;

const reverseTypeMapping = {
  'FACTORY': 'factory',
  'WAREHOUSE': 'warehouse',
  'GAS_STATION': 'gasStation',
  'MAINTENANCE_CENTER': 'maintenance',
  'REST_AREA': 'restArea',
  'DISTRIBUTION_CENTER': 'transport',
  'TEST': 'test',
  'TIMBER_YARD': 'timberYard',
  'SAWMILL': 'sawmill',
  'BOARD_FACTORY': 'boardFactory',
  'IRON_MINE': 'ironMine',
  'STEEL_MILL': 'steelMill',
  'STEEL_PROCESSING_PLANT': 'steelProcessingPlant',
  'FURNITURE_FACTORY': 'furnitureFactory',
  'RUBBER_PROCESSING_PLANT': 'rubberProcessingPlant',
  'TIRE_MANUFACTURING_PLANT': 'tireManufacturingPlant',
  'AUTO_ASSEMBLY_PLANT': 'autoAssemblyPlant'
} as const;

// 地图初始化
const initMap = async () => {
  try {
    window._AMapSecurityConfig = {
      securityJsCode: "9df38c185c95fa1dbf78a1082b64f668",
    };

    const AMapInstance = await AMapLoader.load({
      key: "e0ea478e44e417b4c2fc9a54126debaa",
      version: "2.0",
      plugins: ["AMap.PlaceSearch", "AMap.Geocoder", "AMap.Scale", "AMap.ToolBar", "AMap.Polygon"],
    });

    AMap.value = AMapInstance;

    map.value = new AMapInstance.Map("poi-map-container", {
      viewMode: "3D",
      zoom: 11,
      center: [104.066158, 30.657150],
    });

    // 添加控件
    map.value.addControl(new AMapInstance.ToolBar());
    map.value.addControl(new AMapInstance.Scale());

    ElMessage.success('地图初始化成功');

  } catch (error) {
    console.error('地图初始化失败:', error);
    ElMessage.error('地图初始化失败');
  }
};

// const pathArr = [
//     [105.142653,31.953638],
//     [105.754144,31.049152],
//     [105.310195,29.575159],
//     [102.521285,26.021473],
//     [101.792754,26.001013],
//     [100.74549,26.531822],
//     [102.726817,31.722436]
//     // 105.142653,31.953638|105.754144,31.049152|105.310195,29.575159|102.521285,26.021473|101.792754,26.001013|100.74549,26.531822102.726817,31.722436|105.142653,31.953638
// ];
//
// const drawPolygon = () => {
//   if (!map.value || !AMap.value) return;
//
//   // 1.3 创建 Polygon 对象
//   const polygon = new AMap.value.Polygon({
//     path: pathArr, // 使用你现有的成都平原边界数组
//     fillColor: "#ccebc5",      // 多边形填充颜色
//     fillOpacity: 0.5,          // 多边形填充透明度
//     strokeColor: "#2b8cbe",    // 线条颜色
//     strokeWeight: 2            // 线条宽度
//   });
//
//   // 1.4 给 Polygon 添加事件
//   // 鼠标移入更改样式
//   polygon.on("mouseover", () => {
//     polygon.setOptions({
//       fillOpacity: 0.7, // 多边形填充透明度
//       fillColor: "#7bccc4",
//     });
//   });
//
//   // 鼠标移出恢复样式
//   polygon.on("mouseout", () => {
//     polygon.setOptions({
//       fillOpacity: 0.5,
//       fillColor: "#ccebc5",
//     });
//   });
//
//   // 1.5 添加 Polygon 实例至地图
//   map.value.add(polygon);
//
//   // 可选：自动调整地图视野以完全展示这个多边形
//   // map.value.setFitView([polygon]);
// };



// 从后端加载数据
const loadDataFromBackend = async (): Promise<void> => {
  if (!map.value) {
    ElMessage.warning('地图未初始化')
    return
  }

  loadingData.value = true
  loadProgress.value = 0
  const startTime = Date.now()
  showTypeMappingWarning.value = false

  try {
    ElMessage.info('开始从数据库加载POI数据...')

    const progressInterval = setInterval(() => {
      if (loadProgress.value < 90) {
        loadProgress.value += 10
      }
    }, 200)

    const poisFromDB = await poiManagerApi.getAll()

    clearInterval(progressInterval)
    loadProgress.value = 100

    if (poisFromDB && poisFromDB.length > 0) {
      const convertedPOIs = convertDBDataToFrontend(poisFromDB)
      classifyPOIData(convertedPOIs)
      updateMapDisplay()

      const endTime = Date.now()
      dataStats.value = {
        total: convertedPOIs.length,
        loadTime: endTime - startTime,
        source: '数据库'
      }

      ElMessage.success(`成功加载 ${convertedPOIs.length} 个POI数据`)

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
    if (!reverseTypeMapping[poi.poiType as keyof typeof reverseTypeMapping]) {
      unmappedTypes.add(poi.poiType)
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

  // 如果没有数据，直接返回空数组
  if (!dbData || dbData.length === 0) {
    console.log('没有数据需要转换')
    console.groupEnd()
    return []
  }

  const convertedPOIs = dbData.map((item, index) => {
    // 防御性编程：确保item存在
    if (!item) {
      console.warn(`[${index}] 数据项为空，跳过`)
      return null
    }

    // 标准化类型处理
    let frontendCategory = 'unknown'

    // 安全地处理type字段
    const itemType = (item.poiType || '').toString().trim()
    const normalizedType = itemType.toUpperCase()

    console.log(`[${index}] 转换: "${item.name}" - 后端类型: "${itemType}"`)

    // 尝试直接映射
    if (normalizedType && reverseTypeMapping[normalizedType as keyof typeof reverseTypeMapping]) {
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
    // 如果类型为空
    else if (!normalizedType) {
      console.warn(`  ⚠️ POI "${item.name}" 的类型为空，映射到unknown分类`)
    }
    // 未知类型
    else {
      console.warn(`  ⚠️ 未知POI类型: "${itemType}"，映射到unknown分类`)
    }

    // 创建转换后的POI对象，确保所有字段都有默认值
    const converted: POI = {
      id: (item.id || `unknown-${Date.now()}-${index}`).toString(),
      name: item.name || '未知名称',
      poiType: itemType,
      location: {
        lng: Number(item.longitude) || 0,
        lat: Number(item.latitude) || 0
      },
      address: item.address || '未知地址',
      tel: item.tel || '',
      category: frontendCategory
    }

    console.log(`  转换结果: category = "${frontendCategory}"`)
    return converted
  }).filter(poi => poi !== null) as POI[] // 过滤掉null值

  console.log(`转换完成: ${convertedPOIs.length} 条有效记录`)
  console.groupEnd()

  return convertedPOIs
}

// 转换前端数据为数据库格式
const convertFrontendDataToDB = (frontendData: POI[]): any[] => {
  return frontendData.map((poi, index) => {
    // 调试日志
    console.log(`转换POI ${index}:`, {
      "原分类": poi.category,
      "映射类型": typeMapping[poi.category as keyof typeof typeMapping]
    })

    // 确保类型映射正确
    const backendType = typeMapping[poi.category as keyof typeof typeMapping] || 'UNKNOWN'

    // 构建基础对象，确保所有必需字段都有默认值
    const dto: any = {
      name: poi.name || `未命名POI_${index}`,
      poiType: backendType,
      longitude: Number(poi.location.lng?.toFixed(6)) || 0,
      latitude: Number(poi.location.lat?.toFixed(6)) || 0,
    }

    // 处理可选字段，确保不为null
    if (poi.id && poi.id !== 'unknown' && poi.id !== 'null') {
      const idNum = Number(poi.id)
      if (!isNaN(idNum)) {
        dto.id = idNum
      }
    }

    // 确保字符串字段不为null
    dto.tel = poi.tel || ''
    dto.address = poi.address || ''

    // 验证必需字段
    const requiredFields = ['name', 'poiType', 'longitude', 'latitude']
    requiredFields.forEach(field => {
      if (dto[field] === null || dto[field] === undefined) {
        console.error(`❌ POI ${index} 的必需字段 ${field} 为空:`, dto[field])
        // 设置默认值
        if (field === 'name') dto.name = `未命名POI_${index}`
        if (field === 'poiType') dto.poiType = 'UNKNOWN'
        if (field === 'longitude') dto.longitude = 0
        if (field === 'latitude') dto.latitude = 0
      }
    })

    return dto
  })
}

// 在保存方法中添加调试信息
const saveToBackend = async (): Promise<void> => {
  try {
    const allPOIs = Object.values(poiData.value).flat()
    if (allPOIs.length === 0) {
      ElMessage.warning('没有数据可保存')
      return
    }

    console.log('原始前端数据:', allPOIs)

    const poisToSave = convertFrontendDataToDB(allPOIs)

    // 详细检查每个字段
    console.log('转换后的数据详情:')
    poisToSave.forEach((poi, index) => {
      console.log(`POI ${index}:`, {
        name: poi.name,
        poiType: poi.poiType,
        longitude: poi.longitude,
        latitude: poi.latitude,
        id: poi.id,
        tel: poi.tel,
        address: poi.address
      })

      // 检查是否有null或undefined
      Object.keys(poi).forEach(key => {
        if (poi[key as keyof typeof poi] === null || poi[key as keyof typeof poi] === undefined) {
          console.warn(`⚠️ POI ${index} 的字段 ${key} 为 null/undefined:`, poi[key as keyof typeof poi])
        }
      })
    })

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
    console.error('完整错误对象:', error)
    ElMessage.error('保存POI数据时发生错误: ' + (error as Error).message)
  }
}

// ToDO
// 临时测试函数
const testSaveWithSimpleData = async (): Promise<void> => {
  try {
    const testData = [{
      id: "3000",
      name: "测试POI",
      poiType: "WAREHOUSE",
      longitude: 104.066158,
      latitude: 30.657150,
      tel: "",
      address: "测试地址"
    }]

    console.log('发送测试数据:', testData)

    const result = await poiManagerApi.batchSave(testData)

    if (result.success) {
      ElMessage.success('测试数据保存成功！')
    } else {
      ElMessage.error(`测试保存失败: ${result.message}`)
    }
  } catch (error) {
    console.error('测试保存失败:', error)
    ElMessage.error('测试保存失败: ' + (error as Error).message)
  }
}
// ToDo

// ToDo
// 管理数据库自增计数
// 重置自增ID的方法
const resetAutoIncrement = async (): Promise<void> => {
  try {
    await ElMessageBox.confirm(
        '这将删除所有POI数据并将ID计数器重置为1。此操作不可逆！确定要继续吗？',
        '确认重置ID',
        {
          confirmButtonText: '确定重置',
          cancelButtonText: '取消',
          type: 'error',
          confirmButtonClass: 'el-button--danger'
        }
    )

    ElMessage.info('正在重置ID计数器...')

    // 调用重置接口
    const response = await fetch('http://localhost:8080/api/pois/reset-auto-increment', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      }
    })

    if (response.ok) {
      const result = await response.json()
      ElMessage.success(result.message || 'ID重置成功')

      // 清空前端数据
      clearAllData()
      console.log('数据库ID已重置为1')
    } else {
      const error = await response.json()
      ElMessage.error(error.error || '重置失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('重置失败:', error)
      ElMessage.error('重置失败: ' + (error as Error).message)
    }
  }
}

// 安全重置（只在表为空时重置）
const safeResetAutoIncrement = async (): Promise<void> => {
  try {
    await ElMessageBox.confirm(
        '这将重置ID计数器为1。此操作需要表为空才能执行。确定要继续吗？',
        '确认安全重置ID',
        {
          confirmButtonText: '确定重置',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    ElMessage.info('正在安全重置ID计数器...')

    const response = await fetch('http://localhost:8080/api/pois/safe-reset-auto-increment', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      }
    })

    if (response.ok) {
      const result = await response.json()
      ElMessage.success(result.message || 'ID安全重置成功')
      console.log('数据库ID已安全重置为1')
    } else {
      const error = await response.json()
      ElMessage.error(error.error || '安全重置失败: ' + (error.error || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('安全重置失败:', error)
      ElMessage.error('安全重置失败: ' + (error as Error).message)
    }
  }
}

// ToDo

// 搜索功能
const searchByKeywordWithPagination = async (keyword: string, categoryName: string): Promise<POI[]> => {
  return new Promise(async (resolve) => {
    if (!map.value || !AMap.value) {
      console.error('地图未就绪');
      resolve([]);
      return;
    }

    let pageIndex = 1;
    const pageSize = 50;
    let hasMoreData = true;
    let allResults: POI[] = [];

    while (hasMoreData && pageIndex <= 5) {
      try {
        const pois = await searchSinglePage(keyword, categoryName, pageIndex, pageSize);

        if (pois.length > 0) {
          allResults.push(...pois);
          console.log(`搜索 "${keyword}" 第${pageIndex}页找到 ${pois.length} 个${categoryName}`);

          if (pois.length < pageSize) {
            hasMoreData = false;
          } else {
            pageIndex++;
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

const searchSinglePage = (keyword: string, categoryName: string, pageIndex: number, pageSize: number): Promise<POI[]> => {
  return new Promise((resolve) => {
    if (!map.value || !AMap.value) {
      resolve([]);
      return;
    }

    const placeSearch = new AMap.value.PlaceSearch({
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
        // 过滤POI数据：如果是工厂分类，跳过名称中包含"仓库"的POI
        const pois: POI[] = result.poiList.pois
          .filter((poi: any) => {
            // 如果是工厂分类，检查名称是否包含"仓库"
            if (categoryName === '工厂' && poi.name && poi.name.includes('仓库')) {
              console.log(`🚫 跳过工厂分类中的仓库POI: ${poi.name}`);
              return false; // 跳过这个POI
            }
            return true; // 保留其他POI
          })
          .map((poi: any) => ({
            id: poi.id,
            name: poi.name,
            type: categoryName,
            location: poi.location,
            address: poi.address,
            tel: poi.tel || '',
            category: categoryKey
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
  if (!map.value) {
    ElMessage.warning('地图未初始化');
    return;
  }

  isSearching.value = true;
  const allPOIs: POI[] = [];

  const totalTasks = poiCategories.value.reduce((sum, category) => sum + category.keywords.length, 0);
  let completedTasks = 0;

  searchProgress.value = {
    total: totalTasks,
    completed: completedTasks,
    currentCategory: '',
    currentKeyword: ''
  };

  try {
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

        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }

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

  Object.keys(poiData.value).forEach(key => {
    const oldCount = poiData.value[key as keyof typeof poiData.value].length
    if (oldCount > 0) {
      console.log(`清空分类 ${key}: 原有 ${oldCount} 条数据`)
    }
    poiData.value[key as keyof typeof poiData.value] = []
  })

  console.log(`开始分类 ${pois.length} 个POI数据`)

  let classifiedCount = 0
  let unclassifiedCount = 0

  pois.forEach(poi => {
    const categoryKey = poi.category

    if (categoryKey === 'factory' && poi.name && poi.name.includes('仓库')) {
      console.log(`🚫 分类阶段跳过工厂仓库POI: ${poi.name}`)
      return
    }
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
  clearAllMarkers();
  addIndividualMarkers();
};

// 添加单独标记
const addIndividualMarkers = (): void => {
  if (!map.value || !AMap.value) {
    console.error('❌ 地图未就绪')
    return
  }

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
        const icon = new AMap.value.Icon({
          image: iconConfig.url,
          size: new AMap.value.Size(iconConfig.size[0], iconConfig.size[1]),
          imageSize: new AMap.value.Size(iconConfig.size[0], iconConfig.size[1]),
          anchor: iconConfig.anchor as any
        })

        const marker = new AMap.value.Marker({
          position: [poi.location.lng, poi.location.lat],
          title: `${poi.name} - ${category.label}`,
          icon: icon,
          offset: new AMap.value.Pixel(-iconConfig.size[0] / 2, -iconConfig.size[1]),
          extData: poi
        })

        marker.on('click', () => {
          console.log(`🖱️ 点击标记: ${poi.name}`)
          showPOIInfoWindow(poi, marker.getPosition())
        })

        map.value.add(marker)
        totalMarkers++

        if (index < 3) {
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

/// ToDo

// 文件上传处理
const handleFileUpload = (file: any): void => {
  const reader = new FileReader()

  reader.onload = (e) => {
    try {
      const content = e.target?.result as string
      const jsonData = JSON.parse(content)

      // 验证数据格式
      if (!Array.isArray(jsonData)) {
        ElMessage.error('文件格式错误：数据应该是数组格式')
        return
      }

      // 确认数据
      ElMessageBox.confirm(
          `确定要加载 ${jsonData.length} 个POI数据吗？这将替换当前所有数据。`,
          '确认加载文件数据',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
      ).then(async () => {
        await processUploadedPOIData(jsonData)
      }).catch(() => {
        ElMessage.info('已取消加载')
      })

    } catch (error) {
      console.error('文件解析失败:', error)
      ElMessage.error('文件解析失败，请检查文件格式')
    }
  }

  reader.onerror = () => {
    ElMessage.error('文件读取失败')
  }

  reader.readAsText(file.raw)
}

// 处理上传的POI数据
const processUploadedPOIData = async (uploadedData: any[]): Promise<void> => {
  try {
    ElMessage.info('开始处理上传的POI数据...')

    // 转换数据格式
    const convertedPOIs = convertUploadedDataToFrontend(uploadedData)

    if (convertedPOIs.length === 0) {
      ElMessage.warning('没有有效的POI数据可加载')
      return
    }

    // 分类存储数据
    classifyPOIData(convertedPOIs)

    // 更新地图显示
    updateMapDisplay()

    ElMessage.success(`成功加载 ${convertedPOIs.length} 个POI数据`)

    // 显示统计信息
    const endTime = Date.now()
    dataStats.value = {
      total: convertedPOIs.length,
      loadTime: endTime - Date.now(),
      source: '文件上传'
    }

  } catch (error) {
    console.error('处理上传数据失败:', error)
    ElMessage.error('处理POI数据失败: ' + (error as Error).message)
  }
}

// 转换上传数据为前端格式
const convertUploadedDataToFrontend = (uploadedData: any[]): POI[] => {
  console.group('📤 处理上传的POI数据')
  console.log(`开始转换 ${uploadedData.length} 条上传记录`)

  const convertedPOIs = uploadedData.map((item, index) => {
    // 防御性编程：确保item存在
    if (!item) {
      console.warn(`[${index}] 数据项为空，跳过`)
      return null
    }

    // 验证必需字段
    if (!item.name || !item.location || !item.category) {
      console.warn(`[${index}] POI数据缺少必需字段:`, item)
      return null
    }

    // 处理分类映射
    let frontendCategory = item.category

    // 如果category是后端类型，尝试映射到前端分类
    if (item.category in reverseTypeMapping) {
      frontendCategory = reverseTypeMapping[item.category as keyof typeof reverseTypeMapping]
    }

    // 验证分类是否有效
    if (!(frontendCategory in poiData.value)) {
      console.warn(`[${index}] 无效的分类: "${frontendCategory}"，POI: ${item.name}`)
      return null
    }

    // 处理坐标格式
    let lng: number, lat: number
    if (typeof item.location === 'object' && item.location.lng !== undefined && item.location.lat !== undefined) {
      lng = Number(item.location.lng)
      lat = Number(item.location.lat)
    } else {
      console.warn(`[${index}] 无效的坐标格式:`, item.location)
      return null
    }

    // 验证坐标有效性
    if (isNaN(lng) || isNaN(lat) || lng === 0 || lat === 0) {
      console.warn(`[${index}] 无效的坐标值:`, { lng, lat })
      return null
    }

    // 创建转换后的POI对象
    const converted: POI = {
      id: item.id?.toString() || `uploaded-${Date.now()}-${index}`,
      name: item.name || '未知名称',
      poiType: item.poiType || 'UNKNOWN',
      location: {
        lng: lng,
        lat: lat
      },
      address: item.address || '未知地址',
      tel: item.tel || '',
      category: frontendCategory
    }

    console.log(`✅ 转换成功: "${converted.name}" -> ${converted.category}`)
    return converted
  }).filter(poi => poi !== null) as POI[]

  console.log(`转换完成: ${convertedPOIs.length} 条有效记录`)
  console.groupEnd()

  return convertedPOIs
}

/// ToDo

// 显示POI信息窗口
const showPOIInfoWindow = (poi: POI, position: any): void => {
  if (!map.value || !AMap.value) return;

  const infoContent = `
    <div class="poi-info-window" style="color: #000000;">
      <div class="poi-header">
        <h4 style="color: #000000; margin: 0 0 8px 0;">${poi.name}</h4>
        <span class="poi-category" style="color: #ffffff; background-color: #409eff; padding: 2px 8px; border-radius: 4px; font-size: 12px;">${poi.category}</span>
      </div>
      <div class="poi-details" style="color: #000000; margin-top: 10px;">
        <p style="color: #000000; margin: 4px 0;"><strong style="color: #000000;">地址:</strong> ${poi.address}</p>
        ${poi.tel ? `<p style="color: #000000; margin: 4px 0;"><strong style="color: #000000;">电话:</strong> ${poi.tel}</p>` : ''}
        <p style="color: #000000; margin: 4px 0;"><strong style="color: #000000;">坐标:</strong> ${poi.location.lng.toFixed(6)}, ${poi.location.lat.toFixed(6)}</p>
      </div>
    </div>
  `;

  const infoWindow = new AMap.value.InfoWindow({
    content: infoContent,
    offset: new AMap.value.Pixel(0, -30)
  });

  infoWindow.open(map.value, position);
};

// 清除所有标记
const clearAllMarkers = (): void => {
  if (!map.value) return;
  map.value.clearMap();
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

onMounted(async () => {
  await nextTick()
  await initMap()
  console.log('POI管理页面加载完成')
})

onUnmounted(() => {
  if (map.value) {
    map.value.destroy()
  }
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

/* 导航栏样式 */
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
  cursor: pointer;
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

/* 搜索进度区域字体颜色 */
.search-progress {
  color: #000000; /* 黑色字体 */
  background: #f8f9fa;
  border-left: 4px solid #409eff;
  margin-bottom: 15px;
  padding: 10px;
  border-radius: 6px;
}

/* 地图容器样式 */
#poi-map-container {
  width: 100%;
  height: 100%;
  min-height: 600px;
}

/* 操作按钮样式 */
.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-buttons .el-button {
  width: 100%;
}

/* 分类控制样式 */
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

/* 文件上传区域样式 */
.file-upload-section {
  margin-bottom: 15px;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
  margin: 5px 0 0 0;
}

:deep(.upload-demo) {
  width: 100%;
}

/* 统计信息样式 */
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

/* 进度和加载状态样式 */
.search-progress,
.loading-data,
.type-mapping-warning {
  margin-bottom: 15px;
  padding: 10px;
  border-radius: 6px;
}

.search-progress {
  background: #f8f9fa;
  border-left: 4px solid #409eff;
}

.loading-data {
  background: #fff3cd;
  border-left: 4px solid #ffc107;
}

/* 快速操作样式 */
.quick-actions {
  display: flex;
  gap: 5px;
  justify-content: space-between;
}

.quick-actions .el-button {
  flex: 1;
}

/* Element Plus 样式覆盖 */
:deep(.el-card__header) {
  padding: 10px 15px;
  border-bottom: none;
}

:deep(.el-card__body) {
  padding: 15px;
}

:deep(.el-main) {
  padding: 0;
}

/* 添加POI信息窗口样式 */
:deep(.poi-info-window) {
  color: #000000 !important; /* 黑色字体 */
  font-family: "Microsoft YaHei", sans-serif;
}

:deep(.poi-info-window .poi-header h4) {
  color: #000000 !important;
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: bold;
}

:deep(.poi-info-window .poi-category) {
  color: #ffffff !important;
  background-color: #409eff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

:deep(.poi-info-window .poi-details) {
  color: #000000 !important;
}

:deep(.poi-info-window .poi-details p) {
  margin: 4px 0;
  color: #000000 !important;
  font-size: 14px;
}

:deep(.poi-info-window .poi-details strong) {
  color: #000000 !important;
}

/* 高德地图信息窗口样式覆盖 */
:deep(.amap-info-content) {
  color: #000000 !important;
  background-color: #ffffff !important;
  border: 1px solid #e4e7ed !important;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1) !important;
}

:deep(.amap-info-sharp) {
  border-top-color: #e4e7ed !important;
}
</style>