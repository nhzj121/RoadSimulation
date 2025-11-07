<template>
  <ElContainer class="page-container">
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
  
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, createHydrationRenderer } from "vue";
import AMapLoader from "@amap/amap-jsapi-loader";
import {
  ElAside,
  ElMain,
  ElContainer,
  ElCard,
  ElButton,
  ElButtonGroup,
  ElCheckTag,
} from "element-plus";
import { InfoFilled } from '@element-plus/icons-vue'
import axios from 'axios'

let map = null;
let AMapLib = null; // 保存加载后的 AMap 构造对象
const drawnRoutes = []; // 存放已绘制的覆盖物，便于清理

// --- 仿真控制 ---
const speedFactor = ref(1);
const setSpeed = (val) => speedFactor.value = val;
const decSpeed = () => speedFactor.value = Math.max(0.5, speedFactor.value - 0.5);
const incSpeed = () => speedFactor.value = Math.min(5, speedFactor.value + 0.5);
const startSimulation = () => console.log("开始仿真");
const resetSimulation = () => console.log("重置仿真");

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

// 新增：前端统计与数据容器
const stats = reactive({
  running: 0,
  poiCount: 0,
  tasks: 0,
  anomalyRate: 0, // 百分比整数
});

const pois = ref([]);    // POI 列表
const tasks = ref([]);   // 运输任务列表

// Axios 客户端与与后端/路线相关函数（已有）
const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
});

const clearDrawnRoutes = () => {
  for (const o of drawnRoutes) {
    try { o.setMap && o.setMap(null); } catch (_) {}
  }
  drawnRoutes.length = 0;
};

const fetchVehicles = async () => {
  try {
    const res = await api.get('/vehicles');
    // 约定后端返回数组或 { data: [...] }
    const list = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.items) ? res.data.items : []);
    vehicles.splice(0, vehicles.length, ...list);
    updateStats(); // 更新统计
  } catch (e) {
    console.error('fetchVehicles error', e);
  }
};

const fetchPOIs = async () => {
  try {
    const res = await api.get('/pois');
    const list = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.items) ? res.data.items : []);
    pois.value = list;
    stats.poiCount = list.length;
  } catch (e) {
    console.error('fetchPOIs error', e);
  }
};

const fetchTasks = async () => {
  try {
    const res = await api.get('/tasks');
    const list = Array.isArray(res.data) ? res.data : (Array.isArray(res.data?.items) ? res.data.items : []);
    tasks.value = list;
    stats.tasks = list.length;
  } catch (e) {
    console.error('fetchTasks error', e);
  }
};

// 根据当前 vehicles 数据计算运行车辆与异常率
const updateStats = () => {
  stats.running = vehicles.filter(v => v.status === 'running').length;
  // 优先使用车辆对象中的 anomaly 布尔字段计算异常率；否则以 status === 'maintenance' 作为异常示例
  const total = Math.max(vehicles.length, 1);
  const anomalies = vehicles.filter(v => v.anomaly === true).length || vehicles.filter(v => v.status === 'maintenance').length;
  stats.anomalyRate = Math.round((anomalies / total) * 100);
};

const fetchRawRoutes = async () => {
  try {
    const res = await api.get('/routes'); // 后端返回原始路线或任务，包含 id 与点列表
    return Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    console.error('fetchRawRoutes error', e);
    return [];
  }
};

// 将每条路线的起点/终点发送给后端，后端负责调用高德API规划并返回可绘制的路线数据
// 请求体示例: { endpoints: [{ id, start: [lng,lat], end: [lng,lat] }, ...] }
// 返回示例: [{ id, path: [[lng,lat],...], start: [lng,lat], end: [lng,lat] }, ...]
const computeRoutesOnBackend = async (endpoints) => {
  try {
    const res = await api.post('/routes/compute', { endpoints });
    return Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    console.error('computeRoutesOnBackend error', e);
    return [];
  }
};

const drawComputedRoutes = (routes) => {
  if (!AMapLib || !map) return;
  clearDrawnRoutes();
  for (const r of routes) {
    try {
      // 绘制路线折线 (后端返回的 path 应为 [[lng,lat], ...])
      const poly = new AMapLib.Polyline({
        path: r.path || [],
        strokeColor: '#3388ff',
        strokeOpacity: 1,
        strokeWeight: 4,
        lineJoin: 'round',
      });
      poly.setMap(map);
      drawnRoutes.push(poly);
      0

      // 起点标记
      if (r.start) {
        const m1 = new AMapLib.Marker({
          position: r.start,
          title: '起点',
        });
        m1.setMap(map);
        drawnRoutes.push(m1);
      }
      // 终点标记
      if (r.end) {
        const m2 = new AMapLib.Marker({
          position: r.end,
          title: '终点',
        });
        m2.setMap(map);
        drawnRoutes.push(m2);
      }
    } catch (e) {
      console.error('drawComputedRoutes error', e);
    }
  }
};

// --- 统计信息 ---
const runningVehicleCount = computed(() => {
    return vehicles.filter(v => v.status === 'running').length;
});

onMounted(() => {
  window._AMapSecurityConfig = {
    securityJsCode: "9df38c185c95fa1dbf78a1082b64f668", // 请替换为您的安全密钥
  };
  AMapLoader.load({
    key: "e0ea478e44e417b4c2fc9a54126debaa", // 请替换为您的Key
    version: "2.0",
    plugins: ["AMap.Scale", "AMap.Driving"], // 1. 在这里加载 AMap.Driving 插件
  })
    .then(async (AMap) => {
      AMapLib = AMap; // 保存 AMap 构造体以便后续创建覆盖物
      map = new AMap.Map("container", {
        viewMode: "3D",
        zoom: 11,
        center: [104.066158, 30.657150],
      });

      // 拉取前端需要展示的所有数据
      try {
        await Promise.all([
          fetchVehicles(),
          fetchPOIs(),
          fetchTasks()
        ]);

        // 按既有流程拉取原始路线并请求后端规划，绘制路线
        const rawRoutes = await fetchRawRoutes();
        const endpoints = rawRoutes.map(r => {
          const pts = Array.isArray(r.points) ? r.points : (r.path || []);
          if (!pts || pts.length === 0) return null;
          const first = Array.isArray(pts[0]) ? pts[0] : [pts[0].lng, pts[0].lat];
          const last = Array.isArray(pts[pts.length - 1]) ? pts[pts.length - 1] : [pts[pts.length - 1].lng, pts[pts.length - 1].lat];
          return { id: r.id, start: first, end: last };
        }).filter(Boolean);

        if (endpoints.length > 0) {
          const computed = await computeRoutesOnBackend(endpoints);
          drawComputedRoutes(computed);
        }
      } catch (e) {
        console.error('init data error', e);
      }
    })
    .catch((e) => {
      console.log(e);
    });
});

onUnmounted(() => {
  map?.destroy();
  clearDrawnRoutes();
});
</script>

<style scoped>
.page-container {
  height: 100vh;
  width: 100vw;
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
