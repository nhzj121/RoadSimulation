<template>
  <div class="shipment-detail-panel">
    <!-- 头部 -->
    <div class="shipment-detail-panel__header">
      <div class="header__left">
        <h3 class="header__title">运单详情</h3>
        <div class="header__subtitle">
          <span class="subtitle__ref">运单号: {{ shipment.refNo }}</span>
          <ElTag
              size="small"
              :type="getStatusTagType(shipment.status)"
          >
            {{ shipment.statusText }}
          </ElTag>
        </div>
      </div>
      <div class="header__right">
        <ElButton
            text
            icon="Close"
            @click="emit('close')"
        />
      </div>
    </div>

    <!-- 基本信息 -->
    <div class="shipment-detail-panel__section">
      <h4 class="section__title">基本信息</h4>
      <div class="basic-info">
        <div class="basic-info__row">
          <div class="basic-info__item">
            <span class="item__label">货物类型:</span>
            <span class="item__value">{{ shipment.cargoType || '普通货物' }}</span>
          </div>
          <div class="basic-info__item">
            <span class="item__label">总重量:</span>
            <span class="item__value">{{ shipment.totalWeight ? shipment.totalWeight.toFixed(2) : 0 }} kg</span>
          </div>
          <div class="basic-info__item">
            <span class="item__label">总体积:</span>
            <span class="item__value">{{ shipment.totalVolume ? shipment.totalVolume.toFixed(2) : 0 }} m³</span>
          </div>
        </div>

        <div class="basic-info__row">
          <div class="basic-info__item">
            <span class="item__label">起点:</span>
            <span class="item__value">{{ shipment.originPOIName || '未知' }}</span>
          </div>
          <div class="basic-info__item">
            <span class="item__label">终点:</span>
            <span class="item__value">{{ shipment.destPOIName || '未知' }}</span>
          </div>
        </div>

        <div class="basic-info__row">
          <div class="basic-info__item">
            <span class="item__label">创建时间:</span>
            <span class="item__value">{{ formatDateTime(shipment.createdAt) }}</span>
          </div>
          <div class="basic-info__item">
            <span class="item__label">最后更新:</span>
            <span class="item__value">{{ formatDateTime(shipment.updatedAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 进度概览 -->
    <div class="shipment-detail-panel__section">
      <h4 class="section__title">运输进度</h4>
      <div class="progress-overview">
        <div class="progress-overview__stats">
          <div class="progress-stat">
            <div class="progress-stat__value">{{ shipment.completedItems || 0 }}</div>
            <div class="progress-stat__label">已完成</div>
          </div>
          <div class="progress-stat">
            <div class="progress-stat__value">{{ shipment.inProgressItems || 0 }}</div>
            <div class="progress-stat__label">运输中</div>
          </div>
          <div class="progress-stat">
            <div class="progress-stat__value">{{ shipment.waitingItems || 0 }}</div>
            <div class="progress-stat__label">待运输</div>
          </div>
          <div class="progress-stat">
            <div class="progress-stat__value">{{ shipment.totalItems || 0 }}</div>
            <div class="progress-stat__label">总计</div>
          </div>
        </div>

        <div class="progress-overview__bars">
          <div class="progress-bar-group">
            <div class="progress-bar__header">
              <span>数量进度</span>
              <span>{{ (shipment.progressPercentage || 0).toFixed(1) }}%</span>
            </div>
            <div class="progress-bar">
              <div
                  class="progress-bar__fill"
                  :style="{
                  width: `${shipment.progressPercentage || 0}%`,
                  backgroundColor: shipment.progressColor || '#1890ff'
                }"
              ></div>
            </div>
          </div>

          <div
              v-if="shipment.totalWeight"
              class="progress-bar-group"
          >
            <div class="progress-bar__header">
              <span>重量进度</span>
              <span>{{ (shipment.completedWeightPercentage || 0).toFixed(1) }}%</span>
            </div>
            <div class="progress-bar">
              <div
                  class="progress-bar__fill"
                  :style="{
                  width: `${shipment.completedWeightPercentage || 0}%`,
                  backgroundColor: '#52c41a'
                }"
              ></div>
            </div>
          </div>

          <div
              v-if="shipment.totalVolume"
              class="progress-bar-group"
          >
            <div class="progress-bar__header">
              <span>体积进度</span>
              <span>{{ (shipment.completedVolumePercentage || 0).toFixed(1) }}%</span>
            </div>
            <div class="progress-bar">
              <div
                  class="progress-bar__fill"
                  :style="{
                  width: `${shipment.completedVolumePercentage || 0}%`,
                  backgroundColor: '#1890ff'
                }"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 货物明细 -->
    <div class="shipment-detail-panel__section">
      <div class="section__header">
        <h4 class="section__title">货物明细</h4>
        <ElInput
            v-model="searchQuery"
            placeholder="搜索货物..."
            size="small"
            style="width: 200px;"
            clearable
        >
          <template #prefix>
            <ElIcon><Search /></ElIcon>
          </template>
        </ElInput>
      </div>

      <ElTable
          v-loading="loadingItems"
          :data="filteredItems"
          size="small"
          stripe
          style="width: 100%;"
          :header-cell-style="{ backgroundColor: '#fafafa' }"
      >
        <ElTableColumn
            prop="name"
            label="货物名称"
            min-width="150"
        >
          <template #default="{ row }">
            <div class="item-name">
              <span class="item-name__text">{{ row.name }}</span>
              <span
                  v-if="row.sku"
                  class="item-name__sku"
              >{{ row.sku }}</span>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn
            prop="qty"
            label="数量"
            width="80"
            align="center"
        />

        <ElTableColumn
            prop="weight"
            label="重量"
            width="100"
            align="right"
        >
          <template #default="{ row }">
            {{ row.weight ? row.weight.toFixed(2) : 0 }} kg
          </template>
        </ElTableColumn>

        <ElTableColumn
            prop="volume"
            label="体积"
            width="100"
            align="right"
        >
          <template #default="{ row }">
            {{ row.volume ? row.volume.toFixed(2) : 0 }} m³
          </template>
        </ElTableColumn>

        <ElTableColumn
            prop="status"
            label="状态"
            width="120"
        >
          <template #default="{ row }">
            <ElTag
                size="small"
                :type="getItemStatusTagType(row.status)"
                :style="{ backgroundColor: row.statusColor, borderColor: row.statusColor }"
            >
              {{ row.statusText }}
            </ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn
            label="关联车辆"
            width="180"
        >
          <template #default="{ row }">
            <div
                v-if="row.vehicleLicensePlate"
                class="vehicle-info"
            >
              <span class="vehicle-info__icon">🚚</span>
              <span class="vehicle-info__plate">{{ row.vehicleLicensePlate }}</span>
              <ElTag
                  size="mini"
                  effect="plain"
              >
                {{ row.vehicleStatus || '空闲' }}
              </ElTag>
            </div>
            <span
                v-else
                class="no-vehicle"
            >未分配</span>
          </template>
        </ElTableColumn>

        <ElTableColumn
            label="分配时间"
            width="160"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.assignedTime) }}
          </template>
        </ElTableColumn>

        <ElTableColumn
            label="送达时间"
            width="160"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.deliveredTime) }}
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <!-- 关联车辆 -->
    <div
        v-if="shipment.vehicles && shipment.vehicles.length > 0"
        class="shipment-detail-panel__section"
    >
      <h4 class="section__title">关联车辆</h4>
      <div class="vehicles-list">
        <div
            v-for="vehicle in shipment.vehicles"
            :key="vehicle.id"
            class="vehicle-card"
        >
          <div class="vehicle-card__header">
            <div class="vehicle-card__title">
              <span class="vehicle-card__plate">{{ vehicle.licensePlate }}</span>
              <span class="vehicle-card__type">{{ vehicle.vehicleType || '货车' }}</span>
            </div>
            <ElTag
                size="small"
                :type="getVehicleStatusTagType(vehicle.currentStatus)"
            >
              {{ vehicle.currentStatus || '空闲' }}
            </ElTag>
          </div>

          <div class="vehicle-card__details">
            <div class="vehicle-card__detail">
              <span class="detail__label">车型:</span>
              <span class="detail__value">{{ vehicle.brand }} {{ vehicle.modelType }}</span>
            </div>
            <div class="vehicle-card__detail">
              <span class="detail__label">载重:</span>
              <span class="detail__value">{{ vehicle.currentLoad }}/{{ vehicle.maxLoadCapacity }} t</span>
            </div>
            <div class="vehicle-card__detail">
              <span class="detail__label">驾驶员:</span>
              <span class="detail__value">{{ vehicle.driverName || '未分配' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作 -->
    <div class="shipment-detail-panel__footer">
      <ElButton @click="emit('close')">关闭</ElButton>
      <ElButton
          type="primary"
          @click="refreshData"
          :loading="refreshing"
      >
        刷新数据
      </ElButton>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import {
  ElButton,
  ElTag,
  ElInput,
  ElIcon,
  ElTable,
  ElTableColumn
} from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { formatDateTime } from '../api/shipmentProgressApi.js';

const props = defineProps({
  shipment: {
    type: Object,
    required: true,
    default: () => ({})
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['close', 'refresh']);

const searchQuery = ref('');
const loadingItems = ref(false);
const refreshing = ref(false);

// 过滤货物列表
const filteredItems = computed(() => {
  if (!props.shipment.items) return [];

  if (!searchQuery.value) return props.shipment.items;

  const query = searchQuery.value.toLowerCase();
  return props.shipment.items.filter(item =>
      (item.name && item.name.toLowerCase().includes(query)) ||
      (item.sku && item.sku.toLowerCase().includes(query))
  );
});

// 获取状态标签类型
const getStatusTagType = (status) => {
  switch (status) {
    case 'DELIVERED':
      return 'success';
    case 'IN_TRANSIT':
      return 'primary';
    case 'PICKED_UP':
    case 'PLANNED':
      return 'warning';
    case 'CANCELLED':
      return 'danger';
    default:
      return 'info';
  }
};

// 获取货物状态标签类型
const getItemStatusTagType = (status) => {
  switch (status) {
    case 'DELIVERED':
      return 'success';
    case 'IN_TRANSIT':
    case 'LOADED':
      return 'primary';
    case 'ASSIGNED':
      return 'warning';
    case 'NOT_ASSIGNED':
      return 'info';
    default:
      return '';
  }
};

// 获取车辆状态标签类型
const getVehicleStatusTagType = (status) => {
  if (!status) return 'info';

  const statusLower = status.toLowerCase();
  if (statusLower.includes('idle') || statusLower.includes('waiting')) {
    return 'info';
  } else if (statusLower.includes('driving') || statusLower.includes('transit')) {
    return 'primary';
  } else if (statusLower.includes('loading') || statusLower.includes('unloading')) {
    return 'warning';
  } else if (statusLower.includes('breakdown') || statusLower.includes('error')) {
    return 'danger';
  } else {
    return '';
  }
};

// 刷新数据
const refreshData = async () => {
  refreshing.value = true;
  try {
    await emit('refresh', props.shipment.shipmentId);
  } finally {
    refreshing.value = false;
  }
};

// 监听shipment变化，重置搜索
watch(() => props.shipment, () => {
  searchQuery.value = '';
});
</script>

<style scoped>
.shipment-detail-panel {
  background: #fff;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.shipment-detail-panel__header {
  padding: 16px 20px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  background: linear-gradient(135deg, #f8f9fa 0%, #fff 100%);
  flex-shrink: 0;
}

.header__left {
  flex: 1;
}

.header__title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.header__subtitle {
  display: flex;
  align-items: center;
  gap: 12px;
}

.subtitle__ref {
  font-size: 14px;
  color: #606266;
}

.header__right {
  flex-shrink: 0;
}

.shipment-detail-panel__section {
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.shipment-detail-panel__section:last-child {
  border-bottom: none;
}

.section__title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
}

.section__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.basic-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.basic-info__row {
  display: flex;
  gap: 24px;
}

.basic-info__item {
  display: flex;
  align-items: center;
  min-width: 200px;
}

.item__label {
  font-size: 14px;
  color: #909399;
  margin-right: 8px;
  min-width: 80px;
}

.item__value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.progress-overview {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.progress-overview__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: 16px;
}

.progress-stat {
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  transition: all 0.3s ease;
}

.progress-stat:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.progress-stat__value {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 4px;
}

.progress-stat__label {
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.progress-overview__bars {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.progress-bar-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.progress-bar__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #606266;
}

.progress-bar {
  height: 10px;
  background-color: #ebeef5;
  border-radius: 5px;
  overflow: hidden;
}

.progress-bar__fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.6s ease;
}

.item-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-name__text {
  font-size: 14px;
  color: #303133;
}

.item-name__sku {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}

.vehicle-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.vehicle-info__icon {
  font-size: 14px;
}

.vehicle-info__plate {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.no-vehicle {
  color: #909399;
  font-style: italic;
}

.vehicles-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.vehicle-card {
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  transition: all 0.3s ease;
}

.vehicle-card:hover {
  border-color: #c0c4cc;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.vehicle-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.vehicle-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.vehicle-card__plate {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.vehicle-card__type {
  font-size: 12px;
  color: #909399;
  background-color: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}

.vehicle-card__details {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.vehicle-card__detail {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.detail__label {
  color: #909399;
  min-width: 60px;
  margin-right: 8px;
}

.detail__value {
  color: #606266;
  font-weight: 500;
}

.shipment-detail-panel__footer {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  background: #f8f9fa;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .basic-info__row {
    flex-direction: column;
    gap: 8px;
  }

  .basic-info__item {
    min-width: auto;
  }

  .progress-overview__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .section__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .vehicles-list {
    grid-template-columns: 1fr;
  }
}
</style>