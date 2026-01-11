<template>
  <ElContainer class="page-container">
    <ElHeader class="header-navbar">
      <div class="navbar-content">
        <div class="navbar-left">
          <h2 class="navbar-title" @click="gotoMain">运输车辆调度优化与仿真系统</h2>
        </div>
        <div class="navbar-menu">
          <ElButton text @click="goToPOIManager">POI点管理</ElButton>
        </div>
      </div>
    </ElHeader>
    <ElContainer>
      <ElAside width="320px" class="side-panel">
        <div class="side-panel-scroll" ref="sidePanelScroll">
          <!-- 仿真控制 -->
          <div class="panel-section">
            <ElCard shadow="never" class="box-card simulation-control">
              <template #header>
                <div class="card-header">
                  <span>仿真控制</span>
                </div>
              </template>
              <div class="control-group">
                <span class="control-label">时间压缩:</span>
                <div class="speed-slider">
                  <ElSlider
                      v-model="speedFactor"
                      :min="0.1"
                      :max="10"
                      :step="0.1"
                      :format-tooltip="formatSpeedTooltip"
                      @change="onSpeedChange"
                      size="small"
                  />
                </div>
              </div>
              <div class="control-group" style="margin-top: 15px;">
                <ElButton type="primary" @click="startSimulation">▶ 开始</ElButton>
                <ElButton type="primary" @click="pauseSimulation">⏸ 暂停</ElButton>
                <ElButton @click="resetSimulation">↻ 重置</ElButton>
              </div>
              <div class="speed-display" style="margin-top: 10px; font-size: 12px; color: #666;">
                当前速度: {{ formattedSpeed }}
              </div>
            </ElCard>
          </div>

          <!-- 车辆详细信息 -->
          <div class="panel-section" v-if="showVehicleDetail && selectedVehicle">
            <ElCard shadow="never" class="box-card vehicle-detail">
              <template #header>
                <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
                  <span>车辆详情</span>
                  <ElButton type="text" @click="closeVehicleDetail" style="padding: 0; height: auto;">关闭</ElButton>
                </div>
              </template>
              <div class="vehicle-detail-content">
                <!-- 车辆基本信息 -->
                <div class="vehicle-detail-header">
                  <div class="vehicle-detail-icon" :style="{ backgroundColor: getVehicleStatusColor(selectedVehicle.status) }">
                    {{ getVehicleStatusIcon(selectedVehicle.status) }}
                  </div>
                  <div class="vehicle-detail-title">
                    <h4>{{ selectedVehicle.licensePlate }}</h4>
                    <div class="vehicle-detail-subtitle">
                      <span class="status-badge" :style="{ backgroundColor: getVehicleStatusColor(selectedVehicle.status) }">
                        {{ getVehicleStatusText(selectedVehicle.status) }}
                      </span>
                      <span class="vehicle-id">ID: {{ selectedVehicle.id }}</span>
                    </div>
                  </div>
                </div>

                <!-- 状态信息 -->
                <div class="detail-section">
                  <div class="detail-item">
                    <span class="detail-label">当前动作:</span>
                    <span class="detail-value">{{ selectedVehicle.actionDescription || '无' }}</span>
                  </div>
                </div>

                <!-- 任务信息 -->
                <div class="detail-section" v-if="selectedVehicle.currentAssignment">
                  <h5>运输任务</h5>
                  <div class="detail-item">
                    <span class="detail-label">任务路线:</span>
                    <span class="detail-value">{{ selectedVehicle.currentAssignment }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">货物信息:</span>
                    <span class="detail-value">{{ selectedVehicle.goodsInfo || '未知' }} ({{ selectedVehicle.quantity || 0 }}件)</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">装货点:</span>
                    <span class="detail-value">{{ selectedVehicle.startPOI || '未知' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">卸货点:</span>
                    <span class="detail-value">{{ selectedVehicle.endPOI || '未知' }}</span>
                  </div>
                </div>

                <!-- 载重信息 -->
                <div class="detail-section">
                  <h5>载重状态</h5>
                  <div class="detail-item">
                    <span class="detail-label">载重:</span>
                    <span class="detail-value">
                      {{ selectedVehicle.currentLoad?.toFixed(1) || '0.0' }} / {{ selectedVehicle.maxLoadCapacity?.toFixed(1) || '0.0' }} 吨
                    </span>
                    <div class="detail-progress">
                      <div class="progress-bar">
                        <div
                            class="progress-fill load-progress"
                            :style="{ width: `${selectedVehicle.loadPercentage || 0}%` }"
                        ></div>
                      </div>
                      <span class="progress-text">{{ selectedVehicle.loadPercentage?.toFixed(1) || 0 }}%</span>
                    </div>
                  </div>
                </div>

                <!-- 车辆属性 -->
                <div class="detail-section">
                  <h5>车辆属性</h5>
                  <div class="detail-item">
                    <span class="detail-label">最大载重:</span>
                    <span class="detail-value">{{ selectedVehicle.maxLoadCapacity?.toFixed(1) || '0.0' }} 吨</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">货物单位重量:</span>
                    <span class="detail-value">{{ selectedVehicle.goodsWeightPerUnit?.toFixed(2) || '0.00' }} 吨/件</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">货物单位体积:</span>
                    <span class="detail-value">{{ selectedVehicle.goodsVolumePerUnit?.toFixed(2) || '0.00' }} m³/件</span>
                  </div>
                </div>
              </div>
            </ElCard>
          </div>

          <!-- 车辆状态 -->
          <div class="panel-section">
            <ElCard shadow="never" class="box-card vehicle-status">
              <template #header>
                <div class="card-header">
                  <span>车辆状态</span>
                </div>
              </template>
              <div class="vehicle-list">
                <!-- 为每个车辆项添加唯一的ID，用于滚动定位 -->
                <div
                    v-for="v in vehicles"
                    :key="v.id"
                    :id="`vehicle-item-${v.id}`"
                    class="vehicle-item"
                    :class="{ 'vehicle-item-highlighted': highlightedVehicleId === v.id }"
                    @click="handleVehicleClick(v)"
                    style="cursor: pointer;"
                >
                  <span class="status-dot" :style="{ backgroundColor: statusMap[v.status]?.color || '#ccc' }"></span>
                  <div class="vehicle-info">
                    <div class="vehicle-id">{{ v.licensePlate }}</div>
                    <div class="vehicle-stats">
                      <!-- 载重信息 -->
                      <div class="load-info">
                        <span class="label">载重:</span>
                        <span class="value">{{ v.currentLoad?.toFixed(1) || '0.0' }}/{{ v.maxLoadCapacity?.toFixed(1) || '0.0' }}t</span>
                        <div class="progress-bar">
                          <div
                              class="progress-fill load-progress"
                              :style="{ width: `${v.loadPercentage || 0}%` }"
                          ></div>
                        </div>
                      </div>
                      <!-- 位置和状态 -->
                      <div class="vehicle-location" :class="`status-${v.status?.toLowerCase()}`">
                        {{ v.actionDescription || statusMap[v.status]?.text || v.status || '未知' }}
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
          </div>

        </div>
      </ElAside>
      <ElMain>
        <div id="container"></div>
      </ElMain>
    </ElContainer>
  </ElContainer>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, markRaw , nextTick} from "vue";
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
  ElCheckTag,
  ElMessage,
  ElMessageBox,
  ElSlider
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
const goToPressureTest = () => {
  router.push('./pressure-test')
}

// --- 车辆详细信息相关 ---
const showVehicleDetail = ref(false);
const selectedVehicle = ref(null);

// 获取车辆状态颜色
const getVehicleStatusColor = (status) => {
  return statusMap[status]?.color || '#ccc';
};

// 获取车辆状态图标
const getVehicleStatusIcon = (status) => {
  const icons = {
    'TRANSPORT_DRIVING': '🚚',
    'ORDER_DRIVING': '🚗',
    'LOADING': '⏳',
    'UNLOADING': '📦',
    'IDLE': '🚙',
    'WAITING': '⏱️',
    'BREAKDOWN': '🔧'
  };
  return icons[status] || '🚗';
};

// 获取车辆状态文本
const getVehicleStatusText = (status) => {
  return statusMap[status]?.text || status;
};

// 关闭车辆详细信息
const closeVehicleDetail = () => {
  showVehicleDetail.value = false;
  selectedVehicle.value = null;
};

// --- 侧边栏滚动相关 ---
const sidePanelScroll = ref(null); // 侧边栏滚动容器引用
const highlightedVehicleId = ref(null); // 当前高亮的车辆ID
let highlightTimer = null; // 高亮定时器

const handleVehicleClick = (vehicle) => {
  console.log('点击车辆:', vehicle);

  // 设置选中的车辆
  selectedVehicle.value = { ...vehicle };
  showVehicleDetail.value = true;

  // 滚动到该车辆
  if (vehicle.id) {
    scrollToVehicle(vehicle.id);
  }

  // 可以在地图上高亮显示该车辆
  // highlightVehicleOnMap(vehicle);
};

// 滚动到指定车辆
const scrollToVehicle = (vehicleId) => {
  // 清除之前的高亮
  clearHighlight();

  // 设置当前高亮车辆ID
  highlightedVehicleId.value = vehicleId;

  // 等待DOM更新
  nextTick(() => {
    // 查找车辆元素
    const vehicleElement = document.getElementById(`vehicle-item-${vehicleId}`);

    if (vehicleElement && sidePanelScroll.value) {
      // 计算滚动位置
      const scrollContainer = sidePanelScroll.value;
      const containerRect = scrollContainer.getBoundingClientRect();
      const elementRect = vehicleElement.getBoundingClientRect();

      // 计算元素在容器内的相对位置
      const relativeTop = elementRect.top - containerRect.top;
      const containerHeight = containerRect.height;
      const elementHeight = elementRect.height;

      // 如果元素不在可视区域内，滚动到合适位置
      if (relativeTop < 0 || relativeTop + elementHeight > containerHeight) {
        // 计算目标滚动位置，使元素位于容器中间
        const targetScrollTop = scrollContainer.scrollTop + relativeTop - (containerHeight / 2) + (elementHeight / 2);

        // 平滑滚动到目标位置
        scrollContainer.scrollTo({
          top: Math.max(0, targetScrollTop),
          behavior: 'smooth'
        });
      }

      console.log(`已滚动到车辆 ${vehicleId}`);

      // 设置定时器，0.5秒后清除高亮
      highlightTimer = setTimeout(() => {
        highlightedVehicleId.value = null;
      }, 500);
    } else {
      console.warn(`未找到车辆 ${vehicleId} 的元素或滚动容器未初始化`);
    }
  });
};

// 清除高亮
const clearHighlight = () => {
  if (highlightTimer) {
    clearTimeout(highlightTimer);
    highlightTimer = null;
  }
  highlightedVehicleId.value = null;
};

// --- 仿真控制 ---
const speedFactor = ref(1);
const formattedSpeed = computed(() => `${speedFactor.value.toFixed(1)}x`);

const formatSpeedTooltip = (value) => {
  return `${value.toFixed(1)}x`;
};

const onSpeedChange = (value) => {
  if (animationManager) {
    animationManager.setGlobalSpeedFactor(value);
  }
  console.log(`速度因子调整为: ${value}`);
};

const simulationTimer = ref(null);
const simulationInterval = ref(3000); // 8秒更新一次

// --- 原有POI功能 ---
const poiMarkers = ref([]); // 存储POI标记
const currentPOIs = ref([]); // 当前显示的POI数据
const isSimulationRunning = ref(false); // 仿真运行状态

// 响应式数据
const drawnPairIds = ref(new Set()); // 已绘制的配对ID (可以删除)
const drawnAssignmentIds = ref(new Set()); // 已绘制的Assignment ID
const activeRoutes = ref(new Map()); // 当前活动的路线映射，key为assignmentId

// 路线规划缓存
const routePlanningCache = new Map();

// Assignment状态跟踪
const assignmentStates = new Map();

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

// ==================== 车辆状态管理器类 ====================
class VehicleStatusManager {
  constructor(vehiclesRef, mapRef) {
    this.vehicles = vehiclesRef; // 车辆列表引用
    this.map = mapRef; // 地图引用
    this.vehicleMarkers = new Map(); // 车辆ID -> 标记映射
    this.assignmentData = new Map(); // 车辆ID -> Assignment数据
    this.statusCallbacks = []; // 状态变化回调
  }

  /**
   * 注册车辆标记到管理器
   */
  registerVehicleMarker(vehicleId, marker, assignmentData = null) {
    this.vehicleMarkers.set(vehicleId, marker);
    if (assignmentData) {
      this.assignmentData.set(vehicleId, assignmentData);
    }

    // 设置初始状态
    const vehicle = this.vehicles.find(v => v.id === vehicleId);
    if (vehicle && vehicle.status) {
      this.updateVehicleIcon(vehicleId, vehicle.status);
    }
  }

  /**
   * 更新车辆状态（核心方法）
   */
  updateVehicleStatus(vehicleId, status, additionalData = {}) {
    console.log(`[VehicleStatusManager] 更新车辆状态: ${vehicleId} -> ${status}`, additionalData);

    // 1. 更新车辆列表中的状态
    const vehicleIndex = this.vehicles.findIndex(v => v.id === vehicleId);
    if (vehicleIndex !== -1) {
      const vehicle = this.vehicles[vehicleIndex];
      const oldStatus = vehicle.status;

      // 保存旧状态用于回调
      vehicle.previousStatus = oldStatus;
      vehicle.status = status;

      // 2. 更新载重信息
      this.updateVehicleLoadInfo(vehicle, status, additionalData);

      // 3. 更新位置信息（如果有提供）
      if (additionalData.position) {
        vehicle.currentLongitude = additionalData.position[0];
        vehicle.currentLatitude = additionalData.position[1];
      }

      // 4. 更新车辆图标
      this.updateVehicleIcon(vehicleId, status);

      // 5. 触发状态变化回调
      this.triggerStatusChange(vehicleId, oldStatus, status, vehicle);

      // 6. 如果是到达终点后的状态变更，同步到后端
      if (status === 'WAITING' || status === 'IDLE') {
        this.syncStatusToBackend(vehicleId, vehicle);
      }

      console.log(`[VehicleStatusManager] 车辆 ${vehicle.licensePlate} 状态已更新: ${oldStatus} -> ${status}`);
      return true;
    } else {
      console.warn(`[VehicleStatusManager] 车辆ID ${vehicleId} 未找到`);
      return false;
    }
  }

  /**
   * 同步车辆状态到后端
   */
  async syncStatusToBackend(vehicleId, vehicle) {
    try {
      await request.post('/api/vehicles/update-status', {
        vehicleId: vehicleId,
        status: vehicle.status,
        currentLoad: vehicle.currentLoad || 0,
        currentVolume: vehicle.currentVolume || 0,
        actionDescription: vehicle.actionDescription || '等待任务'
      });
      console.log(`[VehicleStatusManager] 车辆 ${vehicle.licensePlate} 状态已同步到后端`);
    } catch (error) {
      console.error(`[VehicleStatusManager] 同步车辆状态失败: ${vehicleId}`, error);
    }
  }

  /**
   * 更新车辆载重信息
   */
  updateVehicleLoadInfo(vehicle, status, data) {
    const assignment = data.assignment || this.assignmentData.get(vehicle.id);

    switch (status) {
      case 'ORDER_DRIVING':
        // 前往装货点：空车
        vehicle.currentLoad = 0;
        vehicle.currentVolume = 0;
        vehicle.loadPercentage = 0;
        vehicle.volumePercentage = 0;
        vehicle.actionDescription = `前往装货点: ${assignment?.startPOIName || '未知'}`;
        break;

      case 'LOADING':
        // 装货中：载重逐渐增加
        vehicle.actionDescription = `正在装货...`;
        // 在实际应用中，这里可以模拟装货过程
        break;

      case 'TRANSPORT_DRIVING':
        // 运输中：满载
        if (assignment) {
          vehicle.currentLoad = assignment.currentLoad || 0;
          vehicle.currentVolume = assignment.currentVolume || 0;

          if (vehicle.maxLoadCapacity > 0) {
            vehicle.loadPercentage = Math.min(100,
                (vehicle.currentLoad / vehicle.maxLoadCapacity) * 100);
          }

          if (vehicle.maxVolumeCapacity > 0) {
            vehicle.volumePercentage = Math.min(100,
                (vehicle.currentVolume / vehicle.maxVolumeCapacity) * 100);
          }
        }
        vehicle.actionDescription = `运输至: ${assignment?.endPOIName || '未知'}`;
        break;

      case 'UNLOADING':
        // 卸货中：载重逐渐减少
        vehicle.actionDescription = `正在卸货...`;
        break;

      case 'WAITING':
      case 'IDLE':
        // 等待/空闲：空车
        vehicle.currentLoad = 0;
        vehicle.currentVolume = 0;
        vehicle.loadPercentage = 0;
        vehicle.volumePercentage = 0;
        vehicle.actionDescription = '等待任务';
        break;

      case 'BREAKDOWN':
        // 故障：保持当前载重
        vehicle.actionDescription = '车辆故障';
        break;
    }
  }

  /**
   * 更新车辆图标
   */
  updateVehicleIcon(vehicleId, status) {
    const marker = this.vehicleMarkers.get(vehicleId);
    if (!marker) {
      console.warn(`[VehicleStatusManager] 车辆ID ${vehicleId} 的标记未找到`);
      return;
    }

    // 获取车辆信息以确定颜色
    const vehicle = this.vehicles.find(v => v.id === vehicleId);
    let color = null;

    // 使用状态映射中的颜色，如果没有则使用车辆默认颜色
    const statusColors = {
      'IDLE': '#95a5a6',
      'ORDER_DRIVING': '#3498db',
      'LOADING': '#f39c12',
      'TRANSPORT_DRIVING': '#2ecc71',
      'UNLOADING': '#e74c3c',
      'WAITING': '#e74c3c',
      'BREAKDOWN': '#e74c3c'
    };

    color = statusColors[status] || (vehicle?.color || '#ff7f50');

    // 创建新图标
    const newIcon = createVehicleIcon(32, status, color);

    // 更新标记
    marker.setContent(newIcon);

    // 更新标记标题
    const newTitle = `${vehicle?.licensePlate || '车辆'} - ${this.getStatusText(status)}`;
    marker.setTitle(newTitle);

    console.log(`[VehicleStatusManager] 车辆ID ${vehicleId} 图标已更新: ${status}`);
  }

  /**
   * 获取状态文本描述
   */
  getStatusText(status) {
    const statusMap = {
      'IDLE': '空闲',
      'ORDER_DRIVING': '前往装货点',
      'LOADING': '装货中',
      'TRANSPORT_DRIVING': '运输中',
      'UNLOADING': '卸货中',
      'WAITING': '等待中',
      'BREAKDOWN': '故障'
    };
    return statusMap[status] || status;
  }

  /**
   * 添加状态变化回调
   */
  onStatusChange(callback) {
    this.statusCallbacks.push(callback);
  }

  /**
   * 触发状态变化回调
   */
  triggerStatusChange(vehicleId, oldStatus, newStatus, vehicle) {
    this.statusCallbacks.forEach(callback => {
      try {
        callback(vehicleId, oldStatus, newStatus, vehicle);
      } catch (error) {
        console.error('状态变化回调执行失败:', error);
      }
    });
  }

  /**
   * 获取车辆当前状态
   */
  getVehicleStatus(vehicleId) {
    const vehicle = this.vehicles.find(v => v.id === vehicleId);
    return vehicle?.status || 'UNKNOWN';
  }

  /**
   * 获取车辆详细信息（包含位置和载重）
   */
  getVehicleInfo(vehicleId) {
    const vehicle = this.vehicles.find(v => v.id === vehicleId);
    if (!vehicle) return null;

    const assignment = this.assignmentData.get(vehicleId);
    return {
      ...vehicle,
      assignment,
      statusText: this.getStatusText(vehicle.status),
      position: vehicle.currentLongitude && vehicle.currentLatitude ?
          [vehicle.currentLongitude, vehicle.currentLatitude] : null
    };
  }

  /**
   * 清理资源
   */
  cleanup() {
    this.vehicleMarkers.clear();
    this.assignmentData.clear();
    this.statusCallbacks = [];
  }
}

// ==================== 车辆动画类 ====================
class VehicleAnimation {
  constructor(assignment, routeData, statusManager) {
    this.assignmentId = assignment.assignmentId;
    this.vehicleId = assignment.vehicleId;
    this.licensePlate = assignment.licensePlate;
    this.statusManager = statusManager; // 添加状态管理器引用
    this.manager = routeData.manager;
    this.routeData = routeData;

    // 动画状态
    this.isPaused = false;
    this.isCompleted = false;
    this.currentStage = 1; // 1: 前往装货点, 2: 运输到卸货点
    this.currentProgress = 0;
    this.currentSegment = 0;
    this.currentPosition = null;

    // 时间控制
    this.realStartTime = null;
    this.realPausedTime = 0;
    this.animationTime = 0;
    this.speedFactor = 1;
    this.lastUpdateTime = null;

    // 路线数据
    this.stage1Path = routeData.stage1Path || [];
    this.stage2Path = routeData.stage2Path || [];
    this.stage1Segments = this._calculateSegments(this.stage1Path);
    this.stage2Segments = this._calculateSegments(this.stage2Path);

    // 基础速度
    this.baseSpeed = 900; // 米/秒

    // 标记引用
    this.movingMarker = routeData.movingMarker;
    this.startMarker = routeData.startMarker;
    this.vehicleStartMarker = routeData.vehicleMarker;

    // 动画帧ID
    this.animationFrameId = null;

    // 完成回调
    this.onCompleteCallbacks = [];

    // 偏移
    this.offset = this._generateRandomOffset();

    console.log(`[VehicleAnimation] 创建车辆动画: ${this.licensePlate} (${this.assignmentId})`);

    // 初始化车辆状态为 ORDER_DRIVING
    if (this.statusManager) {
      this.statusManager.updateVehicleStatus(this.vehicleId, 'ORDER_DRIVING', {
        assignment: assignment,
        position: this.stage1Path[0]
      });
    }
  }

  // 计算路段的长度和累积距离
  _calculateSegments(path) {
    if (!path || path.length < 2) return { segments: [], totalLength: 0 };

    const segments = [];
    let cumulativeLength = 0;

    for (let i = 0; i < path.length - 1; i++) {
      const start = path[i];
      const end = path[i + 1];
      const length = this._haversineDistance(start, end);
      segments.push({
        start,
        end,
        length,
        cumulativeLength
      });
      cumulativeLength += length;
    }

    return { segments, totalLength: cumulativeLength };
  }

  // 球面距离计算
  _haversineDistance(a, b) {
    const toRad = d => d * Math.PI / 180;
    const R = 6371000;
    const dLat = toRad(b[1] - a[1]);
    const dLon = toRad(b[0] - a[0]);
    const lat1 = toRad(a[1]), lat2 = toRad(b[1]);
    const sinDLat = Math.sin(dLat/2), sinDLon = Math.sin(dLon/2);
    const c = 2 * Math.asin(Math.sqrt(sinDLat*sinDLat + Math.cos(lat1)*Math.cos(lat2)*sinDLon*sinDLon));
    return R * c;
  }

  // 生成随机偏移（避免图标重叠）
  _generateRandomOffset() {
    const angle = Math.random() * Math.PI * 2;
    const radius = 8; // 像素偏移半径
    return {
      x: Math.cos(angle) * radius,
      y: Math.sin(angle) * radius
    };
  }

  // 开始动画
  start() {
    if (this.isCompleted) return;

    const now = performance.now();

    if (this.realStartTime === null) {
      this.realStartTime = now;
      this.animationTime = 0;
    } else if (this.isPaused) {
      const pauseDuration = now - this.realPausedTime;
      this.realStartTime += pauseDuration;
    }

    this.isPaused = false;
    this.lastUpdateTime = now;

    // 设置初始位置
    if (this.stage1Path && this.stage1Path.length > 0 && !this.currentPosition) {
      this.currentPosition = [...this.stage1Path[0]];
      this._updateMarkerPosition();

      // 更新状态管理器中的位置
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId, 'ORDER_DRIVING', {
          assignment: this.routeData.assignment,
          position: this.currentPosition
        });
      }
    }

    this._animate();
    console.log(`[VehicleAnimation] 开始车辆动画: ${this.licensePlate}`);
  }

  // 暂停动画
  pause() {
    if (this.isPaused || this.isCompleted) return;

    this.isPaused = true;
    this.realPausedTime = performance.now();

    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }

    console.log(`[VehicleAnimation] 暂停车辆动画: ${this.licensePlate}`);
  }

  // 恢复动画
  resume() {
    if (!this.isPaused || this.isCompleted) return;

    this.isPaused = false;
    this.lastUpdateTime = performance.now();

    this._animate();
    console.log(`[VehicleAnimation] 恢复车辆动画: ${this.licensePlate}`);
  }

  // 停止动画
  stop() {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }

    this.isCompleted = true;
    this.isPaused = false;

    console.log(`[VehicleAnimation] 停止车辆动画: ${this.licensePlate}`);
  }

  // 更新速度因子
  updateSpeedFactor(speedFactor) {
    const now = performance.now();

    if (this.lastUpdateTime && !this.isPaused && !this.isCompleted) {
      const delta = (now - this.lastUpdateTime) / 1000;
      this.animationTime += delta * this.speedFactor;
    }

    this.speedFactor = speedFactor;
    this.lastUpdateTime = now;

    console.log(`[VehicleAnimation] 更新车辆速度因子: ${this.licensePlate} -> ${speedFactor.toFixed(1)}x`);
  }

  // 获取当前路径
  _getCurrentPath() {
    return this.currentStage === 1 ? this.stage1Path : this.stage2Path;
  }

  // 获取当前路段数据
  _getCurrentSegments() {
    return this.currentStage === 1 ? this.stage1Segments : this.stage2Segments;
  }

  // 根据距离获取位置
  _getPositionByDistance(distance, path, segments) {
    if (!path || path.length < 2) return path[0] || [0, 0];

    const { segments: segs, totalLength } = segments;

    // 如果距离小于等于0，返回起点
    if (distance <= 0) return [...path[0]];

    // 如果距离大于等于总长度，返回终点
    if (distance >= totalLength) return [...path[path.length - 1]];

    // 找到当前所在的路段
    for (let i = 0; i < segs.length; i++) {
      const seg = segs[i];
      const segmentEnd = seg.cumulativeLength + seg.length;

      if (distance >= seg.cumulativeLength && distance <= segmentEnd) {
        const segmentProgress = (distance - seg.cumulativeLength) / seg.length;

        // 线性插值计算位置
        const lng = seg.start[0] + (seg.end[0] - seg.start[0]) * segmentProgress;
        const lat = seg.start[1] + (seg.end[1] - seg.start[1]) * segmentProgress;

        return [lng, lat];
      }
    }

    // 默认返回终点
    return [...path[path.length - 1]];
  }

  // 更新标记位置
  _updateMarkerPosition() {
    if (!this.movingMarker || !this.currentPosition) return;

    try {
      const positionWithOffset = [
        this.currentPosition[0],
        this.currentPosition[1]
      ];

      this.movingMarker.setPosition(positionWithOffset);

      // 更新状态管理器中的位置
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId,
            this.currentStage === 1 ? 'ORDER_DRIVING' : 'TRANSPORT_DRIVING', {
              assignment: this.routeData.assignment,
              position: this.currentPosition
            });
      }
    } catch (error) {
      console.warn(`[VehicleAnimation] 更新车辆标记位置失败: ${this.licensePlate}`, error);
    }
  }

  // 动画主循环
  _animate() {
    if (this.isPaused || this.isCompleted) {
      this.animationFrameId = null;
      return;
    }

    const now = performance.now();

    if (this.lastUpdateTime === null) {
      this.lastUpdateTime = now;
    }

    const deltaTime = (now - this.lastUpdateTime) / 1000;
    this.animationTime += deltaTime * this.speedFactor;
    this.lastUpdateTime = now;

    const currentSegments = this._getCurrentSegments();
    const currentPath = this._getCurrentPath();

    if (!currentPath || currentPath.length < 2 || currentSegments.segments.length === 0) {
      console.error(`[VehicleAnimation] 无效的路径数据: ${this.licensePlate}`);
      this.stop();
      return;
    }

    const distance = this.animationTime * this.baseSpeed;
    const totalLength = currentSegments.totalLength;

    if (distance >= totalLength) {
      this._completeCurrentStage();
    } else {
      this.currentPosition = this._getPositionByDistance(distance, currentPath, currentSegments);
      this.currentProgress = distance / totalLength;

      this._updateMarkerPosition();
      this.animationFrameId = requestAnimationFrame(() => this._animate());
    }
  }

  // 完成当前阶段（修改版）
  async _completeCurrentStage() {
    const currentPath = this._getCurrentPath();
    if (currentPath && currentPath.length > 0) {
      this.currentPosition = [...currentPath[currentPath.length - 1]];
      this._updateMarkerPosition();
    }

    if (this.currentStage === 1) {
      // 第一阶段完成：到达装货点
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId, 'LOADING', {
          assignment: this.routeData.assignment,
          position: this.currentPosition
        });
      }

      // 装货停留2秒（动画时间）
      console.log(`[VehicleAnimation] ${this.licensePlate} 开始装货...`);
      await this._waitWithSpeedFactor(2000);

      // 切换到第二阶段
      this.currentStage = 2;
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId, 'TRANSPORT_DRIVING', {
          assignment: this.routeData.assignment,
          position: this.currentPosition,
          isLoaded: true
        });
      }

      // 重置时间，开始第二阶段
      this.animationTime = 0;
      this.lastUpdateTime = performance.now();
      this.currentProgress = 0;

      console.log(`[VehicleAnimation] ${this.licensePlate} 装货完成，开始运输...`);
      this._animate();

    } else if (this.currentStage === 2) {
      // 第二阶段完成：到达卸货点
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId, 'UNLOADING', {
          assignment: this.routeData.assignment,
          position: this.currentPosition,
          isLoaded: true
        });
      }

      // 卸货停留2秒（动画时间）
      console.log(`[VehicleAnimation] ${this.licensePlate} 开始卸货...`);
      await this._waitWithSpeedFactor(2000);
      // ToDo
      // 完成任务
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId, 'IDLE', {
          assignment: {
            ...this.routeData.assignment,
            currentLoad: 0,  // 明确设置载重为0
            currentVolume: 0, // 明确设置载容为0
            isUnloading: true  // 标记为卸货完成状态
          },
          position: this.currentPosition,
          isLoaded: false
        });
      }

      // 标记为完成
      this.isCompleted = true;

      // 调用车辆到达处理函数
      await handleVehicleArrived(this.assignmentId, this.vehicleId,
          this.routeData.assignment.endPOIId, this.licensePlate);

      // 延迟清理（1-2秒后）
      setTimeout(() => {
        this.cleanup();
        this.manager.removeAnimation(this.assignmentId);
      }, 1000 + Math.random() * 1000);

      // 触发完成回调
      this.onCompleteCallbacks.forEach(callback => callback(this));

      console.log(`[VehicleAnimation] ${this.licensePlate} 卸货完成，任务结束`);
    }
  }

  // 考虑速度因子的等待
  async _waitWithSpeedFactor(ms) {
    const adjustedMs = ms / this.speedFactor;
    return new Promise(resolve => setTimeout(resolve, adjustedMs));
  }

  // 清理资源
  cleanup() {
    // 清理车辆移动标记
    if (this.movingMarker && map) {
      try {
        map.remove(this.movingMarker);
      } catch (error) {
        // 忽略清理错误
      }
    }

    console.log(`[VehicleAnimation] 清理车辆资源: ${this.licensePlate}`);
  }

  // 添加完成回调
  onComplete(callback) {
    this.onCompleteCallbacks.push(callback);
  }
}

// ==================== 车辆动画管理器类 ====================
class VehicleAnimationManager {
  constructor(statusManager = null) {
    this.animations = new Map();
    this.globalSpeedFactor = 1;
    this.isPaused = false;
    this.vehicleColors = [
      '#ff7f50', '#3498db', '#2ecc71', '#e74c3c', '#9b59b6',
      '#1abc9c', '#d35400', '#c0392b', '#16a085', '#8e44ad'
    ];
    this.statusManager = statusManager; // 添加状态管理器引用
  }

  // 添加动画
  addAnimation(assignment, routeData) {
    if (this.animations.has(assignment.assignmentId)) {
      console.warn(`[VehicleAnimationManager] 动画已存在: ${assignment.assignmentId}`);
      return this.animations.get(assignment.assignmentId);
    }

    // 为车辆分配颜色（基于车辆ID）
    const colorIndex = assignment.vehicleId % this.vehicleColors.length;
    routeData.color = this.vehicleColors[colorIndex];

    // 创建动画实例，传入状态管理器
    const animation = new VehicleAnimation(assignment, routeData, this.statusManager);
    this.animations.set(assignment.assignmentId, animation);

    // 设置初始速度因子
    animation.updateSpeedFactor(this.globalSpeedFactor);

    // 如果全局未暂停，则启动动画
    if (!this.isPaused) {
      animation.start();
    }

    return animation;
  }

  // 开始所有动画
  startAll() {
    this.isPaused = false;
    this.animations.forEach(animation => {
      if (!animation.isCompleted) {
        animation.start();
      }
    });
    console.log(`[VehicleAnimationManager] 开始所有动画，共 ${this.animations.size} 个`);
  }

  // 暂停所有动画
  pauseAll() {
    this.isPaused = true;
    this.animations.forEach(animation => {
      if (!animation.isCompleted && !animation.isPaused) {
        animation.pause();
      }
    });
    console.log(`[VehicleAnimationManager] 暂停所有动画，共 ${this.animations.size} 个`);
  }

  // 恢复所有动画
  resumeAll() {
    this.isPaused = false;
    this.animations.forEach(animation => {
      if (!animation.isCompleted && animation.isPaused) {
        animation.resume();
      }
    });
    console.log(`[VehicleAnimationManager] 恢复所有动画，共 ${this.animations.size} 个`);
  }

  // 停止所有动画
  stopAll() {
    this.animations.forEach(animation => {
      animation.stop();
      animation.cleanup();
    });
    this.animations.clear();
    console.log('[VehicleAnimationManager] 停止所有动画并清理资源');
  }

  // 设置全局速度因子
  setGlobalSpeedFactor(factor) {
    this.globalSpeedFactor = Math.max(0.1, Math.min(10, factor));
    this.animations.forEach(animation => {
      animation.updateSpeedFactor(this.globalSpeedFactor);
    });
    console.log(`[VehicleAnimationManager] 设置全局速度因子: ${this.globalSpeedFactor}`);
  }

  // 移除动画
  removeAnimation(assignmentId) {
    const animation = this.animations.get(assignmentId);
    if (animation) {
      animation.stop();
      animation.cleanup();
      this.animations.delete(assignmentId);
      console.log(`[VehicleAnimationManager] 移除动画: ${assignmentId}`);
    }
  }

  // 更新车辆状态（委托给状态管理器）
  updateVehicleStatus(vehicleId, status) {
    if (this.statusManager) {
      this.statusManager.updateVehicleStatus(vehicleId, status);
    } else {
      console.log(`[VehicleAnimationManager] 更新车辆状态: ${vehicleId} -> ${status}`);
    }
  }

  // 获取活动动画数量
  getActiveCount() {
    return this.animations.size;
  }

  // 检查是否有动画
  hasAnimations() {
    return this.animations.size > 0;
  }
}

// 初始化动画管理器
let animationManager = null;

// 状态管理器引用
const vehicleStatusManager = ref(null);

// --- 车辆到达处理函数 ---
const handleVehicleArrived = async (assignmentId, vehicleId, endPOIId, licensePlate) => {
  try {
    console.log(`处理车辆到达: ${licensePlate} (Assignment: ${assignmentId})`);

    // 1. 立即更新车辆状态为 WAITING，载重归零
    if (vehicleStatusManager.value) {
      vehicleStatusManager.value.updateVehicleStatus(vehicleId, 'WAITING', {
        assignment: {
          currentLoad: 0,
          currentVolume: 0
        },
        position: null,
        isLoaded: false
      });
    } else {
      // 如果状态管理器未初始化，直接更新车辆列表
      const vehicleIndex = vehicles.findIndex(v => v.id === vehicleId);
      if (vehicleIndex !== -1) {
        const vehicle = vehicles[vehicleIndex];
        vehicle.status = 'WAITING';
        vehicle.currentLoad = 0;
        vehicle.currentVolume = 0;
        vehicle.loadPercentage = 0;
        vehicle.volumePercentage = 0;
        vehicle.actionDescription = '等待任务';
        console.log(`车辆 ${licensePlate} 载重已归零`);
      }
    }

    // 1. 调用车辆到达接口
    await request.post('/api/simulation/vehicle-arrived', {
      assignmentId: assignmentId,
      vehicleId: vehicleId,
      endPOIId: endPOIId,
      // 明确传递车辆状态和载重信息
      vehicleStatus: 'WAITING',
      currentLoad: 0,
      currentVolume: 0
    });

    console.log(`车辆 ${licensePlate} 到达处理完成`);

    // 2. 立即更新车辆状态为 WAITING，载重归零
    if (vehicleStatusManager.value) {
      vehicleStatusManager.value.updateVehicleStatus(vehicleId, 'IDLE', {
        assignment: {
          currentLoad: 0,
          currentVolume: 0
        },
        position: null,
        isLoaded: false
      });
    } else {
      // 如果状态管理器未初始化，直接更新车辆列表
      const vehicleIndex = vehicles.findIndex(v => v.id === vehicleId);
      if (vehicleIndex !== -1) {
        const vehicle = vehicles[vehicleIndex];
        vehicle.status = 'IDLE';
        vehicle.currentLoad = 0;
        vehicle.currentVolume = 0;
        vehicle.loadPercentage = 0;
        vehicle.volumePercentage = 0;
        vehicle.actionDescription = '等待任务';
        console.log(`车辆 ${licensePlate} 载重已归零`);
      }
    }
    // 4. 清理前端动画和路线
    clearRouteByAssignmentId(assignmentId);

    // 2. 等待后端处理完成
    setTimeout(async () => {
      await updateVehicleInfo();
      console.log(`车辆 ${licensePlate} 状态已刷新`);
    }, 2500);
  } catch (error) {
    console.error('车辆到达处理失败:', error);
    ElMessage.error(`车辆 ${licensePlate} 状态更新失败: ${error.message}`);
  }
};

// --- 核心仿真方法 ---

/**
 * 启动仿真
 */
const startSimulation = async () => {
  try {
    console.log("开始仿真");

    // 启动后端仿真
    await simulationController.startSimulation();
    isSimulationRunning.value = true;

    await new Promise(resolve => setTimeout(resolve, 2500));

    // 启动动画管理器
    if (animationManager.hasAnimations()) {
      // 有现有动画，恢复它们
      animationManager.resumeAll();
      console.log("恢复现有动画");
    } else {
      // 没有动画，重新加载Assignment
      console.log("重新加载Assignment");

      // 启动动画管理器
      animationManager.startAll();

      // 初始加载当前活跃的Assignment
      await fetchCurrentAssignments();
    }

    // 启动定时更新
    startSimulationTimer();

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
const pauseSimulation = async () => {
  try {
    console.log("已暂停仿真");

    // 暂停动画管理器
    animationManager.pauseAll();

    // 暂停后端仿真
    await simulationController.stopSimulation();
    isSimulationRunning.value = false;

    ElMessage.success('仿真已暂停');
  } catch (error) {
    console.error("暂停仿真失败：", error);
    ElMessage.error('暂停仿真失败：' + error.message);
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

      // 停止后端仿真
      await simulationController.resetSimulation();
      isSimulationRunning.value = false;

      // 停止定时器
      stopSimulationTimer();

      // 停止并清理所有动画
      animationManager.stopAll();

      // 清除缓存
      routePlanningCache.clear();
      assignmentStates.clear();

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
      vehicles.splice(0, vehicles.length);

      // 重置统计信息
      stats.running = 0;
      stats.poiCount = 0;
      stats.tasks = 0;
      stats.anomalyRate = 0;

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
 * 更新POI数据
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
    // console.error("更新POI数据失败：", error);
    // ElMessage.error('更新POI数据失败');
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
  ORDER_DRIVING: { text: '前往装货点', color: '#3498db' },
  LOADING: { text: '装货中', color: '#f39c12' },
  TRANSPORT_DRIVING: { text: '运输中', color: '#2ecc71' },
  UNLOADING: { text: '卸货中', color: '#e74c3c' },
  WAITING: { text: '等待中', color: '#e74c3c' },
  BREAKDOWN: { text: '故障', color: '#e74c3c' },
};

const vehicles = reactive([]); // 车辆列表，将从Assignment中获取

// 更新车辆信息的方法
const updateVehicleInfo = async () => {
  try {
    const response = await request.get('/api/assignments/active');
    const activeAssignments = response.data;

    const positionsResponse = await request.get('/api/vehicles/current-positions');
    const vehiclePositions = positionsResponse.data;

    // 记录当前已有的车辆状态（避免覆盖）
    const currentVehicleStates = new Map();
    vehicles.forEach(v => {
      currentVehicleStates.set(v.id, {
        status: v.status,
        actionDescription: v.actionDescription
      });
    });

    vehicles.splice(0, vehicles.length);

    const vehicleMap = new Map();

    activeAssignments.forEach(assignment => {
      if (assignment.vehicleId && assignment.licensePlate) {
        // 检查车辆是否有特殊状态（如刚完成运输）
        const currentState = currentVehicleStates.get(assignment.vehicleId);
        let vehicleStatus = assignment.vehicleStatus || 'ORDER_DRIVING';

        // 如果车辆之前的状态是完成任务的状态，优先使用之前的状态
        if (currentState &&
            (currentState.status === 'IDLE' ||
                currentState.status === 'UNLOADING_COMPLETE' ||
                currentState.status === 'WAITING')) {
          vehicleStatus = currentState.status;
        }

        const vehicle = {
          id: assignment.vehicleId,
          licensePlate: assignment.licensePlate,
          status: vehicleStatus, // 使用合并后的状态
          assignments: [{
            id: assignment.assignmentId,
            routeName: assignment.routeName,
            goodsName: assignment.goodsName,
            quantity: assignment.quantity
          }],
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

        // 如果车辆状态是空闲，但还有assignment，这可能是一个bug
        if (vehicleStatus === 'IDLE' && assignment.status === 'ACTIVE') {
          console.warn(`车辆 ${vehicle.licensePlate} 状态冲突: 前端IDLE, 后端ACTIVE`);
          // 可以选择信任后端状态
          vehicle.status = 'IDLE';
          vehicle.currentLoad = 0.0;
        }

        vehicleMap.set(assignment.vehicleId, vehicle);
      }
    });

    // 添加没有活跃assignment但可能存在的车辆（如空闲车辆）
    // 这需要后端提供专门的空闲车辆接口
    try {
      const idleVehiclesResponse = await request.get('/api/vehicles/idle');
      const idleVehicles = idleVehiclesResponse.data;

      idleVehicles.forEach(vehicle => {
        if (!vehicleMap.has(vehicle.id)) {
          vehicleMap.set(vehicle.id, {
            ...vehicle,
            status: 'IDLE',
            actionDescription: '空闲',
            currentLoad: 0,
            currentVolume: 0,
            loadPercentage: 0,
            volumePercentage: 0
          });
        }
      });
    } catch (error) {
      console.log('获取空闲车辆失败，可能接口不存在:', error.message);
    }

    vehicleMap.forEach(vehicle => {
      vehicles.push(vehicle);
    });

  } catch (error) {
    // console.error('获取车辆信息失败:', error);
    // ElMessage.error('获取车辆信息失败');
  }
};

// 同步车辆状态到后端
const syncVehicleStatusToBackend = async (vehicleId, status, loadInfo = {}) => {
  try {
    await request.post('/api/vehicles/update-status', {
      vehicleId: vehicleId,
      status: status,
      currentLoad: loadInfo.currentLoad || 0,
      currentVolume: loadInfo.currentVolume || 0,
      actionDescription: loadInfo.actionDescription || '等待任务'
    });
    console.log(`车辆 ${vehicleId} 状态已同步到后端: ${status}`);
  } catch (error) {
    console.error(`同步车辆 ${vehicleId} 状态到后端失败:`, error);
  }
};

// 获取车辆详细信息
const getVehicleDetail = async (vehicleId) => {
  try {
    const response = await request.get(`/api/vehicles/${vehicleId}`);
    return response.data;
  } catch (error) {
    // console.error(`获取车辆${vehicleId}详细信息失败:`, error);
    // ElMessage.error(`获取车辆信息失败: ${vehicleId}`);
    return null;
  }
};

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

// 创建车辆图标（支持颜色和状态区分）
const createVehicleIcon = (size = 32, status = 'IDLE', color = null) => {
  const el = document.createElement('div');
  el.style.width = `${size}px`;
  el.style.height = `${size}px`;
  el.style.borderRadius = '50%';
  el.style.display = 'flex';
  el.style.alignItems = 'center';
  el.style.justifyContent = 'center';
  el.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)';
  el.style.border = '2px solid white';

  // 状态颜色映射
  const statusColors = {
    'IDLE': '#95a5a6',
    'ORDER_DRIVING': '#3498db',
    'LOADING': '#f39c12',
    'TRANSPORT_DRIVING': '#2ecc71',
    'UNLOADING': '#e74c3c',
    'WAITING': '#e74c3c',
    'BREAKDOWN': '#e74c3c'
  };

  // 设置背景颜色
  const bgColor = color || statusColors[status] || '#ff7f50';
  el.style.background = bgColor;
  el.style.color = '#fff';

  // 根据状态生成不同的SVG图标
  let svgContent = '';
  const iconSize = Math.round(size * 0.6);

  switch (status) {
    case 'ORDER_DRIVING':
      // 空车图标（灰色或蓝色）
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
        </svg>`;
      break;

    case 'TRANSPORT_DRIVING':
      // 载货车图标（显示货物）
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
          <rect x="4" y="4" width="8" height="2" rx="0.5" fill="#ffeb3b"></rect>
        </svg>`;
      break;

    case 'LOADING':
      // 装载中图标（带加载动画效果）
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
          <path d="M8 10v-4" stroke-dasharray="2,2"></path>
        </svg>`;
      break;

    case 'UNLOADING':
      // 卸货中图标
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
          <path d="M12 10v4" stroke-dasharray="2,2"></path>
        </svg>`;
      break;

    default:
      // 默认车辆图标
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
          <path d="M3 13v-6h11v6H3zm13 0h3l2 3v3h-3a2 2 0 0 1-2-2v-4zM6 18a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm10 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z"/>
        </svg>`;
  }

  el.innerHTML = svgContent;
  return el;
};

// 清除特定Assignment的路线
const clearRouteByAssignmentId = (assignmentId) => {
  const routeData = activeRoutes.value.get(assignmentId);
  if (routeData) {
    // 清理动画
    if (animationManager) {
      animationManager.removeAnimation(assignmentId);
    }

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

// 获取当前活跃的Assignment（用于初始加载）
const fetchCurrentAssignments = async () => {
  try {
    const response = await request.get('/api/assignments/active');
    const assignments = response.data;

    if (assignments && assignments.length > 0) {
      // 为每个Assignment绘制两段路线
      for (const assignment of assignments) {
        if (assignment && assignment.assignmentId) {
          // 检查是否已有动画
          if (!animationManager.animations.has(assignment.assignmentId)) {
            await drawTwoStageRouteForAssignment(assignment);
            drawnAssignmentIds.value.add(assignment.assignmentId);
          }
        }
      }

      // 更新统计信息
      stats.tasks = drawnAssignmentIds.value.size;
    }
  } catch (error) {
    // console.error('获取当前Assignment失败:', error);
    // ElMessage.error('获取当前任务失败');
  }
};

// 增量获取并绘制新Assignment
const fetchAndDrawNewAssignments = async () => {
  try {
    const response = await request.get('/api/assignments/new');
    const newAssignments = response.data;

    if (!newAssignments || newAssignments.length === 0) {
      console.log('没有新增的Assignment');
      return;
    }

    console.log(`获取到 ${newAssignments.length} 个新增Assignment`);

    // 绘制新路线
    for (const assignment of newAssignments) {
      if (assignment && assignment.assignmentId) {
        if (!drawnAssignmentIds.value.has(assignment.assignmentId)) {
          await drawTwoStageRouteForAssignment(assignment);

          drawnAssignmentIds.value.add(assignment.assignmentId);

          try {
            await request.post(`/api/assignments/mark-drawn/${assignment.assignmentId}`);
          } catch (error) {
            // console.error(`标记Assignment ${assignment.assignmentId} 为已绘制失败:`, error);
            // ElMessage.error(`标记任务 ${assignment.assignmentId} 失败`);
          }
        }
      }
    }

    stats.tasks = drawnAssignmentIds.value.size;

  } catch (error) {
    // console.error('获取并绘制新增Assignment失败:', error);
    // ElMessage.error('获取新增任务失败');
  }
};

// 为Assignment绘制两段路线（修复版）
const drawTwoStageRouteForAssignment = async (assignment) => {
  if (!AMapLib || !map) return null;

  try {
    // 检查是否已有该Assignment的路线数据
    if (activeRoutes.value.has(assignment.assignmentId)) {
      console.log(`Assignment ${assignment.assignmentId} 已有路线数据，跳过绘制`);
      return activeRoutes.value.get(assignment.assignmentId);
    }

    // 检查坐标有效性
    if (!isValidCoordinate(assignment.vehicleStartLng, assignment.vehicleStartLat) ||
        !isValidCoordinate(assignment.startLng, assignment.startLat) ||
        !isValidCoordinate(assignment.endLng, assignment.endLat)) {
      console.warn(`Assignment ${assignment.assignmentId} 坐标无效，跳过`);
      return null;
    }

    // 规划两段路线
    const stage1Route = await computeSingleRouteWithCache(
        [assignment.vehicleStartLng, assignment.vehicleStartLat],
        [assignment.startLng, assignment.startLat],
        assignment.assignmentId + '_stage1'
    );

    const stage2Route = await computeSingleRouteWithCache(
        [assignment.startLng, assignment.startLat],
        [assignment.endLng, assignment.endLat],
        assignment.assignmentId + '_stage2'
    );

    if (!stage1Route || !stage2Route) {
      // console.error(`Assignment ${assignment.assignmentId} 路线规划失败`);
      // ElMessage.error(`任务 ${assignment.assignmentId} 路线规划失败`);
      return null;
    }

    const elements = [];

    // 绘制第一段路线（空驶阶段）
    const stage1Poly = new AMapLib.Polyline({
      path: stage1Route.path,
      strokeColor: '#95a5a6', // 灰色表示空驶
      strokeOpacity: 0.6,
      strokeWeight: 3,
      strokeDasharray: [5, 5], // 虚线
      lineJoin: 'round',
    });
    stage1Poly.setMap(map);
    elements.push(stage1Poly);

    // 绘制第二段路线（运输阶段）
    const stage2Poly = new AMapLib.Polyline({
      path: stage2Route.path,
      strokeColor: '#3388ff', // 蓝色表示运输
      strokeOpacity: 0.8,
      strokeWeight: 4,
      lineJoin: 'round',
    });
    stage2Poly.setMap(map);
    elements.push(stage2Poly);

    // 起点标记（装货点）
    const startMarker = new AMapLib.Marker({
      position: [assignment.startLng, assignment.startLat],
      title: `装货点: ${assignment.startPOIName || '未知'}`,
      icon: new AMapLib.Icon({
        image: factoryIcon,
        size: new AMapLib.Size(24, 24),
        imageSize: new AMapLib.Size(24, 24)
      })
    });
    startMarker.setMap(map);
    elements.push(startMarker);

    // 终点标记（卸货点）
    const endMarker = new AMapLib.Marker({
      position: [assignment.endLng, assignment.endLat],
      title: `卸货点: ${assignment.endPOIName || '未知'}`,
      icon: new AMapLib.Icon({
        image: materialMarketIcon,
        size: new AMapLib.Size(24, 24),
        imageSize: new AMapLib.Size(24, 24)
      })
    });
    endMarker.setMap(map);
    elements.push(endMarker);

    // 创建车辆移动标记
    const movingEl = createVehicleIcon(32, 'ORDER_DRIVING', '#ff7f50');
    const movingMarker = new AMapLib.Marker({
      position: stage1Route.path[0],
      content: movingEl,
      offset: new AMapLib.Pixel(-16, -16),
      title: `${assignment.goodsName || '货物'}运输 - ${assignment.licensePlate}`,
      extData: {
        type: 'vehicle',
        vehicleId: assignment.vehicleId,
        assignmentId: assignment.assignmentId,
        licensePlate: assignment.licensePlate,
        status: 'ORDER_DRIVING'
      }
    });
    movingMarker.setMap(map);
    elements.push(movingMarker);

    // 注册移动标记到状态管理器
    if (vehicleStatusManager.value) {
      vehicleStatusManager.value.registerVehicleMarker(
          assignment.vehicleId,
          movingMarker,
          assignment
      );
    }

    // 车辆信息窗口
    movingMarker.on('click', () => {
      showVehicleInfoWindowFromMarker(assignment, null);
    });

    // 构建路线数据对象
    const routeData = {
      assignment,
      stage1Path: stage1Route.path,
      stage2Path: stage2Route.path,
      movingMarker,
      startMarker,
      vehicleMarker: null,
      elements,
      animations: [],
      manager: animationManager, // 传递动画管理器引用
      cleanup: () => {
        routeData.animations.forEach(anim => {
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

        // 从状态管理器中移除标记
        if (vehicleStatusManager.value) {
          vehicleStatusManager.value.vehicleMarkers.delete(assignment.vehicleId);
          vehicleStatusManager.value.assignmentData.delete(assignment.vehicleId);
        }
      }
    };

    // 添加到活动路线映射
    activeRoutes.value.set(assignment.assignmentId, routeData);

    // 添加到动画管理器
    if (animationManager) {
      animationManager.addAnimation(assignment, routeData);
    }

    console.log(`成功绘制Assignment ${assignment.assignmentId} 的两段路线`);
    return routeData;

  } catch (e) {
    // console.error('绘制两段路线错误', e);
    // ElMessage.error(`绘制任务路线失败: ${assignment.assignmentId}`);
    return null;
  }
};

// 坐标有效性检查
const isValidCoordinate = (lng, lat) => {
  return lng !== null && lat !== null &&
      !isNaN(lng) && !isNaN(lat) &&
      lng >= -180 && lng <= 180 &&
      lat >= -90 && lat <= 90;
};

// 带缓存的路线规划
const computeSingleRouteWithCache = async (start, end, cacheKey) => {
  // 检查缓存
  if (routePlanningCache.has(cacheKey)) {
    console.log(`使用缓存的路线: ${cacheKey}`);
    return routePlanningCache.get(cacheKey);
  }

  // 规划新路线
  const route = await computeSingleRoute(start, end, '0');

  if (route) {
    // 缓存结果
    routePlanningCache.set(cacheKey, route);
  }

  return route;
};

// 处理车辆标记点击事件
const handleVehicleMarkerClick = async (assignment) => {
  console.log('点击车辆标记:', assignment);
  console.log('尝试滚动到车辆ID:', assignment.vehicleId);

  // 滚动到侧边栏对应车辆
  if (assignment.vehicleId) {
    scrollToVehicle(assignment.vehicleId);
  }

  try {
    // 获取车辆详细信息
    const vehicleDetail = await getVehicleDetail(assignment.vehicleId);

    // 合并车辆信息
    const vehicle = vehicles.find(v => v.id === assignment.vehicleId);
    if (vehicle) {
      selectedVehicle.value = {
        ...vehicle,
        ...vehicleDetail,
        // 确保有必要的属性
        currentLoad: vehicle.currentLoad || 0,
        currentVolume: vehicle.currentVolume || 0,
        maxLoadCapacity: vehicle.maxLoadCapacity || 0,
        maxVolumeCapacity: vehicle.maxVolumeCapacity || 0,
        loadPercentage: vehicle.loadPercentage || 0,
        volumePercentage: vehicle.volumePercentage || 0,
        actionDescription: vehicle.actionDescription || '',
        currentAssignment: vehicle.currentAssignment || assignment.routeName,
        goodsInfo: vehicle.goodsInfo || assignment.goodsName,
        quantity: vehicle.quantity || assignment.quantity,
        startPOI: vehicle.startPOI || assignment.startPOIName,
        endPOI: vehicle.endPOI || assignment.endPOIName,
        goodsWeightPerUnit: vehicle.goodsWeightPerUnit || 0,
        goodsVolumePerUnit: vehicle.goodsVolumePerUnit || 0
      };
      showVehicleDetail.value = true;
    }
  } catch (error) {
    console.error('获取车辆信息失败:', error);
    // 显示基本信息
    const vehicle = vehicles.find(v => v.id === assignment.vehicleId);
    if (vehicle) {
      selectedVehicle.value = vehicle;
      showVehicleDetail.value = true;
    }
  }
};

// 从标记点击显示车辆信息窗口
const showVehicleInfoWindowFromMarker = (assignment, vehicleDetail) => {
  handleVehicleMarkerClick(assignment);
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

// 计算单段路线
const computeSingleRoute = async (start, end, strategy = '0') => {
  try {
    const params = {
      startLon: String(start[0]),
      startLat: String(start[1]),
      endLon: String(end[0]),
      endLat: String(end[1]),
      strategy: strategy
    };

    const res = await request.get(
        '/api/routes/gaode/plan-by-coordinates',
        { params }
    );

    const response = res.data;

    if (!response.success) {
      // console.error(`路线规划失败:`, response.message);
      // ElMessage.error('路线规划失败');
      return null;
    }

    const gaodeData = response.data?.data;

    if (!gaodeData?.paths?.length) {
      console.error(`没有找到路径方案`);
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

    return {
      path: fullPath,
      start: fullPath[0] || start,
      end: fullPath[fullPath.length - 1] || end,
      distance: pathInfo.distance,
      duration: pathInfo.duration,
      speedMps: pathInfo.distance / pathInfo.duration
    };
  } catch (error) {
    // console.error('路线规划出错:', error);
    // ElMessage.error('路线规划出错');
    return null;
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
};

// --- 统计信息 ---
const runningVehicleCount = computed(() => {
  return vehicles.filter(v => v.status === 'running').length;
});

// 初始化状态管理器
const initVehicleStatusManager = () => {
  vehicleStatusManager.value = new VehicleStatusManager(vehicles, map);

  // 添加状态变化监听器
  vehicleStatusManager.value.onStatusChange((vehicleId, oldStatus, newStatus, vehicle) => {
    console.log(`[状态变化] 车辆 ${vehicle.licensePlate}: ${oldStatus} → ${newStatus}`);

    // 更新统计信息中的运行车辆数量
    stats.running = vehicles.filter(v =>
        v.status === 'ORDER_DRIVING' ||
        v.status === 'LOADING' ||
        v.status === 'TRANSPORT_DRIVING' ||
        v.status === 'UNLOADING'
    ).length;
  });
};

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

        // 初始化状态管理器
        initVehicleStatusManager();

        // 初始化动画管理器，传入状态管理器
        animationManager = new VehicleAnimationManager(vehicleStatusManager.value);

        // 初始化速度因子
        if (animationManager) {
          animationManager.setGlobalSpeedFactor(speedFactor.value);
        }

        // 初始加载POI数据
        updatePOIData();
      })
      .catch((e) => {
        console.log(e);
        ElMessage.error('地图加载失败');
      });
});

onUnmounted(() => {
  stopSimulationTimer();

  // 清理动画管理器
  if (animationManager) {
    animationManager.stopAll();
  }

  // 清理状态管理器
  if (vehicleStatusManager.value) {
    vehicleStatusManager.value.cleanup();
  }

  // 清理地图
  map?.destroy();

  // 清理所有绘制的路线
  activeRoutes.value.forEach(routeData => {
    if (routeData.cleanup) {
      routeData.cleanup();
    }
  });
  activeRoutes.value.clear();
  drawnPairIds.value.clear();

  console.log('[MapContainer] 所有资源已清理');
});
</script>

<style scoped>
.page-container {
  height: 100vh;
  width: 100vw;
  overflow: hidden; /* 防止整个页面滚动 */
}

.header-navbar {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 60px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  flex-shrink: 0; /* 防止header被压缩 */
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
  cursor: pointer;
}

.navbar-title:hover {
  color: #409eff;
}

.navbar-menu {
  display: flex;
  gap: 10px;
}

/* 侧边栏样式 - 整体滚动 */
.side-panel {
  background-color: #f7f8fa;
  padding: 0;
  border-right: 1px solid #e6e6e6;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px); /* 减去header高度 */
}

/* 侧边栏滚动容器 - 修复遮挡 */
.side-panel-scroll {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px 10px; /* 增加上下内边距 */
  overflow-y: auto;
  overflow-x: hidden; /* 防止水平滚动 */
  height: 100%;
  gap: 12px;
  /* 自定义滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: #c1c1c1 #f5f5f5;
  box-sizing: border-box; /* 确保padding包含在内 */
}

/* Webkit浏览器滚动条样式 */
.side-panel-scroll::-webkit-scrollbar {
  width: 6px;
}

.side-panel-scroll::-webkit-scrollbar-track {
  background: #f5f5f5;
  border-radius: 3px;
}

.side-panel-scroll::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.side-panel-scroll::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 面板部分容器 */
.panel-section {
  flex-shrink: 0; /* 防止被压缩 */
}

.panel-section:last-child {
  margin-bottom: 10px; /* 最后一个部分增加底部间距 */
}

/* 卡片基础样式 */
.box-card {
  border: none;
  width: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  box-sizing: border-box; /* 确保padding包含在内 */
}

.box-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

/* 仿真控制卡片 - 修复右侧遮挡 */
.simulation-control {
  min-height: 140px;
}

.simulation-control :deep(.el-card__body) {
  padding: 15px 12px; /* 调整内边距，确保内容不超出 */
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

/* 仿真控制内部布局优化 */
.control-group {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  width: 100%;
  box-sizing: border-box;
}

.control-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  flex-shrink: 0; /* 防止标签被压缩 */
  width: 60px; /* 固定标签宽度 */
}

.speed-slider {
  flex: 1;
  margin-left: 0;
  min-width: 0; /* 允许滑块压缩 */
}

/* 仿真按钮组调整 */
.simulation-control .control-group:last-child {
  display: flex;
  justify-content: space-between; /* 均匀分布按钮 */
  gap: 8px;
  margin-top: 10px;
}

.simulation-control .control-group:last-child .el-button {
  flex: 1; /* 按钮等宽分布 */
  min-width: 0; /* 允许按钮压缩 */
  padding: 8px 4px; /* 调整按钮内边距 */
  font-size: 13px;
}

.speed-display {
  text-align: center;
  font-size: 12px;
  color: #666;
  padding: 6px 0;
  background-color: #f8f9fa;
  border-radius: 4px;
  margin-top: 8px;
  flex-shrink: 0;
}

/* 车辆状态卡片 */
.vehicle-status {
  min-height: 180px;
  max-height: 380px; /* 稍微降低最大高度 */
}

.vehicle-status :deep(.el-card__body) {
  padding: 12px;
  overflow-y: auto;
  flex: 1;
  max-height: 320px; /* 限制内部滚动区域高度 */
  box-sizing: border-box;
}

/* 车辆列表 */
.vehicle-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.vehicle-item {
  display: flex;
  align-items: center;
  gap: 8px; /* 减小间距 */
  padding: 8px;
  border-radius: 6px;
  background-color: #fff;
  border: 1px solid #f0f0f0;
  transition: all 0.3s ease;
  cursor: pointer;
  box-sizing: border-box;
  width: 100%; /* 确保宽度100% */
}

.vehicle-item:hover {
  background-color: #f5f7fa;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.vehicle-item.selected {
  background-color: #ecf5ff;
  border-left: 3px solid #409eff;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.vehicle-info {
  flex-grow: 1;
  min-width: 0; /* 允许内容压缩 */
  overflow: hidden; /* 防止内容溢出 */
}

.vehicle-id {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vehicle-stats {
  margin-top: 4px;
}

.load-info,
.volume-info {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
  font-size: 11px;
  width: 100%;
}

.label {
  min-width: 28px; /* 稍微减小标签宽度 */
  color: #606266;
  font-weight: 500;
  flex-shrink: 0;
}

.value {
  min-width: 65px; /* 稍微减小数值宽度 */
  color: #303133;
  margin-right: 6px;
  flex-shrink: 0;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background-color: #ebeef5;
  border-radius: 3px;
  overflow: hidden;
  position: relative;
  min-width: 50px; /* 确保进度条最小宽度 */
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.load-progress {
  background-color: #67c23a;
}

.volume-progress {
  background-color: #409eff;
}

.vehicle-location {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.2;
  display: flex;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vehicle-location::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
  background-color: currentColor;
  flex-shrink: 0;
}

.vehicle-location.status-order-driving {
  color: #3498db;
}

.vehicle-location.status-transport-driving {
  color: #2ecc71;
}

.vehicle-location.status-loading {
  color: #f39c12;
}

.vehicle-location.status-unloading {
  color: #e74c3c;
}

.no-vehicle {
  text-align: center;
  padding: 20px;
  color: #909399;
  font-size: 14px;
  font-style: italic;
}

/* 车辆详细信息卡片样式 */
.vehicle-detail {
  min-height: 200px;
  max-height: 450px;
  overflow-y: auto;
}

.vehicle-detail :deep(.el-card__body) {
  padding: 15px;
  display: flex;
  flex-direction: column;
}

.vehicle-detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.vehicle-detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.vehicle-detail-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
  flex-shrink: 0;
}

.vehicle-detail-title h4 {
  margin: 0;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.vehicle-detail-subtitle {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 12px;
  color: white;
  font-size: 11px;
  font-weight: 500;
}

.vehicle-id {
  font-size: 11px;
  color: #909399;
}

.detail-section {
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.detail-section:first-of-type {
  border-top: none;
  padding-top: 0;
}

.detail-section h5 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.detail-item {
  display: flex;
  flex-direction: column;
  margin-bottom: 10px;
}

.detail-item:last-child {
  margin-bottom: 0;
}

.detail-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
  font-weight: 500;
}

.detail-value {
  font-size: 13px;
  color: #303133;
  margin-bottom: 6px;
}

.detail-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.detail-progress .progress-bar {
  flex: 1;
  height: 8px;
  background-color: #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.detail-progress .progress-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.detail-progress .load-progress {
  background-color: #67c23a;
}

.detail-progress .volume-progress {
  background-color: #409eff;
}

.progress-text {
  font-size: 12px;
  color: #606266;
  font-weight: 600;
  min-width: 40px;
  text-align: right;
}

/* 确保车辆详细信息卡片不会太高 */
.vehicle-detail :deep(.el-card__body)::-webkit-scrollbar {
  width: 4px;
}

.vehicle-detail :deep(.el-card__body)::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.vehicle-detail :deep(.el-card__body)::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 2px;
}

/* 响应式调整 */
@media (max-width: 1400px) {
  .vehicle-detail {
    max-height: 400px;
  }
}

@media (max-width: 768px) {
  .vehicle-detail {
    max-height: 350px;
  }
}

/* 统计信息卡片 - 修复下部遮挡 */
.statistics-info {
  min-height: 120px;
  margin-bottom: 10px; /* 增加底部边距 */
}

.statistics-info :deep(.el-card__body) {
  padding: 10px 12px; /* 调整内边距 */
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
}

.stats-info {
  display: flex;
  flex-direction: column;
  gap: 6px; /* 减小间距 */
}

.stats-info div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px; /* 减小内边距 */
  font-size: 12px;
  border-bottom: 1px solid #f5f5f5;
  transition: background-color 0.2s;
  box-sizing: border-box;
}

.stats-info div:hover {
  background-color: #f8f9fa;
}

.stats-info div:last-child {
  border-bottom: none;
}

.stats-info strong {
  color: #606266;
  font-weight: 500;
}

.stats-info span {
  color: #303133;
  font-weight: 600;
}

/* 车辆项高亮效果 */
.vehicle-item-highlighted {
  background-color: rgba(64, 158, 255, 0.1) !important;
  border: 2px solid #409eff !important;
  box-shadow: 0 0 15px rgba(64, 158, 255, 0.3) !important;
  transform: scale(1.02);
  transition: all 0.3s ease;
  position: relative;
  z-index: 10;
}

.vehicle-item-highlighted::before {
  content: '';
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  border-radius: 8px;
  background: linear-gradient(45deg, #409eff, #67c23a, #e6a23c, #f56c6c);
  background-size: 400% 400%;
  z-index: -1;
  animation: gradient-border 0.5s ease infinite;
}

@keyframes gradient-border {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

/* 车辆项内部高亮指示器 */
.vehicle-item-highlighted .status-dot {
  animation: pulse 1.5s infinite;
  box-shadow: 0 0 10px currentColor;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.2);
    opacity: 0.8;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 车辆ID高亮 */
.vehicle-item-highlighted .vehicle-id {
  color: #409eff;
  font-weight: bold;
}

/* 卡片头部统一调整 */
.card-header {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
}

/* 覆盖Element Plus默认样式 */
:deep(.el-card__header) {
  padding: 10px 12px; /* 减小内边距 */
  border-bottom: 1px solid #f0f0f0;
  background-color: #fafafa;
}

:deep(.el-card__body) {
  padding: 12px;
}

/* 地图容器 */
#container {
  width: 100%;
  height: 100%;
}

.el-main {
  padding: 0;
}

/* 响应式调整 */
@media (max-width: 1400px) {
  .side-panel {
    width: 340px !important; /* 稍微增加侧边栏宽度 */
  }

  .load-info,
  .volume-info {
    flex-direction: column;
    align-items: flex-start;
  }

  .label,
  .value {
    margin-bottom: 2px;
    width: 100%;
  }

  .progress-bar {
    width: 100%;
    margin-top: 4px;
  }
}

@media (max-width: 768px) {
  .side-panel {
    width: 300px !important;
  }

  .vehicle-stats {
    flex-direction: column;
    align-items: flex-start;
  }

  .load-info,
  .volume-info {
    width: 100%;
  }
}

/* 滚动优化 */
.vehicle-status :deep(.el-card__body)::-webkit-scrollbar {
  width: 4px;
}

.vehicle-status :deep(.el-card__body)::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.vehicle-status :deep(.el-card__body)::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 2px;
}

/* 平滑滚动 */
.side-panel-scroll {
  scroll-behavior: smooth;
}

/* 修复滑块宽度问题 */
:deep(.el-slider) {
  width: 100%;
}

:deep(.el-slider__runway) {
  margin: 0;
}

/* 确保按钮组适应容器 */
.simulation-control :deep(.el-button-group) {
  display: flex;
  width: 100%;
}

.simulation-control :deep(.el-button-group .el-button) {
  flex: 1;
}
</style>