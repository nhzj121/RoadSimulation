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
              <span class="status-dot" :style="{ backgroundColor: statusMap[v.status].color }"></span>
              <div class="vehicle-info">
                <div class="vehicle-id">{{ v.id }}</div>
                <div class="vehicle-location">{{ v.location }} | {{ statusMap[v.status].text }}</div>
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
            <div><strong>运行车辆</strong><span></span></div>
            <div><strong>POI点数</strong><span></span></div>
            <div><strong>运输任务</strong><span></span></div>
            <div><strong>异常率</strong><span></span></div>
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
import {poiManagerApi} from "../api/poiManagerApi";
import AMapLoader from "@amap/amap-jsapi-loader";
import factoryIcon from '../../public/icons/factory.png';
import warehouseIcon from '../../public/icons/warehouse.png';
import gasStationIcon from '../../public/icons/gas-station.png';
import maintenanceIcon from '../../public/icons/maintenance-center.png';
import restAreaIcon from '../../public/icons/rest-area.png';
import transportIcon from '../../public/icons/distribution-center.png';
import materialMarketIcon from '../../public/icons/materialMarket.png';
import {
  ElHeader,
  ElAside,
  ElMain,
  ElContainer,
  ElCard,
  ElButton,
  ElButtonGroup,
  ElCheckTag, ElMessage,
} from "element-plus";
import { InfoFilled } from '@element-plus/icons-vue'

let map = null;
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

// ToDo 添加的用于展示POI点的功能
// 添加仿真循环时POI点自动生成的对应功能
const poiMarkers = ref([]); // 存储POI标记
const isSimulationRunning = ref(false); // 仿真运行状态

// 图标配置 - 根据POI类型使用不同的图标
const poiIcons = {
  'FACTORY': factoryIcon,
  'WAREHOUSE': warehouseIcon,
  'GAS_STATION': gasStationIcon,
  'MAINTENANCE_CENTER': maintenanceIcon,
  'REST_AREA': restAreaIcon,
  'DISTRIBUTION_CENTER': transportIcon,
  'MATERIAL_MARKET': materialMarketIcon
};

// 获取POI类型对应的图标
const getPOIIcon = (poiType) => {
  return poiIcons[poiType] || factoryIcon; // 默认使用工厂图标
};

// 启动仿真 - 获取并显示可展示的POI
const startSimulation = async () => {
  try {
    console.log("开始仿真");
    isSimulationRunning.value = true;

    // 获取可展示的POI数据
    const pois = await poiManagerApi.getPOIAbleToShow();
    console.log('获取到可展示的POI数据：', pois);

    // 清除现有标记
    clearPOIMarkers();

    // 添加POI标记到地图
    await addPOIMarkersToMap(pois);

    ElMessage.success(`成功加载 ${pois.length} 个POI点`);

  } catch (error) {
    console.error("启动仿真模拟失败：", error);
    ElMessage.error('获取POI数据失败：' + error.message);
  }
};

// 重置仿真
const resetSimulation = () => {
  console.log("重置仿真");
  isSimulationRunning.value = false;
  clearPOIMarkers();
  ElMessage.info('仿真已重置');
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

    for(const poi of pois){
      // 根据POI类型选择图标
      const iconUrl = getPOIIcon(poi.poiType);
      const icon = new AMap.Icon({
        image: iconUrl,
        size: new AMap.Size(32, 32),
        imageSize: new AMap.Size(32, 32)
      });

      const marker = new AMap.Marker({
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
      map.setFitView();
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
  ElMessage.info(`POI: ${poi.name} (${poiTypeText})`);

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
    'Vegetable_Base': '蔬菜基地',
    'Vegetable_Market': '蔬菜市场'
  };
  return typeMap[poiType] || poiType;
};

// 显示信息窗口
const showInfoWindow = (poi) => {
  if (!map) return;

  const infoWindow = new AMap.InfoWindow({
    content: `
            <div style="padding: 10px; min-width: 200px; color: #000;">
                <h3 style="margin: 0 0 8px 0; color: #000;">${poi.name}</h3>
                <p style="margin: 4px 0; color: #000;"><strong>类型:</strong> ${getPOITypeText(poi.poiType)}</p>
                <p style="margin: 4px 0; color: #000;"><strong>坐标:</strong> ${poi.longitude.toFixed(6)}, ${poi.latitude.toFixed(6)}</p>
                ${poi.address ? `<p style="margin: 4px 0; color: #000;"><strong>地址:</strong> ${poi.address}</p>` : ''}
                ${poi.tel ? `<p style="margin: 4px 0; color: #000;"><strong>电话:</strong> ${poi.tel}</p>` : ''}
            </div>
        `,
    offset: new AMap.Pixel(0, -30)
  });

  infoWindow.open(map, [poi.longitude, poi.latitude]);
};
// ToDo 添加的用于展示POI点的功能

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

const vehicles = reactive([
//各个汽车状态

]);

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
    plugins: ["AMap.Scale", "AMap.Driving", "AMap.Marker"], // 1. 在这里加载 AMap.Driving 插件
  })
    .then((AMap) => {
      map = new AMap.Map("container", {
        viewMode: "3D",
        zoom: 11,
        center: [104.066158, 30.657150],
      });
            // --- 驾车路线规划 ---
      var driving = new AMap.Driving({
        map: map, // 2. 将路线规划结果绘制到这个 map 对象上
        policy: AMap.DrivingPolicy.LEAST_TIME, // 驾车路线规划策略
      });

      // 起点和终点
      var points = [
        { keyword: "成都市政府", city: "成都" },
        { keyword: "成都东站", city: "成都" },
      ];

      driving.search(points, function (status, result) {
        // status：'complete' 表示查询成功
        // result 即为对应的驾车导航信息
        if (status === 'complete') {
            console.log('绘制驾车路线完成');
        } else {
            console.error('获取驾车数据失败：' + result);
        }
      });
      // --- 驾车路线规划结束 ---
    })
    .catch((e) => {
      console.log(e);
    });
});

onUnmounted(() => {
  map?.destroy();
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
