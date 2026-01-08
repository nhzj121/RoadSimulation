<template>
  <div
      class="shipment-card"
      :class="{
      'shipment-card--expanded': expanded,
      'shipment-card--highlighted': highlighted,
      'shipment-card--clickable': clickable
    }"
      @click="handleClick"
  >
    <!-- 运单头部 -->
    <div class="shipment-card__header">
      <div class="shipment-card__title">
        <span class="shipment-card__ref">{{ shipment.refNo }}</span>
        <span class="shipment-card__type">{{ shipment.cargoType || '普通货物' }}</span>
      </div>

      <div class="shipment-card__actions">
        <ElButton
            v-if="showExpandButton"
            text
            :icon="expanded ? 'ArrowDown' : 'ArrowRight'"
            @click.stop="toggleExpand"
        />
        <ElButton
            v-if="showDetailsButton"
            text
            icon="View"
            @click.stop="emit('view-details', shipment)"
        />
      </div>
    </div>

    <!-- 路线信息 -->
    <div class="shipment-card__route">
      <div class="shipment-card__route-point">
        <span class="route-point__icon">📍</span>
        <span class="route-point__name">{{ shipment.originPOIName || '起点' }}</span>
      </div>
      <div class="shipment-card__route-line">
        <div class="route-line__dash"></div>
        <div class="route-line__arrow">→</div>
      </div>
      <div class="shipment-card__route-point">
        <span class="route-point__icon">🏁</span>
        <span class="route-point__name">{{ shipment.destPOIName || '终点' }}</span>
      </div>
    </div>

    <!-- 状态和进度 -->
    <div class="shipment-card__status">
      <div class="shipment-card__status-badge">
        <span
            class="status-badge__dot"
            :style="{ backgroundColor: shipment.statusColor || '#ccc' }"
        ></span>
        <span class="status-badge__text">{{ shipment.statusText || '未知状态' }}</span>
      </div>

      <div class="shipment-card__progress">
        <div class="progress__info">
          <span class="progress__text">
            完成: {{ shipment.completedItems || 0 }}/{{ shipment.totalItems || 0 }}
          </span>
          <span class="progress__percentage">
            {{ (shipment.progressPercentage || 0).toFixed(1) }}%
          </span>
        </div>
        <div class="progress__bar">
          <div
              class="progress__fill"
              :style="{
              width: `${shipment.progressPercentage || 0}%`,
              backgroundColor: shipment.progressColor || '#1890ff'
            }"
          ></div>
        </div>
      </div>
    </div>

    <!-- 折叠内容 -->
    <div
        v-if="expanded && shipment.items && shipment.items.length > 0"
        class="shipment-card__details"
    >
      <div class="shipment-card__details-header">
        <h4 class="details-header__title">货物明细</h4>
        <span class="details-header__count">共 {{ shipment.items.length }} 项</span>
      </div>

      <div class="shipment-card__items-list">
        <div
            v-for="item in shipment.items.slice(0, showAllItems ? shipment.items.length : 3)"
            :key="item.id"
            class="shipment-item"
            :class="`shipment-item--${item.status.toLowerCase()}`"
        >
          <div class="shipment-item__header">
            <div class="shipment-item__name">{{ item.name || '未知货物' }}</div>
            <div class="shipment-item__status">
              <span
                  class="item-status__dot"
                  :style="{ backgroundColor: item.statusColor || '#ccc' }"
              ></span>
              <span class="item-status__text">{{ item.statusText || '未知' }}</span>
            </div>
          </div>

          <div class="shipment-item__details">
            <div class="shipment-item__detail">
              <span class="detail__label">数量:</span>
              <span class="detail__value">{{ item.qty || 0 }}</span>
            </div>
            <div class="shipment-item__detail">
              <span class="detail__label">重量:</span>
              <span class="detail__value">{{ item.weight ? item.weight.toFixed(2) : 0 }} kg</span>
            </div>
            <div class="shipment-item__detail">
              <span class="detail__label">体积:</span>
              <span class="detail__value">{{ item.volume ? item.volume.toFixed(2) : 0 }} m³</span>
            </div>
          </div>

          <!-- 关联的车辆信息 -->
          <div
              v-if="item.vehicleLicensePlate"
              class="shipment-item__vehicle"
          >
            <span class="vehicle__icon">🚚</span>
            <span class="vehicle__plate">{{ item.vehicleLicensePlate }}</span>
            <span class="vehicle__status">{{ item.vehicleStatus || '空闲' }}</span>
          </div>
        </div>
      </div>

      <!-- 显示更多/收起按钮 -->
      <div
          v-if="shipment.items.length > 3"
          class="shipment-card__more-items"
      >
        <ElButton
            text
            :icon="showAllItems ? 'ArrowUp' : 'ArrowDown'"
            @click="showAllItems = !showAllItems"
        >
          {{ showAllItems ? '收起' : `展开全部 ${shipment.items.length} 项` }}
        </ElButton>
      </div>
    </div>

    <!-- 底部信息 -->
    <div class="shipment-card__footer">
      <div class="shipment-card__timestamps">
        <span class="timestamp" title="创建时间">
          📅 {{ formatDateTime(shipment.createdAt) }}
        </span>
        <span
            v-if="shipment.updatedAt"
            class="timestamp"
            title="最后更新"
        >
          🔄 {{ formatDateTime(shipment.updatedAt) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { ElButton } from 'element-plus';
import { formatDateTime } from '../api/shipmentProgressApi.js';

const props = defineProps({
  shipment: {
    type: Object,
    required: true,
    default: () => ({})
  },
  expanded: {
    type: Boolean,
    default: false
  },
  highlighted: {
    type: Boolean,
    default: false
  },
  clickable: {
    type: Boolean,
    default: true
  },
  showExpandButton: {
    type: Boolean,
    default: true
  },
  showDetailsButton: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['click', 'expand', 'view-details']);

const showAllItems = ref(false);

// 处理卡片点击
const handleClick = () => {
  if (props.clickable) {
    emit('click', props.shipment);
  }
};

// 切换展开状态
const toggleExpand = () => {
  emit('expand', !props.expanded);
};
</script>

<style scoped>
.shipment-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  transition: all 0.3s ease;
  cursor: default;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.shipment-card:hover {
  box-shadow: 0 3px 6px rgba(0, 0, 0, 0.08);
  border-color: #c0c4cc;
}

.shipment-card--expanded {
  border-color: #409eff;
  box-shadow: 0 3px 12px rgba(64, 158, 255, 0.12);
}

.shipment-card--highlighted {
  background-color: #f0f7ff;
  border-color: #409eff;
}

.shipment-card--clickable {
  cursor: pointer;
}

.shipment-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.shipment-card__title {
  flex: 1;
  min-width: 0;
}

.shipment-card__ref {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-right: 8px;
}

.shipment-card__type {
  font-size: 12px;
  color: #909399;
  background-color: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}

.shipment-card__actions {
  display: flex;
  gap: 4px;
}

.shipment-card__route {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.shipment-card__route-point {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.route-point__icon {
  font-size: 14px;
  margin-right: 6px;
}

.route-point__name {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.shipment-card__route-line {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  position: relative;
}

.route-line__dash {
  flex: 1;
  height: 1px;
  background-color: #dcdfe6;
  margin: 0 4px;
}

.route-line__arrow {
  font-size: 14px;
  color: #909399;
}

.shipment-card__status {
  margin-bottom: 16px;
}

.shipment-card__status-badge {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.status-badge__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.status-badge__text {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.shipment-card__progress {
  background-color: #fafafa;
  padding: 8px;
  border-radius: 6px;
}

.progress__info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
}

.progress__text {
  color: #606266;
}

.progress__percentage {
  font-weight: 600;
  color: #409eff;
}

.progress__bar {
  height: 6px;
  background-color: #ebeef5;
  border-radius: 3px;
  overflow: hidden;
}

.progress__fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.shipment-card__details {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.shipment-card__details-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.details-header__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.details-header__count {
  font-size: 12px;
  color: #909399;
}

.shipment-card__items-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.shipment-item {
  background-color: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 12px;
  transition: all 0.2s ease;
}

.shipment-item:hover {
  border-color: #dcdfe6;
  background-color: #f5f7fa;
}

.shipment-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.shipment-item__name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 8px;
}

.shipment-item__status {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.item-status__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
}

.item-status__text {
  font-size: 12px;
  color: #606266;
}

.shipment-item__details {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.shipment-item__detail {
  font-size: 12px;
}

.detail__label {
  color: #909399;
  margin-right: 4px;
}

.detail__value {
  color: #606266;
  font-weight: 500;
}

.shipment-item__vehicle {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #606266;
  background-color: #f0f7ff;
  padding: 4px 8px;
  border-radius: 4px;
  margin-top: 4px;
}

.vehicle__icon {
  margin-right: 4px;
  font-size: 14px;
}

.vehicle__plate {
  font-weight: 500;
  margin-right: 8px;
}

.vehicle__status {
  color: #409eff;
}

.shipment-card__more-items {
  text-align: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e4e7ed;
}

.shipment-card__footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.shipment-card__timestamps {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.timestamp {
  display: flex;
  align-items: center;
  gap: 4px;
}

@media (max-width: 768px) {
  .shipment-card__route-point {
    flex-direction: column;
    align-items: flex-start;
  }

  .route-point__icon {
    margin-right: 0;
    margin-bottom: 2px;
  }

  .shipment-card__timestamps {
    flex-direction: column;
    gap: 4px;
  }
}
</style>