<template>
  <ElContainer class="page-container">
    <ElHeader class="header-navbar">
      <div class="navbar-content">
        <div class="navbar-left">
          <h2 class="navbar-title" @click="gotoMain">物流运输仿真系统</h2>
        </div>
        <div class="navbar-menu">
          <ElButton text @click="goToPOIManager">POI点管理</ElButton>
          <ElButton text>帮助文档</ElButton>
          <ElButton text>用户中心</ElButton>
        </div>
      </div>
    </ElHeader>
    <ElContainer>
      <ElAside width="320px" class="side-panel">
        <!-- 仿真控制 -->
        <ElCard shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>仿真控制</span>
            </div>
          </template>
          <div class="control-group">
            <span class="control-label">时间压缩:</span>
            <ElButtonGroup>
              <ElButton :type="speedFactor === 1 ? 'primary' : 'default'" @click="setSpeed(1)">1x</ElButton>
              <ElButton icon="el-icon-minus" @click="decSpeed"></ElButton>
              <ElButton icon="el-icon-plus" @click="incSpeed"></ElButton>
            </ElButtonGroup>
          </div>
          <div class="control-group" style="margin-top: 15px;">
            <ElButton type="primary" @click="startSimulation">▶ 开始</ElButton>
            <ElButton type="primary" @click="stopSimulation">⏯ 暂停</ElButton>
            <ElButton @click="resetSimulation">↻ 重置</ElButton>
          </div>
        </ElCard>

        <!-- 显示筛选 -->
        <ElCard shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>▼ 显示筛选</span>
            </div>
          </template>
          <div class="filter-tags">
            <ElCheckTag v-for="item in filters" :key="item.key" :checked="item.checked" @change="toggleFilter(item.key)">
              {{ item.label }}
            </ElCheckTag>
          </div>
        </ElCard>

        <!-- 车辆状态 -->
        <ElCard shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>车辆状态</span>
            </div>
          </template>
          <div class="vehicle-list">
            <div v-for="v in vehicles" :key="v.id" class="vehicle-item" @click="handleVehicleClick(v)" style="cursor: pointer;">
              <span class="status-dot" :style="{ backgroundColor: statusMap[v.status]?.color || '#ccc' }"></span>
              <div class="vehicle-info">
                <div class="vehicle-id">{{ v.licensePlate }}</div>
                <div class="vehicle-stats">
                  <!-- 载重信息 -->
                  <div class="load-info">
                    <span class="label">载重:</span>
                    <span class="value">{{ v.currentLoad.toFixed(1) }}/{{ v.maxLoadCapacity.toFixed(1) }}t</span>
                    <div class="progress-bar">
                      <div
                          class="progress-fill load-progress"
                          :style="{ width: `${v.loadPercentage}%` }"
                      ></div>
                    </div>
                  </div>
                  <!-- 载容信息 -->
                  <div class="volume-info">
                    <span class="label">载容:</span>
                    <span class="value">{{ v.currentVolume.toFixed(1) }}/{{ v.maxVolumeCapacity.toFixed(1) }}m³</span>
                    <div class="progress-bar">
                      <div
                          class="progress-fill volume-progress"
                          :style="{ width: `${v.volumePercentage}%` }"
                      ></div>
                    </div>
                  </div>
                  <!-- 位置和状态 -->
                  <div class="vehicle-location">
                    {{ v.location || '-' }} | {{ statusMap[v.status]?.text || v.status }}
                  </div>
                </div>
                <template v-if="v.currentAssignment">
                  <br><small>任务: {{ v.currentAssignment }}</small>
                </template>
              </div>
              <ElButton
                  text
                  :icon="InfoFilled"
                  @click.stop="handleVehicleClick(v)"
              />
            </div>
            <div v-if="vehicles.length === 0" class="no-vehicle">
              暂无运输任务
            </div>
          </div>
        </ElCard>

        <!-- 统计信息 -->
        <ElCard shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>统计信息</span>
            </div>
          </template>
          <div class="stats-info">
            <div><strong>运行车辆</strong><span>{{ stats.running }}</span></div>
            <div><strong>POI点数</strong><span>{{ stats.poiCount }}</span></div>
            <div><strong>运输任务</strong><span>{{ stats.tasks }}</span></div>
            <div><strong>异常率</strong><span>{{ stats.anomalyRate }}%</span></div>
          </div>
        </ElCard>

      </ElAside>
      <ElMain>
        <div id="container"></div>
      </ElMain>
    </ElContainer>
  </ElContainer>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from 'vue-router';
import { poiManagerApi } from "../api/poiManagerApi";
import { simulationController} from "@/api/simulationController";
import request from "../utils/request";
import AMapLoader from "@amap/amap-jsapi-loader";
import factoryIcon from '../../public/icons/factory.png';
import warehouseIcon from '../../public/icons/warehouse.png';
import gasStationIcon from '../../public/icons/gas-station.png';
import maintenanceIcon from '../../public/icons/maintenance-center.png';
import restAreaIcon from '../../public/icons/rest-area.png';
import transportIcon from '../../public/icons/distribution-center.png';
import materialMarketIcon from '../../public/icons/materialMarket.png';
import vegetableBaseIcon from '../../public/icons/vegetable-base.png';
import vegetableMarketIcon from '../../public/icons/vegetable-market.png';
import {
  ElHeader,
  ElAside,
  ElMain,
  ElContainer,
  ElCard,
  ElButton,
  ElButtonGroup,
  ElCheckTag, ElMessage,ElMessageBox
} from "element-plus";
import { InfoFilled } from '@element-plus/icons-vue'

let map = null;
let AMapLib = null; // 保存加载后的 AMap 构造对象
const router = useRouter()
const goToPOIManager = () => {
  router.push('/poi-manager')
}
const gotoMain = () => {
  router.push('./')
}

// --- 仿真控制 ---
const speedFactor = ref(1);
const setSpeed = (val) => speedFactor.value = val;
const decSpeed = () => speedFactor.value = Math.max(0.5, speedFactor.value - 0.5);
const incSpeed = () => speedFactor.value = Math.min(5, speedFactor.value + 0.5);

const simulationTimer = ref(null);
const simulationInterval = ref(8000); // 8秒更新一次

// --- 原有POI功能 ---
const poiMarkers = ref([]); // 存储POI标记
const currentPOIs = ref([]); // 当前显示的POI数据
const isSimulationRunning = ref(false); // 仿真运行状态

// 响应式数据
const drawnPairIds = ref(new Set()); // 已绘制的配对ID (可以删除)
const drawnAssignmentIds = ref(new Set()); // 已绘制的Assignment ID
const activeRoutes = ref(new Map()); // 当前活动的路线映射，key为assignmentId

// 图标配置 - 根据POI类型使用不同的图标
const poiIcons = {
  'FACTORY': factoryIcon,
  'WAREHOUSE': warehouseIcon,
  'GAS_STATION': gasStationIcon,
  'MAINTENANCE_CENTER': maintenanceIcon,
  'REST_AREA': restAreaIcon,
  'DISTRIBUTION_CENTER': transportIcon,
  'MATERIAL_MARKET': materialMarketIcon,
  'VEGETABLE_BASE': vegetableBaseIcon,
  'VEGETABLE_MARKET': vegetableMarketIcon,
};

// 获取POI类型对应的图标
const getPOIIcon = (poiType) => {
  const icon = poiIcons[poiType];

  if (icon) {
    return icon;
  } else {
    console.warn(`未找到POI类型 ${poiType} 对应的图标，使用默认工厂图标`);
    return factoryIcon; // 默认使用工厂图标
  }
};

// --- 核心仿真方法 ---

/**
 * 启动仿真
 */
const startSimulation = async () => {
  try {
    console.log("开始仿真");

    await simulationController.startSimulation();
    isSimulationRunning.value = true;

    // 启动定时更新
    startSimulationTimer();

    // 初始加载当前活跃的Assignment
    await fetchCurrentAssignments();

    // 初始化车辆信息
    await updateVehicleInfo();

    ElMessage.success('仿真已启动');

  } catch (error) {
    console.error("启动仿真模拟失败：", error);
    ElMessage.error('启动仿真失败：' + error.message);
    isSimulationRunning.value = false;
  }
};

/**
 * 暂停仿真
 */
const stopSimulation = async () => {
  try {
    console.log("已暂停仿真");
    await simulationController.stopSimulation();
    isSimulationRunning.value = false;
  } catch (error) {
    console.error("暂停仿真失败：", error);
    ElMessage.error('暂停仿真失败：' + error.message);
    isSimulationRunning.value = true;
  }
}

/**
 * 重置仿真
 */
const resetSimulation = async () => {
  try {
    // 简洁版确认对话框
    const confirmResult = await ElMessageBox.confirm(
        '确定要重置仿真吗？',
        '确认重置',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    );

    if (confirmResult === 'confirm') {
      console.log("重置仿真");
      await simulationController.resetSimulation();
      isSimulationRunning.value = false;

      // 停止定时器
      stopSimulationTimer();

      // 清理所有绘制的路线
      activeRoutes.value.forEach(routeData => {
        if (routeData.cleanup) {
          routeData.cleanup();
        }
      });
      activeRoutes.value.clear();
      drawnAssignmentIds.value.clear();

      // 清除所有可视化元素
      clearPOIMarkers();
      clearDrawnRoutes();

      // 重置数据
      currentPOIs.value = [];

      ElMessage.success('仿真已重置');
    }

  } catch (error) {
    // 用户点击取消
    ElMessage.info('已取消重置操作');
  }
};

/**
 * 启动仿真定时器
 */
const startSimulationTimer = () => {
  if (simulationTimer.value) {
    clearInterval(simulationTimer.value);
  }

  simulationTimer.value = setInterval(async () => {
    if (isSimulationRunning.value) {
      // 增量获取并绘制新配对
      await fetchAndDrawNewAssignments();

      // 定期检查并清理已完成的Assignment
      await checkAndCleanupCompletedAssignments();

      // 更新车辆信息
      await updateVehicleInfo();

      // ToDo这里可以添加其他定时更新的数据，如车辆状态、任务状态等
    }
  }, simulationInterval.value);
};

/**
 * 停止仿真定时器
 */
const stopSimulationTimer = () => {
  if (simulationTimer.value) {
    clearInterval(simulationTimer.value);
    simulationTimer.value = null;
  }
};

/**
 * 更新POI数据 - 从 startSimulation 中提取的核心方法
 */
const updatePOIData = async () => {
  try {
    console.log("更新POI数据");

    // 获取可展示的POI数据
    const pois = await poiManagerApi.getPOIAbleToShow();
    console.log('获取到可展示的POI数据：', pois);

    if (!pois || pois.length === 0) {
      console.warn('当前没有可展示的POI数据');
      return;
    }

    // 更新当前POI数据
    currentPOIs.value = pois;

    // 清除现有标记并重新添加
    clearPOIMarkers();
    await addPOIMarkersToMap(pois);

    // 更新统计信息
    stats.poiCount = pois.length;

    console.log(`成功更新 ${pois.length} 个POI点`);

  } catch (error) {
    console.error("更新POI数据失败：", error);
    // 不抛出错误，避免影响其他定时任务
  }
};

const fetchPOIPairs = async () => {
  try {
    const response = await request.get('/api/simulation/pairs/current');
    const pairs = response.data;
    console.log('获取到POI配对:', pairs);

    if (pairs && pairs.length > 0) {
      // 将配对转换为路线规划的endpoints
      const endpoints = pairs.map(pair => ({
        id: `${pair.startPOIId}_${pair.endPOIId}`,
        start: [pair.startLng, pair.startLat],
        end: [pair.endLng, pair.endLat],
        info: {
          startName: pair.startPOIName,
          endName: pair.endPOIName,
          goodsName: pair.goodsName,
          quantity: pair.quantity
        }
      }));

      // 调用路线规划
      const computedRoutes = await computeRoutesOnBackend(endpoints);
      drawComputedRoutes(computedRoutes);

      // 更新统计信息
      stats.tasks = pairs.length;
    } else {
      console.log('当前没有活跃的POI配对');
    }
  } catch (error) {
    console.error('获取POI配对失败:', error);
  }
};

// 清除POI标记
const clearPOIMarkers = () => {
  if(poiMarkers.value.length > 0 && map){
    poiMarkers.value.forEach(marker => {
      map.remove(marker);
    });
    poiMarkers.value = [];
    console.log('已清除所有POI标记');
  }
};

// 添加POI标记到地图
const addPOIMarkersToMap = async (pois) => {
  if(!map || !pois || pois.length === 0) {
    console.log('没有POI数据或地图未初始化');
    return;
  }

  try {
    const markers = [];
    const bounds = [];

    for(const poi of pois){
      // 根据POI类型选择图标
      if (!poi.longitude || !poi.latitude) {
        console.warn(`POI ${poi.name} 坐标无效，跳过`);
        continue;
      }

      const iconUrl = getPOIIcon(poi.poiType);
      const icon = new AMapLib.Icon({
        image: iconUrl,
        size: new AMapLib.Size(16, 16),
        imageSize: new AMapLib.Size(16, 16)
      });

      const marker = new AMapLib.Marker({
        position: [poi.longitude, poi.latitude],
        icon: icon,
        title: `${poi.name} (${poi.poiType})`,
        extData: poi // 将原始数据保存在标记中
      });

      // 添加点击事件
      marker.on('click', () => {
        handlePOIClick(poi);
      });

      // 添加到地图
      map.add(marker);
      markers.push(marker);
    }

    poiMarkers.value = markers;
    console.log(`成功添加 ${markers.length} 个POI标记到地图`);

    // 调整地图视野以包含所有标记
    if (markers.length > 0) {
      map.setFitView(bounds);
    }

  } catch (error){
    console.error('添加POI标记失败', error);
    throw error;
  }
};

// 处理POI点击事件
const handlePOIClick = (poi) => {
  console.log('点击POI:', poi);

  // 显示POI详细信息
  const poiTypeText = getPOITypeText(poi.poiType);

  // 显示信息窗口
  showInfoWindow(poi);
};

// 获取POI类型的中文显示
const getPOITypeText = (poiType) => {
  const typeMap = {
    'FACTORY': '工厂',
    'WAREHOUSE': '仓库',
    'GAS_STATION': '加油站',
    'MAINTENANCE_CENTER': '维修中心',
    'REST_AREA': '休息区',
    'DISTRIBUTION_CENTER': '运输中心',
    'MATERIAL_MARKET': '建材市场',
    'VEGETABLE_BASE': '蔬菜基地',
    'VEGETABLE_MARKET': '蔬菜市场',
  };
  return typeMap[poiType] || poiType;
};

// 显示信息窗口
const showInfoWindow = (poi) => {
  if (!map) return;

  const infoWindow = new AMapLib.InfoWindow({
    content: `
            <div style="padding: 10px; min-width: 200px; color: #000;">
                <h3 style="margin: 0 0 8px 0; color: #000;">${poi.name}</h3>
                <p style="margin: 4px 0; color: #000;"><strong>类型:</strong> ${getPOITypeText(poi.poiType)}</p>
                <p style="margin: 4px 0; color: #000;"><strong>坐标:</strong> ${poi.longitude.toFixed(6)}, ${poi.latitude.toFixed(6)}</p>
                ${poi.address ? `<p style="margin: 4px 0; color: #000;"><strong>地址:</strong> ${poi.address}</p>` : ''}
                ${poi.tel ? `<p style="margin: 4px 0; color: #000;"><strong>电话:</strong> ${poi.tel}</p>` : ''}
            </div>
        `,
    offset: new AMapLib.Pixel(0, -30)
  });

  infoWindow.open(map, [poi.longitude, poi.latitude]);
};

// --- 显示筛选 ---
const filters = reactive([
  { key: 'factory', label: '工厂', checked: true },
  { key: 'parking', label: '停车场', checked: true },
  { key: 'gas', label: '加油站', checked: true },
  { key: 'service', label: '保养站', checked: true },
  { key: 'route', label: '运输路线', checked: true },
]);
const toggleFilter = (key) => {
  const filter = filters.find(f => f.key === key);
  if (filter) {
    filter.checked = !filter.checked;
    console.log(`筛选 ${filter.label}: ${filter.checked}`);
  }
};

// --- 车辆状态 ---
const statusMap = {
  IDLE: { text: '空闲', color: '#95a5a6' },
  ORDER_DRIVING: { text: '前往接货', color: '#43f312' },
  LOADING: { text: '装货中', color: '#f39c12' },
  TRANSPORT_DRIVING: { text: '运输中', color: '#2ecc71' },
  UNLOADING: { text: '卸货中', color: '#f39c12' },
  WAITING: { text: '等待中', color: '#e74c3c' },
  BREAKDOWN: { text: '故障', color: '#e74c3c' },
  running: { text: '运输中', color: '#2ecc71' },
  loading: { text: '装卸货', color: '#f39c12' },
  maintenance: { text: '保养中', color: '#e74c3c' },
  stopped: { text: '停靠中', color: '#95a5a6' },
};

const vehicles = reactive([]); // 车辆列表，将从Assignment中获取

// 更新车辆信息的方法
const updateVehicleInfo = async () => {
  try {
    // 从Assignment获取车辆信息
    const response = await request.get('/api/assignments/active');
    const activeAssignments = response.data;

    // 清空当前车辆列表
    vehicles.splice(0, vehicles.length);

    // 从Assignment中提取车辆信息
    const vehicleMap = new Map(); // 用于去重，key为vehicleId

    activeAssignments.forEach(assignment => {
      if (assignment.vehicleId && assignment.licensePlate) {
        // 如果车辆已在map中，合并信息
        if (vehicleMap.has(assignment.vehicleId)) {
          const existingVehicle = vehicleMap.get(assignment.vehicleId);
          // 如果当前assignment有更详细的信息，更新
          if (assignment.vehicleStatus) {
            existingVehicle.status = assignment.vehicleStatus;
          }
          // 添加当前assignment到车辆的任务列表中
          if (!existingVehicle.assignments) {
            existingVehicle.assignments = [];
          }
          existingVehicle.assignments.push({
            id: assignment.assignmentId,
            routeName: assignment.routeName,
            goodsName: assignment.goodsName,
            quantity: assignment.quantity
          });
        } else {
          // 创建新车辆记录
          const vehicle = {
            id: assignment.vehicleId,
            licensePlate: assignment.licensePlate,
            status: assignment.vehicleStatus || 'running',
            assignments: [{
              id: assignment.assignmentId,
              routeName: assignment.routeName,
              goodsName: assignment.goodsName,
              quantity: assignment.quantity
            }],
            // 位置信息
            location: assignment.startLat && assignment.startLng ?
                `${assignment.startLng.toFixed(4)}, ${assignment.startLat.toFixed(4)}` : '-',
            lat: assignment.startLat,
            lng: assignment.startLng,
            // 任务信息
            currentAssignment: assignment.routeName,
            goodsInfo: assignment.goodsName,
            quantity: assignment.quantity,
            startPOI: assignment.startPOIName,
            endPOI: assignment.endPOIName,
            // 载重信息
            currentLoad: assignment.currentLoad || 0,
            maxLoadCapacity: assignment.maxLoadCapacity || 0,
            // 载容信息
            currentVolume: assignment.currentVolume || 0,
            maxVolumeCapacity: assignment.maxVolumeCapacity || 0,
            // 货物单位信息
            goodsWeightPerUnit: assignment.goodsWeightPerUnit || 0,
            goodsVolumePerUnit: assignment.goodsVolumePerUnit || 0
          };

          // 计算载重和载容的百分比（用于进度条显示）
          vehicle.loadPercentage = vehicle.maxLoadCapacity > 0 ?
              Math.min(100, (vehicle.currentLoad / vehicle.maxLoadCapacity) * 100) : 0;
          vehicle.volumePercentage = vehicle.maxVolumeCapacity > 0 ?
              Math.min(100, (vehicle.currentVolume / vehicle.maxVolumeCapacity) * 100) : 0;
          vehicleMap.set(assignment.vehicleId, vehicle);
        }
      }
    });
    // 计算总载重统计
    let totalLoad = 0;
    let totalCapacity = 0;
    let totalVolume = 0;
    let totalVolumeCapacity = 0;

    vehicleMap.forEach(vehicle => {
      totalLoad += vehicle.currentLoad || 0;
      totalCapacity += vehicle.maxLoadCapacity || 0;
      totalVolume += vehicle.currentVolume || 0;
      totalVolumeCapacity += vehicle.maxVolumeCapacity || 0;
    });

    // 更新统计信息
    stats.totalLoad = totalLoad;
    stats.totalCapacity = totalCapacity;
    stats.totalVolume = totalVolume;
    stats.totalVolumeCapacity = totalVolumeCapacity;
    stats.loadUtilization = totalCapacity > 0 ? (totalLoad / totalCapacity * 100) : 0;
    stats.volumeUtilization = totalVolumeCapacity > 0 ? (totalVolume / totalVolumeCapacity * 100) : 0;

    // 将map中的车辆添加到列表中
    vehicleMap.forEach(vehicle => {
      vehicles.push(vehicle);
    });

    // 更新统计信息
    stats.running = vehicles.length;
    console.log(`更新了 ${vehicles.length} 辆车辆信息`);

  } catch (error) {
    console.error('获取车辆信息失败:', error);
  }
};

// 获取车辆详细信息
const getVehicleDetail = async (vehicleId) => {
  try {
    const response = await request.get(`/api/vehicles/${vehicleId}`);
    return response.data;
  } catch (error) {
    console.error(`获取车辆${vehicleId}详细信息失败:`, error);
    return null;
  }
};

// --- 新增：车辆动画和路线规划功能 ---

// 统计信息
const stats = reactive({
  running: 0,
  poiCount: 0,
  tasks: 0,
  anomalyRate: 0, // 百分比整数
});

const poisData = ref([]);    // POI 列表
const tasks = ref([]);   // 运输任务列表

const drawnRoutes = []; // 存放已绘制的覆盖物，便于清理
const vehicleAnimations = []; // 存放正在移动的 车辆marker，用于取消与清理

// 清理绘制的路线
const clearDrawnRoutes = () => {
  // 第一部分：清除所有已绘制的覆盖物（折线、标记等）
  for (const o of drawnRoutes) {
    try {
      // 如果覆盖物有setMap方法，则调用setMap(null)将其从地图上移除
      o.setMap && o.setMap(null);
    } catch (_) {} // 忽略错误
  }
  drawnRoutes.length = 0; // 清空drawnRoutes数组

  // 第二部分：清除所有车辆动画
  for (const a of vehicleAnimations) {
    try {
      // 如果动画有cancel方法，则调用取消动画
      a.cancel && a.cancel();
    } catch (_) {} // 忽略错误
    try {
      // 如果动画关联的标记存在，并且有setMap方法，则将其从地图上移除
      a.marker && a.marker.setMap && a.marker.setMap(null);
    } catch (_) {} // 忽略错误
  }
  vehicleAnimations.length = 0; // 清空vehicleAnimations数组
};

// 新增：创建 van 内联 SVG 元素（背景圆 + svg）
// 创建一个用于在前端地图界面上展示的自定义车辆图标
const createSvgVanEl = (size = 32, bg = '#ff7f50') => {
  const el = document.createElement('div');
  el.style.width = `${size}px`;
  el.style.height = `${size}px`;
  el.style.borderRadius = '50%';
  el.style.display = 'flex';
  el.style.alignItems = 'center';
  el.style.justifyContent = 'center';
  el.style.background = bg;
  el.style.color = '#fff';
  el.style.boxShadow = '0 1px 4px rgba(0,0,0,0.3)';
  el.innerHTML = `<svg width="${Math.round(size*0.6)}" height="${Math.round(size*0.6)}" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
    <path d="M3 13v-6h11v6H3zm13 0h3l2 3v3h-3a2 2 0 0 1-2-2v-4zM6 18a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm10 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z"/>
  </svg>`;
  return el;
};

// 计算两点球面距离（米）
// a 和 b 是六位小数的经纬度坐标； 两者使用 [经度,纬度] 的形式
const haversineDistance = (a, b) => {
  const toRad = d => d * Math.PI / 180;
  const R = 6371000;
  const dLat = toRad(b[1] - a[1]);
  const dLon = toRad(b[0] - a[0]);
  const lat1 = toRad(a[1]), lat2 = toRad(b[1]);
  const sinDLat = Math.sin(dLat/2), sinDLon = Math.sin(dLon/2);
  const c = 2 * Math.asin(Math.sqrt(sinDLat*sinDLat + Math.cos(lat1)*Math.cos(lat2)*sinDLon*sinDLon));
  return R * c;
};

// marker 匀速沿 path 移动（path: [[lng,lat],...], speed 米/秒），返回 cancel 函数
// 方法基于车辆在 path 相邻两项之间 沿直线 匀速运动
const animateAlongPath = (marker, path, speed = 500, onArrivalCallback = null) => {
  if (!path || path.length < 2) return () => {};
  const segLengths = [];
  let total = 0;
  for (let i = 0; i < path.length - 1; i++) {
    const L = haversineDistance(path[i], path[i+1]);
    segLengths.push(L);
    total += L;
  }
  let start = null;
  let rafId = null;
  let canceled = false;

  const seek = (d) => {
    if (d <= 0) return path[0];
    if (d >= total) return path[path.length-1];
    let acc = 0;
    for (let i = 0; i < segLengths.length; i++) {
      const L = segLengths[i];
      if (acc + L >= d) {
        const t = (d - acc) / L;
        const a = path[i], b = path[i+1];
        return [ a[0] + (b[0]-a[0])*t, a[1] + (b[1]-a[1])*t ];
      }
      acc += L;
    }
    return path[path.length-1];
  };

  const step = (ts) => {
    if (canceled) return;
    if (start === null) start = ts;

    const elapsed = (ts - start)/1000;
    const dist = Math.min(elapsed * speed, total);
    const pos = seek(dist);

    try {
      marker.setPosition(pos);

      // 检查是否到达终点
      if (dist + speed >= total && onArrivalCallback) {
        // 添加一个小延迟，确保动画完全完成
        setTimeout(() => {
          onArrivalCallback(pos);
        }, 100);
        return;
      }
    } catch (e) {}

    if (dist >= total) return;
    rafId = requestAnimationFrame(step);
  };

  rafId = requestAnimationFrame(step);

  return () => {
    canceled = true;
    if (rafId) cancelAnimationFrame(rafId);
  };
};

// 清除特定Assignment的路线
const clearRouteByAssignmentId = (assignmentId) => {
  const routeData = activeRoutes.value.get(assignmentId);
  if (routeData) {
    // 清理动画
    routeData.animations.forEach(anim => {
      anim.cancel && anim.cancel();
      try {
        anim.marker && anim.marker.setMap && anim.marker.setMap(null);
      } catch (_) {}
    });

    // 清理地图元素
    routeData.elements.forEach(el => {
      try {
        el.setMap && el.setMap(null);
      } catch (_) {}
    });

    // 从映射中移除
    activeRoutes.value.delete(assignmentId);
    drawnAssignmentIds.value.delete(assignmentId);

    console.log(`已清理Assignment ${assignmentId} 的路线`);
  }
};

// 清除特定配对的路线
const clearRouteByPairId = (pairId) => {
  const routeData = activeRoutes.value.get(pairId);
  if (routeData) {
    // 清理动画
    routeData.animations.forEach(anim => {
      anim.cancel && anim.cancel();
      try {
        anim.marker && anim.marker.setMap && anim.marker.setMap(null);
      } catch (_) {}
    });

    // 清理地图元素
    routeData.elements.forEach(el => {
      try {
        el.setMap && el.setMap(null);
      } catch (_) {}
    });

    // 从映射中移除
    activeRoutes.value.delete(pairId);
    drawnPairIds.value.delete(pairId);

    console.log(`已清理配对 ${pairId} 的路线`);
  }
};

// ToDo 接下来继续前端数据基础的依据修改

// 增量获取并绘制POI配对
const fetchAndDrawNewPOIPairs = async () => {
  try {
    // 1. 获取新增的Assignment（用于前端绘制）
    const response = await request.get('/api/assignments/new');
    const newAssignments = response.data;

    if (!newAssignments || newAssignments.length === 0) {
      console.log('没有新增的Assignment');
      return;
    }

    console.log(`获取到 ${newAssignments.length} 个新增Assignment`);

    // 2. 转换为路线规划的endpoints
    const endpoints = newAssignments.map(assignment => ({
      id: assignment.assignmentId,
      start: [assignment.startLng, assignment.startLat],
      end: [assignment.endLng, assignment.endLat],
      info: {
        assignmentId: assignment.assignmentId,
        pairId: assignment.pairId, // 保留pairId用于兼容
        startName: assignment.startPOIName,
        endName: assignment.endPOIName,
        goodsName: assignment.goodsName,
        quantity: assignment.quantity,
        shipmentRefNo: assignment.shipmentRefNo,
        vehicleLicensePlate: assignment.licensePlate
      }
    }));

    // 3. 批量规划路线
    const computedRoutes = await computeRoutesOnBackend(endpoints);

    // 4. 绘制新路线
    for (const route of computedRoutes) {
      if (route && route.info && route.info.assignmentId) {
        // 确保该Assignment尚未绘制
        if (!drawnAssignmentIds.value.has(route.info.assignmentId)) {
          await drawSingleRoute(route);

          // 标记为已绘制
          drawnAssignmentIds.value.add(route.info.assignmentId);

          // 通知后端该Assignment已绘制
          try {
            await request.post(`/api/assignments/mark-drawn/${route.info.assignmentId}`);
          } catch (error) {
            console.error(`标记Assignment ${route.info.assignmentId} 为已绘制失败:`, error);
          }
        }
      }
    }

    // 5. 更新统计信息
    stats.tasks = drawnAssignmentIds.value.size;

  } catch (error) {
    console.error('获取并绘制新增Assignment失败:', error);
  }
};

const fetchAndDrawNewAssignments = async () => {
  try {
    const response = await request.get('/api/assignments/new');
    const newAssignments = response.data;

    if (!newAssignments || newAssignments.length === 0) {
      console.log('没有新增的Assignment');
      return;
    }

    console.log(`获取到 ${newAssignments.length} 个新增Assignment`);

    // 转换为路线规划的endpoints
    const endpoints = newAssignments.map(assignment => ({
      id: assignment.assignmentId,
      start: [assignment.startLng, assignment.startLat],
      end: [assignment.endLng, assignment.endLat],
      info: {
        assignmentId: assignment.assignmentId,
        pairId: assignment.pairId,
        startName: assignment.startPOIName,
        endName: assignment.endPOIName,
        goodsName: assignment.goodsName,
        quantity: assignment.quantity,
        shipmentRefNo: assignment.shipmentRefNo,
        vehicleLicensePlate: assignment.licensePlate,
        vehicleId: assignment.vehicleId,
        endPOIId: assignment.endPOIId,
        // 传递Assignment对象用于绘制车辆图标
        assignment: assignment
      }
    }));

    // 批量规划路线
    const computedRoutes = await computeRoutesOnBackend(endpoints);

    // 绘制新路线
    for (const route of computedRoutes) {
      if (route && route.info && route.info.assignmentId) {
        if (!drawnAssignmentIds.value.has(route.info.assignmentId)) {
          await drawSingleRoute(route);

          drawnAssignmentIds.value.add(route.info.assignmentId);

          try {
            await request.post(`/api/assignments/mark-drawn/${route.info.assignmentId}`);
          } catch (error) {
            console.error(`标记Assignment ${route.info.assignmentId} 为已绘制失败:`, error);
          }
        }
      }
    }

    stats.tasks = drawnAssignmentIds.value.size;

  } catch (error) {
    console.error('获取并绘制新增Assignment失败:', error);
  }
};

const drawVehicleIconAtStart = async (assignment) => {
  if (!AMapLib || !map) return null;

  try {
    const { vehicleStartLng, vehicleStartLat, licensePlate, vehicleId } = assignment;

    // 如果没有起始位置，跳过
    if (!vehicleStartLng || !vehicleStartLat) {
      console.warn(`Assignment ${assignment.assignmentId} 没有车辆起始位置信息`);
      return null;
    }

    // 创建车辆图标
    const vanEl = createSvgVanEl(32, '#ff7f50'); // 橙色车辆图标
    const vehicleIcon = new AMapLib.Icon({
      image: createSvgDataUrl(vanEl), // 需要将DOM元素转换为图片URL
      size: new AMapLib.Size(32, 32),
      imageSize: new AMapLib.Size(32, 32)
    });

    // 创建车辆标记
    const vehicleMarker = new AMapLib.Marker({
      position: [vehicleStartLng, vehicleStartLat],
      icon: vehicleIcon,
      offset: new AMapLib.Pixel(-16, -16),
      title: `${licensePlate} - 待出发`,
      extData: {
        type: 'vehicle',
        vehicleId: vehicleId,
        assignmentId: assignment.assignmentId,
        licensePlate: licensePlate
      }
    });

    // 添加到地图
    vehicleMarker.setMap(map);

    // 添加点击事件
    vehicleMarker.on('click', () => {
      handleVehicleMarkerClick(assignment);
    });

    console.log(`在起点(${vehicleStartLng}, ${vehicleStartLat})创建车辆图标: ${licensePlate}`);
    return vehicleMarker;

  } catch (error) {
    console.error('绘制车辆图标失败:', error);
    return null;
  }
};

// 将DOM元素转换为图片URL的辅助方法
const createSvgDataUrl = (domElement) => {
  // 创建一个canvas来绘制DOM元素
  const canvas = document.createElement('canvas');
  canvas.width = 32;
  canvas.height = 32;
  const ctx = canvas.getContext('2d');

  // 设置背景
  ctx.fillStyle = '#ff7f50';
  ctx.beginPath();
  ctx.arc(16, 16, 16, 0, Math.PI * 2);
  ctx.fill();

  // 绘制车辆SVG图标
  ctx.fillStyle = '#fff';
  ctx.fillText('🚚', 8, 22); // 使用文本表情作为简单图标

  return canvas.toDataURL();
};

// 绘制单个路线
const drawSingleRoute = async (route) => {
  if (!AMapLib || !map) return null;

  try {
    const path = Array.isArray(route.path) ? route.path : (route.path || []);
    const elements = [];
    const animations = [];

    const assignment = route.info?.assignment;

    // 如果有关联的Assignment且有车辆起始位置，先绘制车辆图标
    let vehicleMarker = null;
    if (assignment && assignment.vehicleStartLng && assignment.vehicleStartLat) {
      vehicleMarker = await drawVehicleIconAtStart(assignment);
      if (vehicleMarker) {
        elements.push(vehicleMarker);
      }
    }

    // 绘制折线
    const poly = new AMapLib.Polyline({
      path: path,
      strokeColor: '#3388ff',
      strokeOpacity: 0.8,
      strokeWeight: 4,
      lineJoin: 'round',
    });
    poly.setMap(map);
    elements.push(poly);

    // 起点标记
    if (route.start) {
      const startMarker = new AMapLib.Marker({
        position: route.start,
        title: `起点: ${route.info?.startName || '未知'}`,
        icon: new AMapLib.Icon({
          image: factoryIcon,
          size: new AMapLib.Size(24, 24),
          imageSize: new AMapLib.Size(24, 24)
        })
      });
      startMarker.setMap(map);
      elements.push(startMarker);

      // 起点信息窗口
      startMarker.on('click', () => {
        const infoWindow = new AMapLib.InfoWindow({
          content: `
      <div style="padding: 10px; min-width: 200px; color: #000;">
        <h3 style="margin: 0 0 8px 0; color: #000;">起点: ${route.info?.startName || '未知'}</h3>
        <p style="margin: 4px 0; color: #000;"><strong>Assignment ID:</strong> ${route.info?.assignmentId || 'N/A'}</p>
        <p style="margin: 4px 0; color: #000;"><strong>货物:</strong> ${route.info?.goodsName || '未知'}</p>
        <p style="margin: 4px 0; color: #000;"><strong>数量:</strong> ${route.info?.quantity || 0}</p>
        <p style="margin: 4px 0; color: #000;"><strong>运单号:</strong> ${route.info?.shipmentRefNo || 'N/A'}</p>
        <p style="margin: 4px 0; color: #000;"><strong>目的地:</strong> ${route.info?.endName || '未知'}</p>
      </div>
    `,
          offset: new AMapLib.Pixel(0, -30)
        });
        infoWindow.open(map, route.start);
      });
    }

    // 终点标记
    if (route.end) {
      const endMarker = new AMapLib.Marker({
        position: route.end,
        title: `终点: ${route.info?.endName || '未知'}`,
        icon: new AMapLib.Icon({
          image: materialMarketIcon,
          size: new AMapLib.Size(24, 24),
          imageSize: new AMapLib.Size(24, 24)
        })
      });
      endMarker.setMap(map);
      elements.push(endMarker);
    }

    // 车辆动画
    if (path && path.length > 0) {
      const vanEl = createSvgVanEl(32, '#ff7f50');
      const movingMarker = new AMapLib.Marker({
        position: path[0],
        content: vanEl,
        offset: new AMapLib.Pixel(-16, -16),
        title: `${route.info?.goodsName || '货物'}运输`,
      });
      movingMarker.setMap(map);
      elements.push(movingMarker);

      // 车辆信息窗口
      movingMarker.on('click', () => {
        const infoWindow = new AMapLib.InfoWindow({
          content: `
            <div style="padding: 10px; min-width: 220px; color: #000;">
              <h3 style="margin: 0 0 8px 0; color: #000;">运输车辆</h3>
              <p style="margin: 4px 0; color: #000;"><strong>配对ID:</strong> ${route.info?.pairId || 'N/A'}</p>
              <p style="margin: 4px 0; color: #000;"><strong>货物:</strong> ${route.info?.goodsName || '未知'}</p>
              <p style="margin: 4px 0; color: #000;"><strong>数量:</strong> ${route.info?.quantity || 0}</p>
              <p style="margin: 4px 0; color: #000;"><strong>路线:</strong> ${route.info?.startName || '起点'} → ${route.info?.endName || '终点'}</p>
              <p style="margin: 4px 0; color: #000;"><strong>距离:</strong> ${route.distance ? (route.distance / 1000).toFixed(2) + ' km' : 'N/A'}</p>
              <p style="margin: 4px 0; color: #000;"><strong>预计时间:</strong> ${route.duration ? Math.round(route.duration / 60) + ' 分钟' : 'N/A'}</p>
            </div>
          `,
          offset: new AMapLib.Pixel(0, -40)
        });
        infoWindow.open(map, movingMarker.getPosition());
      });

      // 到达终点回调函数
      const handleArrival = async (position) => {
        console.log(`车辆 ${route.info?.vehicleLicensePlate} 已到达终点`);

        try {
          // 通知后端车辆到达终点
          if (route.info?.assignmentId && route.info?.vehicleId && route.info?.endPOIId) {
            await request.post('/api/simulation/vehicle-arrived', {
              vehicleId: route.info.vehicleId,
              endPOIId: route.info.endPOIId
            });

            console.log(`已通知后端车辆到达终点: ${route.info.vehicleLicensePlate}`);

            // 更新车辆状态
            await updateVehicleInfo();
          }
        } catch (error) {
          console.error('通知车辆到达终点失败:', error);
        }
      };

      //const speedMps = typeof route.speedMps === 'number' ? route.speedMps : 20;
      const speedMps = 900;
      const cancelAnimation = animateAlongPath(movingMarker, path, speedMps, handleArrival);
      animations.push({ marker: movingMarker, cancel: cancelAnimation });
    }

    // 保存路线数据
    const routeData = {
      id: route.info?.pairId || route.id,
      elements,
      animations,
      vehicleMarker: vehicleMarker, // 保存车辆标记引用
      movingMarker: movingMarker, // 保存移动车辆标记引用
      cleanup: () => {
        animations.forEach(anim => {
          anim.cancel && anim.cancel();
          try {
            anim.marker && anim.marker.setMap && anim.marker.setMap(null);
          } catch (_) {}
        });
        elements.forEach(el => {
          try {
            el.setMap && el.setMap(null);
          } catch (_) {}
        });
      }
    };

    activeRoutes.value.set(route.info?.pairId || route.id, routeData);

    console.log(`成功绘制配对 ${route.info?.pairId} 的路线`);
    return routeData;

  } catch (e) {
    console.error('绘制单个路线错误', e);
    return null;
  }
};

// 处理车辆标记点击事件
const handleVehicleMarkerClick = async (assignment) => {
  console.log('点击车辆标记:', assignment);

  try {
    // 获取车辆详细信息
    const vehicleDetail = await getVehicleDetail(assignment.vehicleId);

    // 显示车辆信息窗口
    showVehicleInfoWindowFromMarker(assignment, vehicleDetail);
  } catch (error) {
    console.error('获取车辆信息失败:', error);
    // 显示基本信息
    showVehicleInfoWindowFromMarker(assignment, null);
  }
};

// 从标记点击显示车辆信息窗口
const showVehicleInfoWindowFromMarker = (assignment, vehicleDetail) => {
  if (!map) return;

  // 构建信息窗口内容
  let content = `
    <div style="padding: 12px; min-width: 320px; color: #000;">
      <div style="display: flex; align-items: center; margin-bottom: 10px;">
        <div style="width: 32px; height: 32px; background-color: #ff7f50; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-right: 10px; color: #fff; font-size: 18px;">🚚</div>
        <div>
          <h3 style="margin: 0; color: #000; font-size: 16px;">${assignment.licensePlate || '未知车辆'}</h3>
          <p style="margin: 2px 0 0 0; color: #606266; font-size: 12px;">车辆ID: ${assignment.vehicleId}</p>
        </div>
      </div>
  `;

  // 状态信息
  const status = assignment.vehicleStatus || 'ORDER_DRIVING';
  const statusText = statusMap[status]?.text || status;
  const statusColor = statusMap[status]?.color || '#ccc';

  content += `
    <div style="margin-bottom: 12px;">
      <div style="display: flex; align-items: center;">
        <div style="width: 8px; height: 8px; border-radius: 50%; background-color: ${statusColor}; margin-right: 6px;"></div>
        <strong>状态:</strong> ${statusText}
      </div>
      <p style="margin: 4px 0; color: #000;"><strong>任务状态:</strong> ${assignment.status || 'ASSIGNED'}</p>
    </div>
  `;

  // 任务信息
  content += `
    <div style="margin-bottom: 12px; padding: 8px; background-color: #f8f9fa; border-radius: 4px;">
      <p style="margin: 4px 0; color: #000; font-weight: bold;">运输任务详情</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>Assignment ID:</strong> ${assignment.assignmentId}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>路线:</strong> ${assignment.routeName || '未命名路线'}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>起点:</strong> ${assignment.startPOIName || '未知'}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>终点:</strong> ${assignment.endPOIName || '未知'}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>货物:</strong> ${assignment.goodsName || '未知'} (${assignment.quantity || 0}件)</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>运单号:</strong> ${assignment.shipmentRefNo || 'N/A'}</p>
    </div>
  `;

  // 载重信息（如果有）
  if (assignment.currentLoad !== undefined && assignment.maxLoadCapacity !== undefined) {
    const loadPercentage = assignment.maxLoadCapacity > 0 ?
        Math.min(100, (assignment.currentLoad / assignment.maxLoadCapacity) * 100) : 0;

    content += `
      <div style="margin-bottom: 10px;">
        <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
          <span><strong>载重:</strong> ${assignment.currentLoad.toFixed(1)} / ${assignment.maxLoadCapacity.toFixed(1)} 吨</span>
          <span style="color: #67c23a; font-weight: bold;">${loadPercentage.toFixed(1)}%</span>
        </div>
        <div style="height: 6px; background-color: #ebeef5; border-radius: 3px; overflow: hidden;">
          <div style="width: ${loadPercentage}%; height: 100%; background-color: #67c23a;"></div>
        </div>
      </div>
    `;
  //   <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
  //     <span><strong>载容:</strong> ${vehicle.currentVolume.toFixed(1)} / ${vehicle.maxVolumeCapacity.toFixed(1)} m³</span>
  //     <span style="color: #409eff; font-weight: bold;">${vehicle.volumePercentage.toFixed(1)}%</span>
  //   </div>
  //   <div style="height: 8px; background-color: #ebeef5; border-radius: 4px; overflow: hidden; margin-bottom: 8px;">
  //     <div style="width: ${vehicle.volumePercentage}%; height: 100%; background-color: #409eff;"></div>
  //   </div>
  // </div>
  //   `;
  }

  // 车辆详细信息（如果有）
  if (vehicleDetail) {
    content += `
      <div style="margin-top: 12px; padding-top: 8px; border-top: 1px solid #eee;">
        <p style="margin: 4px 0; color: #000; font-weight: bold;">车辆详情</p>
        <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>车型:</strong> ${vehicleDetail.brand || '未知'} ${vehicleDetail.modelType || ''}</p>
        <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>车辆类型:</strong> ${vehicleDetail.vehicleType || '未知'}</p>
        <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>载重能力:</strong> ${vehicleDetail.maxLoadCapacity || 0} 吨</p>
        ${vehicleDetail.driverName ? `<p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>驾驶员:</strong> ${vehicleDetail.driverName}</p>` : ''}
      </div>
    `;
  }

  content += `</div>`;

  // 计算信息窗口位置
  const position = assignment.vehicleStartLng && assignment.vehicleStartLat ?
      [assignment.vehicleStartLng, assignment.vehicleStartLat] :
      [assignment.startLng, assignment.startLat];

  if (position[0] && position[1]) {
    const infoWindow = new AMapLib.InfoWindow({
      content: content,
      offset: new AMapLib.Pixel(0, -40)
    });

    infoWindow.open(map, position);
  }
};

// 定期检查并清理已完成的Assignment
const checkAndCleanupCompletedAssignments = async () => {
  try {
    // 获取需要清理的Assignment ID列表
    const response = await request.get('/api/assignments/to-cleanup');
    const assignmentIdsToCleanup = response.data;

    if (assignmentIdsToCleanup && assignmentIdsToCleanup.length > 0) {
      assignmentIdsToCleanup.forEach(assignmentId => {
        clearRouteByAssignmentId(assignmentId);
      });
      console.log(`清理了 ${assignmentIdsToCleanup.length} 个已完成的Assignment`);
    }
  } catch (error) {
    console.error('检查并清理已完成Assignment失败:', error);
  }
};

// 获取当前活跃的Assignment（用于初始加载）
const fetchCurrentAssignments = async () => {
  try {
    const response = await request.get('/api/assignments/active');
    const assignments = response.data;

    if (assignments && assignments.length > 0) {
      // 转换为路线规划的endpoints
      const endpoints = assignments.map(assignment => ({
        id: assignment.assignmentId,
        start: [assignment.startLng, assignment.startLat],
        end: [assignment.endLng, assignment.endLat],
        info: {
          assignmentId: assignment.assignmentId,
          pairId: assignment.pairId,
          startName: assignment.startPOIName,
          endName: assignment.endPOIName,
          goodsName: assignment.goodsName,
          quantity: assignment.quantity,
          shipmentRefNo: assignment.shipmentRefNo
        }
      }));

      // 批量规划路线
      const computedRoutes = await computeRoutesOnBackend(endpoints);

      // 绘制路线
      for (const route of computedRoutes) {
        if (route && route.info && route.info.assignmentId) {
          await drawSingleRoute(route);
          drawnAssignmentIds.value.add(route.info.assignmentId);
        }
      }

      // 更新统计信息
      stats.tasks = drawnAssignmentIds.value.size;
    }
  } catch (error) {
    console.error('获取当前Assignment失败:', error);
  }
};

// 定期检查并清理已完成的配对
const checkAndCleanupCompletedPairs = async () => {
  try {
    // 获取需要清理的配对ID列表
    const response = await request.get('/api/simulation/pairs/to-cleanup');
    const pairIdsToCleanup = response.data;

    if (pairIdsToCleanup && pairIdsToCleanup.length > 0) {
      pairIdsToCleanup.forEach(pairId => {
        clearRouteByPairId(pairId);
      });
      console.log(`清理了 ${pairIdsToCleanup.length} 个已完成的配对`);
    }
  } catch (error) {
    console.error('检查并清理已完成配对失败:', error);
  }
};

// 数据获取函数
const fetchVehicles = async () => {
  try {
    const response = await request.get('/api/vehicles');
    vehicles.splice(0, vehicles.length, ...response.data);
    stats.running = vehicles.filter(v => v.status === 'running').length;
  } catch (error) {
    console.error('获取车辆数据失败:', error);
  }
};

const fetchPOIs = async () => {
  try {
    const response = await request.get('/api/pois');
    poisData.value = response.data;
    stats.poiCount = poisData.value.length;
  } catch (error) {
    console.error('获取POI数据失败:', error);
  }
};

const fetchTasks = async () => {
  try {
    const response = await request.get('/api/tasks');
    tasks.value = response.data;
    stats.tasks = tasks.value.length;
  } catch (error) {
    console.error('获取任务数据失败:', error);
  }
};

const fetchRawRoutes = async () => {
  try {
    const response = await request.get('/api/routes');
    return response.data;
  } catch (error) {
    console.error('获取路线数据失败:', error);
    return [];
  }
};

// 调整路线计算接口
const computeRoutesOnBackend = async (endpoints) => {
  try {
    const plans = await Promise.all(
        endpoints.map(async (ep) => {
          try {
            const params = {
              startLon: String(ep.start[0]),
              startLat: String(ep.start[1]),
              endLon: String(ep.end[0]),
              endLat: String(ep.end[1]),
              strategy: '0'
            };

            const res = await request.get(
                '/api/routes/gaode/plan-by-coordinates',
                { params }
            );

            const response = res.data;

            if (!response.success) {
              console.error(`路线 ${ep.id} 规划失败:`, response.message);
              return null;
            }

            const gaodeData = response.data?.data;

            if (!gaodeData?.paths?.length) {
              console.error(`路线 ${ep.id}: 没有找到路径方案`);
              return null;
            }

            const pathInfo = gaodeData.paths[0];

            // 从steps的polyline构建完整路径
            let fullPath = [];
            if (pathInfo.steps) {
              pathInfo.steps.forEach(step => {
                if (step.polyline) {
                  const points = step.polyline.split(';');
                  points.forEach(pointStr => {
                    const [lng, lat] = pointStr.split(',').map(Number);
                    fullPath.push([lng, lat]);
                  });
                }
              });
            }

            console.log(`路线 ${ep.id} 规划成功，路径点数: ${fullPath.length}`);

            return {
              id: ep.id,
              path: fullPath,
              start: fullPath[0] || ep.start,
              end: fullPath[fullPath.length - 1] || ep.end,
              distance: pathInfo.distance,
              duration: pathInfo.duration,
              speedMps: pathInfo.distance / pathInfo.duration,
              info: ep.info // 传递配对信息
            };
          } catch (error) {
            console.error(`路线 ${ep.id} 规划出错:`, error);
            return null;
          }
        })
    );

    // 过滤掉失败的规划
    return plans.filter(plan => plan !== null);
  } catch (e) {
    console.error('路线规划整体失败', e);
    return [];
  }
};

// 启动车辆仿真
const startVehicleSimulation = async () => {

  try {
    console.log("开始仿真");
    isSimulationRunning.value = true;

    // 获取可展示的POI数据
    const pois = await poiManagerApi.getPOIAbleToShow();
    console.log('获取到可展示的POI数据：', pois);

    if (!pois || pois.length === 0) {
      ElMessage.warning('当前没有可展示的POI数据');
      return;
    }

    // 清除现有标记
    clearPOIMarkers();

    // 添加POI标记到地图
    await addPOIMarkersToMap(pois);

    ElMessage.success(`成功加载 ${pois.length} 个POI点`);

  } catch (error) {
    console.error("启动仿真模拟失败：", error);
    ElMessage.error('获取POI数据失败：' + error.message);
    // 重置状态
    isSimulationRunning.value = false;
  }


  // try {
  //   // 拉取前端需要展示的所有数据
  //   await Promise.all([
  //     fetchVehicles(),
  //     fetchPOIs(),
  //     fetchTasks()
  //   ]);
  //
  //   // 按既有流程拉取原始路线并请求后端规划，绘制路线
  //   const rawRoutes = await fetchRawRoutes();
  //   const endpoints = rawRoutes.map(r => {
  //     const pts = Array.isArray(r.points) ? r.points : (r.path || []);
  //     if (!pts || pts.length === 0) return null;
  //     const first = Array.isArray(pts[0]) ? pts[0] : [pts[0].lng, pts[0].lat];
  //     const last = Array.isArray(pts[pts.length - 1]) ? pts[pts.length - 1] : [pts[pts.length - 1].lng, pts[pts.length - 1].lat];
  //     return { id: r.id, start: first, end: last };
  //   }).filter(Boolean);
  //
  //   if (endpoints.length > 0) {
  //     const computed = await computeRoutesOnBackend(endpoints);
  //     drawComputedRoutes(computed);
  //   }
  // } catch (e) {
  //   console.error('车辆仿真初始化错误', e);
  // }
};

// --- 统计信息 ---
const runningVehicleCount = computed(() => {
  return vehicles.filter(v => v.status === 'running').length;
});

onMounted(() => {
  window._AMapSecurityConfig = {
    securityJsCode: "9df38c185c95fa1dbf78a1082b64f668",
  };
  AMapLoader.load({
    key: "e0ea478e44e417b4c2fc9a54126debaa",
    version: "2.0",
    plugins: ["AMap.Scale", "AMap.Driving", "AMap.Marker", "AMap.Polyline", "AMap.InfoWindow"],
  })
      .then((AMap) => {
        AMapLib = AMap; // 保存 AMap 构造体以便后续创建覆盖物
        map = new AMap.Map("container", {
          viewMode: "3D",
          zoom: 11,
          center: [104.066158, 30.657150],
        });
      })
      .catch((e) => {
        console.log(e);
      });
});

onUnmounted(() => {
  stopSimulationTimer();
  map?.destroy();
  // 清理所有绘制的路线
  activeRoutes.value.forEach(routeData => {
    if (routeData.cleanup) {
      routeData.cleanup();
    }
  });
  activeRoutes.value.clear();
  drawnPairIds.value.clear();
});
</script>

<style scoped>
.page-container {
  height: 100vh;
  width: 100vw;
}

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
  justify-content: flex-start;
  align-items: center;
  gap: 10px;
  padding-left: 20px;
  width: 100%;
}

.navbar-title {
  margin: 0;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
}

.navbar-menu {
  display: flex;
  gap: 10px;
}

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
}

.control-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.control-label {
  font-size: 14px;
  color: #606266;
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.vehicle-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.vehicle-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.vehicle-info {
  flex-grow: 1;
}

.vehicle-id {
  font-weight: 500;
  font-size: 14px;
}

.vehicle-location {
  font-size: 12px;
  color: #909399;
}

.stats-info div {
  font-size: 14px;
  line-height: 1.8;
}

/*
  车辆相关样式
 */
.vehicle-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 4px;
  margin-bottom: 8px;
  transition: background-color 0.2s;
}

.vehicle-item:hover {
  background-color: #f5f5f5;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.vehicle-info {
  flex-grow: 1;
  min-width: 0; /* 防止内容溢出 */
}

.vehicle-id {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
}

.vehicle-location {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.vehicle-location small {
  color: #67c23a;
  font-size: 11px;
}

.no-vehicle {
  text-align: center;
  padding: 20px;
  color: #909399;
  font-size: 14px;
}

/* 车辆标记样式 */
:deep(.amap-marker-content) {
  transition: transform 0.2s;
}

:deep(.amap-marker-content):hover {
  transform: scale(1.1);
}

/* 车辆信息窗口样式 */
.vehicle-marker-info {
  max-width: 300px;
}

/* 确保信息窗口内容可读 */
:deep(.amap-info-content) {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  line-height: 1.4;
}

:deep(.amap-info-sharp) {
  border-top-color: #fff !important;
}

/* 车辆统计信息样式 */
.vehicle-stats {
  margin-top: 4px;
}

.load-info,
.volume-info {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
  font-size: 11px;
}

.label {
  min-width: 32px;
  color: #606266;
  font-weight: 500;
}

.value {
  min-width: 60px;
  color: #303133;
  margin-right: 6px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background-color: #ebeef5;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
}

.load-progress {
  background-color: #67c23a; /* 绿色，表示载重 */
}

.volume-progress {
  background-color: #409eff; /* 蓝色，表示载容 */
}

.vehicle-location {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.2;
}

/* 车辆列表项悬停效果 */
.vehicle-item:hover {
  background-color: #f5f7fa;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 信息按钮 */
.info-btn {
  opacity: 0.7;
  transition: opacity 0.2s;
}

.info-btn:hover {
  opacity: 1;
}

/* 无车辆时的提示 */
.no-vehicle {
  text-align: center;
  padding: 20px;
  color: #c0c4cc;
  font-size: 13px;
  background-color: #fafafa;
  border-radius: 4px;
  margin-top: 10px;
}

/* 响应式调整 */
@media (max-width: 1400px) {
  .load-info,
  .volume-info {
    flex-direction: column;
    align-items: flex-start;
  }

  .label,
  .value {
    margin-bottom: 2px;
  }

  .progress-bar {
    width: 100%;
    margin-top: 2px;
  }
}

#container {
  width: 100%;
  height: 100%;
}

.el-main {
  padding: 0;
}

/* 覆盖Element Plus默认样式 */
:deep(.el-card__header) {
  padding: 10px 15px;
  border-bottom: none;
}
:deep(.el-card__body) {
  padding: 15px;
}
</style>