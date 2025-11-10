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
import {
  ElHeader,
  ElAside,
  ElMain,
  ElContainer,
  ElCard,
  ElButton,
  ElButtonGroup,
  ElCheckTag,
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
    plugins: ["AMap.Scale", "AMap.Driving"], // 1. 在这里加载 AMap.Driving 插件
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
