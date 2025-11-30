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
            <div v-for="v in vehicles" :key="v.id" class="vehicle-item">
              <span class="status-dot" :style="{ backgroundColor: statusMap[v.status]?.color || '#ccc' }"></span>
              <div class="vehicle-info">
                <div class="vehicle-id">{{ v.id }}</div>
                <div class="vehicle-location">{{ v.location || (v.lat && v.lng ? `${v.lng}, ${v.lat}` : '-') }} | {{ statusMap[v.status]?.text || v.status }}</div>
              </div>
              <ElButton text :icon="InfoFilled" />
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
const simulationInterval = ref(2000); // 5秒更新一次

// --- 原有POI功能 ---
const poiMarkers = ref([]); // 存储POI标记
const currentPOIs = ref([]); // 当前显示的POI数据
const isSimulationRunning = ref(false); // 仿真运行状态

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
    isSimulationRunning.value = true;

    // 初始加载POI数据
    await updatePOIData();

    // 启动定时更新
    //startSimulationTimer();

    // 使用一个简单且已知有效的测试坐标
    const testEndpoints = [
      {
        id: 'test-1',
        start: [104.123712, 30.511696],  // 北京天安门
        end: [104.059277, 30.505305]     // 附近点
      }
    ]

    console.log('使用测试坐标进行路线规划:', testEndpoints);

    try {
      const routes = await computeRoutesOnBackend(testEndpoints);
      console.log('测试路线规划成功，获取到路线:', routes);
      drawComputedRoutes(routes);
    } catch (error) {
      console.error('测试路线规划失败:', error);

      // 如果测试坐标也失败，说明是后端服务问题
      if (error.response?.status === 400) {
        const errorDetail = error.response?.data;
        console.error('后端返回的错误详情:', errorDetail);
        ElMessage.error(`路线规划服务错误: ${errorDetail?.message || '未知错误'}`);
      } else {
        ElMessage.error('路线规划失败: ' + error.message);
      }

      drawComputedRoutes([]);
    }
    // 启动车辆动画和路线规划
    await startVehicleSimulation();

    ElMessage.success('仿真已启动');

  } catch (error) {
    console.error("启动仿真模拟失败：", error);
    ElMessage.error('启动仿真失败：' + error.message);
    isSimulationRunning.value = false;
  }
};

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
      isSimulationRunning.value = false;

      // 停止定时器
      stopSimulationTimer();

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
      await updatePOIData();
      // 这里可以添加其他定时更新的数据，如车辆状态、任务状态等
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
  running: { text: '运输中', color: '#2ecc71' },
  loading: { text: '装卸货', color: '#f39c12' },
  maintenance: { text: '保养中', color: '#e74c3c' },
  stopped: { text: '停靠中', color: '#95a5a6' },
};

const vehicles = reactive([]); // 从后端拉取车辆列表

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
const animateAlongPath = (marker, path, speed = 20) => {
  // 确保运动路径的有效性
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

  // 根据行驶距离计算当前位置坐标
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
    // 初始化时间
    if (start === null) start = ts;

    const elapsed = (ts - start)/1000;
    // 计算当下行驶的距离
    const dist = Math.min(elapsed * speed, total);
    // 通过距离确定具体的位置
    const pos = seek(dist);
    // 应该是进行图标marker 的地图上的展示
    try { marker.setPosition(pos); } catch (e) {}
    if (dist >= total) return; // 到终点停止
    // 继续下一帧
    rafId = requestAnimationFrame(step);
  };
  rafId = requestAnimationFrame(step);

  return () => {
    canceled = true;
    if (rafId) cancelAnimationFrame(rafId);
  };
};

// 在起点添加 van 图标并匀速沿 path 移动
const drawComputedRoutes = (routes) => {
  if (!AMapLib || !map) return;
  clearDrawnRoutes();
  for (const r of routes) {
    try {
      const path = Array.isArray(r.path) ? r.path : (r.path || []);
      // 绘制折线
      const poly = new AMapLib.Polyline({
        path: path,
        strokeColor: '#3388ff',
        strokeOpacity: 1,
        strokeWeight: 4,
        lineJoin: 'round',
      });
      poly.setMap(map);
      drawnRoutes.push(poly);

      // 起点静态标记
      if (r.start) {
        const m1 = new AMapLib.Marker({
          position: r.start,
          title: '起点',
        });
        m1.setMap(map);
        drawnRoutes.push(m1);
      }

      // 终点静态标记
      if (r.end) {
        const m2 = new AMapLib.Marker({
          position: r.end,
          title: '终点',
        });
        m2.setMap(map);
        drawnRoutes.push(m2);
      }

      // 如果有路径，则在起点放置 van（可移动）
      if (path && path.length > 0) {
        const vanEl = createSvgVanEl(32, '#ff7f50');
        const movingMarker = new AMapLib.Marker({
          position: path[0],
          content: vanEl,
          offset: new AMapLib.Pixel(-16, -16),
          title: `van-${r.id || Math.random().toString(36).slice(2,6)}`,
        });
        movingMarker.setMap(map);
        // 速度：优先使用后端返回的 r.speedMps，否则用默认 8 m/s
        const speedMps = typeof r.speedMps === 'number' ? r.speedMps : 8;
        const cancel = animateAlongPath(movingMarker, path, speedMps);
        vehicleAnimations.push({ marker: movingMarker, cancel });
      }
    } catch (e) {
      console.error('drawComputedRoutes error', e);
    }
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
        endpoints.map(ep => {
          const params = {
            startLon: String(ep.start[0]),
            startLat: String(ep.start[1]),
            endLon: String(ep.end[0]),
            endLat: String(ep.end[1]),
            strategy: '0'
          };

          return request
              .get('/api/routes/gaode/plan-by-coordinates', { params })
              .then(res => {
                const response = res.data;

                if (!response.success) {
                  throw new Error(response.message);
                }

                // 获取高德地图数据
                const gaodeData = response.data?.data;

                // 检查是否有路径数据
                if (!gaodeData?.paths?.length) {
                  throw new Error('没有找到路径方案');
                }

                const pathInfo = gaodeData.paths[0];

                // 从 steps 的 polyline 构建完整路径
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
                  speedMps: pathInfo.distance / pathInfo.duration
                };
              })
              .catch(error => {
                const errorMsg = error.response?.data?.message || error.message;
                throw new Error(`路线 ${ep.id} 规划失败: ${errorMsg}`);
              });
        })
    );

    return plans;
  } catch (e) {
    console.error('路线规划整体失败', e);
    throw e;
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

        // 保留原有的驾车路线规划示例
        var driving = new AMap.Driving({
          map: map,
          policy: AMap.DrivingPolicy.LEAST_TIME,
        });

        var points = [
          { keyword: "成都市政府", city: "成都" },
          { keyword: "成都东站", city: "成都" },
        ];

        driving.search(points, function (status, result) {
          if (status === 'complete') {
            console.log('绘制驾车路线完成');
          } else {
            console.error('获取驾车数据失败：' + result);
          }
        });
      })
      .catch((e) => {
        console.log(e);
      });
});

onUnmounted(() => {
  stopSimulationTimer();
  map?.destroy();
  clearDrawnRoutes();
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