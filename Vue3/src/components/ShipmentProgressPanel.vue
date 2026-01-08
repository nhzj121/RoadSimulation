<template>
  <div class="shipment-progress-panel">
    <!-- 面板头部 -->
    <div class="panel-header">
      <h3 class="panel-title">运单信息</h3>
      <div class="panel-actions">
        <ElButton
            text
            :icon="isExpanded ? 'Fold' : 'Expand'"
            @click="togglePanel"
            :title="isExpanded ? '收起' : '展开'"
        />
        <ElButton
            text
            :icon="isLoading ? 'Loading' : 'Refresh'"
            @click="refreshData"
            :loading="isLoading"
            title="刷新数据"
        />
        <ElDropdown
            trigger="click"
            @command="handleFilterCommand"
        >
          <ElButton
              text
              icon="Filter"
              title="筛选"
          />
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem
                  v-for="filter in statusFilters"
                  :key="filter.value"
                  :command="filter.value"
              >
                <span
                    class="filter-option"
                    :class="{ 'filter-option--active': activeStatusFilter === filter.value }"
                >
                  <span
                      class="filter-dot"
                      :style="{ backgroundColor: filter.color }"
                  ></span>
                  {{ filter.label }}
                </span>
              </ElDropdownItem>
              <ElDropdownItem
                  divided
                  command="ALL"
              >
                <span
                    class="filter-option"
                    :class="{ 'filter-option--active': activeStatusFilter === 'ALL' }"
                >
                  全部运单
                </span>
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>
    </div>

    <!-- 统计摘要 -->
    <div
        v-if="showSummary && summaryStats"
        class="panel-summary"
    >
      <div class="summary-stats">
        <div class="summary-stat">
          <div class="stat-value">{{ summaryStats.totalShipments || 0 }}</div>
          <div class="stat-label">总运单</div>
        </div>
        <div class="summary-stat">
          <div class="stat-value">{{ summaryStats.activeShipments || 0 }}</div>
          <div class="stat-label">活跃中</div>
        </div>
        <div class="summary-stat">
          <div class="stat-value">{{ summaryStats.completedItems || 0 }}/{{ summaryStats.totalItems || 0 }}</div>
          <div class="stat-label">完成项</div>
        </div>
        <div class="summary-stat">
          <div class="stat-value">{{ summaryStats.overallProgress ? summaryStats.overallProgress.toFixed(1) : 0 }}%</div>
          <div class="stat-label">总进度</div>
        </div>
      </div>
    </div>

    <!-- 搜索框 -->
    <div
        v-if="showSearch"
        class="panel-search"
    >
      <ElInput
          v-model="searchQuery"
          placeholder="搜索运单号、货物、地点..."
          size="small"
          clearable
          @clear="handleSearchClear"
      >
        <template #prefix>
          <ElIcon><Search /></ElIcon>
        </template>
        <template #append>
          <ElButton
              size="small"
              @click="executeSearch"
          >
            搜索
          </ElButton>
        </template>
      </ElInput>
    </div>

    <!-- 运单列表 -->
    <div
        v-if="!isExpanded"
        class="shipment-list"
    >
      <!-- 空状态 -->
      <div
          v-if="!isLoading && shipments.length === 0"
          class="empty-state"
      >
        <div class="empty-state__icon">📦</div>
        <div class="empty-state__text">暂无运单数据</div>
        <ElButton
            type="primary"
            size="small"
            @click="refreshData"
        >
          刷新数据
        </ElButton>
      </div>

      <!-- 加载中 -->
      <div
          v-else-if="isLoading && shipments.length === 0"
          class="loading-state"
      >
        <ElIcon
            class="loading-icon"
            size="24"
        >
          <Loading />
        </ElIcon>
        <div class="loading-text">正在加载运单数据...</div>
      </div>

      <!-- 虚拟滚动列表 -->
      <VirtualScroll
          v-else
          :items="filteredShipments"
          :item-height="120"
          :overscan="10"
          class="virtual-scroll-wrapper"
      >
        <template #item="{ item }">
          <ShipmentCard
              :shipment="item"
              :expanded="expandedShipmentId === item.shipmentId"
              :highlighted="highlightedShipmentId === item.shipmentId"
              @click="handleShipmentClick(item)"
              @expand="handleShipmentExpand(item, $event)"
          />
        </template>
      </VirtualScroll>

      <!-- 选中运单详情 -->
      <div
          v-if="selectedShipment && isExpanded"
          class="selected-shipment-detail"
      >
        <ShipmentDetailPanel
            :shipment="selectedShipment"
            :loading="loadingDetail"
            @close="closeDetailPanel"
            @refresh="refreshShipmentDetail"
        />
      </div>
    </div>

    <!-- 底部信息 -->
    <div class="panel-footer">
      <div class="footer-info">
        <span class="info-text">
          共 {{ filteredShipments.length }} 个运单
          <span
              v-if="searchQuery"
              class="info-search"
          >(搜索: "{{ searchQuery }}")</span>
        </span>
        <span class="info-time">
          更新于: {{ lastUpdateTime }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import {
  ElButton,
  ElInput,
  ElIcon,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElMessage
} from 'element-plus';
import { Search, Loading, Fold, Expand, Refresh, Filter } from '@element-plus/icons-vue';
import VirtualScroll from './VirtualScroll.vue';
import ShipmentCard from './ShipmentCard.vue';
import ShipmentDetailPanel from './ShipmentDetailPanel.vue';
import {
  getActiveShipments,
  getShipmentProgressDetail,
  getOverallProgressSummary,
  simplifyShipmentForList,
  shipmentStatusMap,
  formatDateTime
} from '../api/shipmentProgressApi.js';

const props = defineProps({
  // 是否显示摘要
  showSummary: {
    type: Boolean,
    default: true
  },
  // 是否显示搜索框
  showSearch: {
    type: Boolean,
    default: true
  },
  // 自动刷新间隔（毫秒），0表示不自动刷新
  autoRefreshInterval: {
    type: Number,
    default: 30000 // 30秒
  },
  // 初始是否展开
  initiallyExpanded: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits([
  'shipment-click',
  'shipment-selected',
  'data-updated',
  'error'
]);

// 状态
const shipments = ref([]);
const selectedShipment = ref(null);
const expandedShipmentId = ref(null);
const highlightedShipmentId = ref(null);
const isLoading = ref(false);
const loadingDetail = ref(false);
const isExpanded = ref(props.initiallyExpanded);
const searchQuery = ref('');
const activeStatusFilter = ref('ALL');
const summaryStats = ref(null);
const lastUpdateTime = ref('--:--:--');

// 定时器
let refreshTimer = null;

// 状态过滤器
const statusFilters = computed(() => {
  return Object.entries(shipmentStatusMap).map(([value, config]) => ({
    value,
    label: config.text,
    color: config.color
  }));
});

// 过滤后的运单列表
const filteredShipments = computed(() => {
  let filtered = [...shipments.value];

  // 状态过滤
  if (activeStatusFilter.value !== 'ALL') {
    filtered = filtered.filter(shipment =>
        shipment.status === activeStatusFilter.value
    );
  }

  // 搜索过滤
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase().trim();
    filtered = filtered.filter(shipment => {
      return (
          (shipment.refNo && shipment.refNo.toLowerCase().includes(query)) ||
          (shipment.cargoType && shipment.cargoType.toLowerCase().includes(query)) ||
          (shipment.originPOIName && shipment.originPOIName.toLowerCase().includes(query)) ||
          (shipment.destPOIName && shipment.destPOIName.toLowerCase().includes(query))
      );
    });
  }

  return filtered;
});

// 获取运单列表数据
const fetchShipments = async () => {
  if (isLoading.value) return;

  isLoading.value = true;
  try {
    const data = await getActiveShipments();
    shipments.value = data.map(shipment => ({
      ...shipment,
      progressColor: shipment.progressColor || getProgressColor(shipment.progressPercentage)
    }));

    // 更新最后更新时间
    lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN');

    // 触发数据更新事件
    emit('data-updated', shipments.value);

    ElMessage.success(`已更新 ${data.length} 个运单`);
  } catch (error) {
    console.error('获取运单列表失败:', error);
    ElMessage.error('获取运单列表失败');
    emit('error', error);
  } finally {
    isLoading.value = false;
  }
};

// 获取运单详情
const fetchShipmentDetail = async (shipmentId) => {
  if (loadingDetail.value) return;

  loadingDetail.value = true;
  try {
    const detail = await getShipmentProgressDetail(shipmentId);
    selectedShipment.value = detail;

    // 触发选中事件
    emit('shipment-selected', detail);
  } catch (error) {
    console.error(`获取运单${shipmentId}详情失败:`, error);
    ElMessage.error('获取运单详情失败');
    emit('error', error);
  } finally {
    loadingDetail.value = false;
  }
};

// 获取统计摘要
const fetchSummaryStats = async () => {
  try {
    const summary = await getOverallProgressSummary();
    summaryStats.value = summary;
  } catch (error) {
    console.error('获取统计摘要失败:', error);
  }
};

// 刷新数据
const refreshData = async () => {
  await Promise.all([
    fetchShipments(),
    fetchSummaryStats()
  ]);
};

// 刷新运单详情
const refreshShipmentDetail = async (shipmentId) => {
  if (!shipmentId && selectedShipment.value) {
    shipmentId = selectedShipment.value.shipmentId;
  }

  if (shipmentId) {
    await fetchShipmentDetail(shipmentId);
  }
};

// 处理运单点击
const handleShipmentClick = (shipment) => {
  // 如果当前是展开模式，点击运单卡牌会切换到详情模式
  if (!isExpanded.value) {
    expandedShipmentId.value =
        expandedShipmentId.value === shipment.shipmentId ? null : shipment.shipmentId;
  }

  emit('shipment-click', shipment);
};

// 处理运单展开
const handleShipmentExpand = (shipment, expanded) => {
  if (expanded) {
    expandedShipmentId.value = shipment.shipmentId;
  } else {
    expandedShipmentId.value = null;
  }
};

// 处理筛选命令
const handleFilterCommand = (command) => {
  activeStatusFilter.value = command;
};

// 处理搜索
const executeSearch = () => {
  // 搜索逻辑已经通过computed自动处理
};

// 处理搜索清空
const handleSearchClear = () => {
  searchQuery.value = '';
};

// 关闭详情面板
const closeDetailPanel = () => {
  selectedShipment.value = null;
  isExpanded.value = false;
  expandedShipmentId.value = null;
};

// 切换面板展开状态
const togglePanel = () => {
  isExpanded.value = !isExpanded.value;

  // 如果展开面板且有选中运单，加载详情
  if (isExpanded.value && expandedShipmentId.value) {
    fetchShipmentDetail(expandedShipmentId.value);
  }
};

// 获取进度颜色
const getProgressColor = (progressPercentage) => {
  if (progressPercentage >= 100) {
    return '#52c41a';
  } else if (progressPercentage >= 70) {
    return '#1890ff';
  } else if (progressPercentage >= 30) {
    return '#faad14';
  } else {
    return '#f5222d';
  }
};

// 高亮显示运单（用于车辆到达事件）
const highlightShipment = (shipmentId, duration = 3000) => {
  highlightedShipmentId.value = shipmentId;

  // 自动取消高亮
  setTimeout(() => {
    if (highlightedShipmentId.value === shipmentId) {
      highlightedShipmentId.value = null;
    }
  }, duration);
};

// 添加运单（用于车辆到达事件）
const addOrUpdateShipment = async (shipmentData) => {
  // 查找是否已存在
  const existingIndex = shipments.value.findIndex(s => s.shipmentId === shipmentData.shipmentId);

  if (existingIndex >= 0) {
    // 更新现有运单
    shipments.value[existingIndex] = {
      ...shipments.value[existingIndex],
      ...shipmentData,
      progressColor: getProgressColor(shipmentData.progressPercentage)
    };
  } else {
    // 添加新运单
    shipments.value.unshift({
      ...shipmentData,
      progressColor: getProgressColor(shipmentData.progressPercentage)
    });
  }

  // 触发高亮
  highlightShipment(shipmentData.shipmentId);

  // 如果当前选中了该运单，刷新详情
  if (selectedShipment.value && selectedShipment.value.shipmentId === shipmentData.shipmentId) {
    await refreshShipmentDetail(shipmentData.shipmentId);
  }
};

// 初始化
const init = async () => {
  await refreshData();

  // 设置自动刷新
  if (props.autoRefreshInterval > 0) {
    refreshTimer = setInterval(refreshData, props.autoRefreshInterval);
  }
};

// 生命周期
onMounted(() => {
  init();
});

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
});

// 监听props变化
watch(() => props.autoRefreshInterval, (newValue) => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }

  if (newValue > 0) {
    refreshTimer = setInterval(refreshData, newValue);
  }
});

// 暴露方法给父组件
defineExpose({
  refreshData,
  addOrUpdateShipment,
  highlightShipment,
  fetchShipmentDetail,
  getShipments: () => shipments.value,
  getSelectedShipment: () => selectedShipment.value
});
</script>

<style scoped>
.shipment-progress-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #f8f9fa 0%, #fff 100%);
  flex-shrink: 0;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.panel-actions {
  display: flex;
  gap: 4px;
}

.panel-summary {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.summary-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
  gap: 12px;
}

.summary-stat {
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
  text-align: center;
  transition: all 0.3s ease;
}

.summary-stat:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.panel-search {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.shipment-list {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.empty-state {
  padding: 60px 20px;
  text-align: center;
  color: #909399;
}

.empty-state__icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-state__text {
  font-size: 14px;
  margin-bottom: 16px;
}

.loading-state {
  padding: 60px 20px;
  text-align: center;
  color: #909399;
}

.loading-icon {
  animation: rotate 1s linear infinite;
  margin-bottom: 16px;
  color: #409eff;
}

.loading-text {
  font-size: 14px;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.virtual-scroll-wrapper {
  height: 100%;
  padding: 0 16px;
}

.selected-shipment-detail {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  z-index: 10;
  overflow-y: auto;
}

.panel-footer {
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #f8f9fa;
  flex-shrink: 0;
}

.footer-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

.info-text {
  display: flex;
  align-items: center;
  gap: 4px;
}

.info-search {
  color: #409eff;
  font-weight: 500;
}

.info-time {
  font-family: monospace;
}

.filter-option {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
}

.filter-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.filter-option--active {
  color: #409eff;
  font-weight: 500;
}
</style>