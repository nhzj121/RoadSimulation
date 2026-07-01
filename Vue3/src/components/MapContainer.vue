<template>
  <ElContainer class="page-container">
    <ElHeader class="header-navbar">
      <div class="navbar-content">
        <div class="navbar-left">
          <h2 class="navbar-title" @click="gotoMain">物流运输仿真系统</h2>
        </div>
        <div class="navbar-menu">
          <ElButton text @click="goToPOIManager">POI点管理</ElButton>
          <ElButton text @click="openMonitorPanel('vehicles')">车辆监控</ElButton>
          <ElButton text @click="openMonitorPanel('shipments')">运单监控</ElButton>
          <ElButton text @click="openMonitorPanel('assignments')">任务监控</ElButton>
          <ElButton type="primary" text @click="openRuntimeDashboard">
            成本监控
          </ElButton>
        </div>
      </div>
    </ElHeader>
    <ElContainer>
      <ElAside width="320px" class="side-panel">
        <div class="side-panel-scroll">

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
                      :min="1"
                      :max="200"
                      :step="1"
                      :marks="speedSliderMarks"
                      :format-tooltip="formatSpeedTooltip"
                      @change="onSpeedChange"
                      size="small"
                  />
                </div>
              </div>
              <div class="control-group" style="margin-top: 15px;">
                <span class="control-label">启发式:</span>
                <ElSwitch
                    v-model="useHeuristicDispatch"
                    :disabled="isSimulationRunning || isExperimentRunActive"
                    active-text="启用"
                    inactive-text="关闭"
                    inline-prompt
                />
              </div>
              <div class="control-group" style="margin-top: 15px;">
                <ElButton type="primary" :disabled="resetInProgress || hasPreparedExperimentScenario || isExperimentRunActive" @click="startSimulation">▶ 开始</ElButton>
                <ElButton type="primary" :disabled="resetInProgress || isExperimentRunActive" @click="pauseSimulation">⏸ 暂停</ElButton>
                <ElButton :loading="resetInProgress" :disabled="resetInProgress || isExperimentRunActive" @click="resetSimulation">↻ 重置</ElButton>
              </div>
              <div v-if="hasPreparedExperimentScenario" class="experiment-start-lock">
                已准备实验场景，请清除实验标记后再启动普通仿真。
              </div>
              <div class="speed-display" style="margin-top: 10px; font-size: 12px; color: #666;">
                当前速度: {{ formattedSpeed }}
              </div>
            </ElCard>
          </div>

          <div class="panel-section">
            <ElCard shadow="never" class="box-card shipment-control-card">
              <template #header>
                <div class="card-header">
                  <span>运单生成</span>
                </div>
              </template>

              <div class="shipment-control">
                <span class="control-label">生成数量:</span>
                <input type="number" class="custom-input" v-model.number="shipmentCount" min="1" />
                <ElButton type="primary" size="small" :disabled="isExperimentRunActive" @click="generateShipments">生成</ElButton>
              </div>

              <div class="task-sidebar" v-if="shipments && shipments.length > 0">
                <ul>
                  <li v-for="shipment in shipments" :key="shipment.id">
                    <span class="shipment-no">{{ shipment.refNo }}</span>
                    <span class="shipment-status">{{ shipment.status }}</span>
                  </li>
                </ul>
              </div>
            </ElCard>
          </div>

          <div class="panel-section">
            <ElCard shadow="never" class="box-card statistics-info">
              <template #header>
                <div class="card-header">
                  <span>统计信息</span>
                </div>
              </template>
              <div class="stats-info">
                <div><strong>运行车辆</strong><span>{{ vehicleMonitorDisplayVehicles.length }}</span></div>
                <div><strong>运输任务</strong><span>{{ displayTaskCount }}</span></div>
              </div>
            </ElCard>
          </div>
        </div>
      </ElAside>

      <ElMain>
        <div id="container"></div>
      </ElMain>

      <transition name="el-zoom-in-left">
        <ElAside v-show="isMonitorPanelVisible" width="340px" class="right-side-panel monitor-panel">
          <div class="monitor-header">
            <span>运行监控</span>
            <ElButton link type="info" @click="closeMonitorPanel">✖</ElButton>
          </div>
          <ElTabs v-model="activeMonitorTab" class="monitor-tabs">
            <ElTabPane label="车辆监控" name="vehicles">
              <div class="vehicle-monitor-list" ref="vehiclePanelScroll">
                <div
                    v-for="v in vehicleMonitorDisplayVehicles"
                    :key="v.id"
                    :id="`vehicle-item-${v.id}`"
                    class="vehicle-monitor-item"
                    :class="{ 'vehicle-item-highlighted': highlightedVehicleId === v.id }"
                    @click="focusVehicleFromPanel(v)"
                >
                  <span class="status-dot" :style="{ backgroundColor: statusMap[v.status]?.color || '#ccc' }"></span>
                  <div class="vehicle-monitor-main">
                    <div class="vehicle-monitor-title">
                      <span class="vehicle-id">{{ v.licensePlate }}</span>
                      <span class="vehicle-status-text">{{ statusMap[v.status]?.text || v.status || '未知' }}</span>
                    </div>
                    <div class="vehicle-monitor-desc">
                      {{ v.actionDescription || v.currentAssignment || '暂无任务信息' }}
                    </div>
                    <div class="vehicle-mini-metrics">
                      <span>载重 {{ v.currentLoad?.toFixed(1) || '0.0' }}/{{ v.maxLoadCapacity?.toFixed(1) || '0.0' }}t</span>
                      <div class="progress-bar mini-progress">
                        <div class="progress-fill load-progress" :style="{ width: `${v.loadPercentage || 0}%` }"></div>
                      </div>
                      <span>载容 {{ v.currentVolume?.toFixed(1) || '0.0' }}/{{ v.maxVolumeCapacity?.toFixed(1) || '0.0' }}m³</span>
                      <div class="progress-bar mini-progress">
                        <div class="progress-fill volume-progress" :style="{ width: `${v.volumePercentage || 0}%` }"></div>
                      </div>
                    </div>
                  </div>
                </div>
                <div v-if="vehicleMonitorDisplayVehicles.length === 0" class="no-vehicle monitor-empty">
                  暂无运输任务
                </div>
              </div>
            </ElTabPane>
            <ElTabPane label="运单监控" name="shipments">
              <div class="monitor-tab-body transport-monitor-list">
                <div
                    v-for="shipment in displayMonitorShipments"
                    :key="shipment.shipmentId"
                    class="transport-monitor-item"
                    :class="{ 'transport-item-highlighted': highlightedShipmentId === shipment.shipmentId }"
                    @click="focusShipmentFromPanel(shipment)"
                >
                  <div class="transport-item-title">
                    <span class="transport-main-text">{{ shipment.refNo || `运单${shipment.shipmentId}` }}</span>
                    <span class="transport-status">{{ shipment.statusText || shipment.status || '未知' }}</span>
                  </div>
                  <div class="transport-item-desc">
                    {{ shipment.originPOIName || '未知起点' }} → {{ shipment.destPOIName || '未知终点' }}
                  </div>
                  <div class="transport-progress-row">
                    <span>{{ shipment.completedItems || 0 }}/{{ shipment.totalItems || 0 }}</span>
                    <div class="progress-bar mini-progress">
                      <div class="progress-fill shipment-progress-fill" :style="{ width: `${Math.min(100, shipment.progressPercentage || 0)}%` }"></div>
                    </div>
                    <span>{{ (shipment.progressPercentage || 0).toFixed(0) }}%</span>
                  </div>
                  <div class="transport-meta-row">
                    <span>任务 {{ shipment.assignmentIds?.length || 0 }}</span>
                    <span>车辆 {{ shipment.vehicleIds?.length || 0 }}</span>
                  </div>
                </div>
                <div v-if="displayMonitorShipments.length === 0" class="monitor-empty">
                  暂无活跃运单
                </div>
              </div>
            </ElTabPane>
            <ElTabPane label="任务监控" name="assignments">
              <div class="monitor-tab-body transport-monitor-list">
                <div
                    v-for="assignment in displayMonitorAssignments"
                    :key="assignment.assignmentId"
                    class="transport-monitor-item"
                    :class="{ 'transport-item-highlighted': highlightedAssignmentId === assignment.assignmentId }"
                    @click="focusAssignmentFromPanel(assignment)"
                >
                  <div class="transport-item-title">
                    <span class="transport-main-text">任务 {{ assignment.assignmentId }}</span>
                    <span class="transport-status">{{ assignment.displayStatusText || assignment.statusText || assignment.status || '未知' }}</span>
                  </div>
                  <div class="transport-item-desc">
                    {{ assignment.startPOIName || '未知起点' }} → {{ assignment.endPOIName || '未知终点' }}
                  </div>
                  <div class="transport-item-desc">
                    {{ assignment.licensePlate || '未分配车辆' }} · {{ assignment.goodsName || '暂无货物信息' }}
                  </div>
                  <div class="transport-meta-row">
                    <span>运单 {{ assignment.shipmentRefNos?.join(', ') || '无' }}</span>
                    <span>数量 {{ assignment.quantity || 0 }}</span>
                  </div>
                </div>
                <div v-if="displayMonitorAssignments.length === 0" class="monitor-empty">
                  暂无活跃运输任务
                </div>
              </div>
            </ElTabPane>
          </ElTabs>
        </ElAside>
      </transition>

    </ElContainer>

    <!-- 成本趋势图对话框 -->
    <ElDialog
      v-model="chartVisible"
      :title="chartTitle"
      width="600px"
      @opened="initChart"
      @closed="disposeChart"
      destroy-on-close
    >
      <div ref="chartRef" style="width: 100%; height: 400px;"></div>
    </ElDialog>

    <div v-show="activeMainView === 'costDashboard'" class="runtime-dashboard-shell">
      <div class="runtime-dashboard">
        <div class="dashboard-topbar">
          <div>
            <div class="dashboard-eyebrow">Global Scheduling Monitor</div>
            <h2>调度效果监控大屏</h2>
          </div>
          <ElButton type="primary" plain @click="returnToMapView">返回地图</ElButton>
        </div>

        <div class="dashboard-hero" :class="`dashboard-hero--${normalizedLevel}`">
          <div class="dashboard-hero-main">
            <div class="dashboard-eyebrow">Dispatch Cost Index</div>
            <div class="dashboard-title-row">
              <span class="dashboard-title">调度效果成本指数</span>
              <span class="dashboard-status">{{ normalizedStatusText }}</span>
            </div>
            <div class="dashboard-score">{{ formatNormalizedValue(simulationCosts.normalizedAllCost) }}</div>
            <div class="dashboard-meta">
              <span>策略 {{ simulationCosts.baselineStrategy || '--' }}</span>
              <span>基准 {{ simulationCosts.baselinePercentile || '--' }}</span>
              <span>窗口 {{ runtimeCostDetail.window?.windowId || '等待完整窗口' }}</span>
            </div>
          </div>
          <div class="dashboard-kpis">
            <div v-for="item in dashboardKpis" :key="item.key" class="dashboard-kpi">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.note }}</small>
            </div>
          </div>
        </div>
        <div v-if="runtimeCostDetail.error" class="dashboard-alert">
          {{ runtimeCostDetail.error }}，当前大屏仅展示已缓存或基础成本监控数据。
        </div>

        <section class="dashboard-panel experiment-prep-panel">
          <div class="dashboard-panel-head">
            <div>
              <h3>策略对比实验准备</h3>
              <p>固定全部车辆初始 POI，并生成一批实验运单进入现有调度池</p>
            </div>
            <div class="experiment-actions">
              <ElButton size="small" :loading="experimentPrep.loading" @click="refreshExperimentPreparation">
                刷新实验状态
              </ElButton>
              <ElButton
                size="small"
                type="danger"
                plain
                :disabled="!hasPreparedExperimentScenario || experimentPrep.loading"
                @click="clearExperimentScenario"
              >
                清除实验标记
              </ElButton>
            </div>
          </div>

          <div v-if="experimentPrep.error" class="dashboard-alert experiment-alert">
            {{ experimentPrep.error }}
          </div>

          <div class="experiment-control-grid">
            <div class="experiment-control-block">
              <span>实验运单数量</span>
              <ElInputNumber
                v-model="experimentPrep.shipmentCount"
                :min="1"
                :max="20"
                :disabled="experimentPrep.loading || hasPreparedExperimentScenario"
                size="small"
              />
            </div>
            <div class="experiment-control-block">
              <span>实验车辆数</span>
              <strong>{{ experimentPrep.vehicleCount || experimentPrep.vehicles.length || 0 }}</strong>
            </div>
            <div class="experiment-control-block">
              <ElButton
                type="primary"
                :loading="experimentPrep.loading"
                :disabled="!canPrepareExperimentScenario"
                @click="prepareExperimentScenario"
              >
                准备实验场景
              </ElButton>
            </div>
          </div>

          <div class="experiment-note">
            准备实验前必须先手动重置普通仿真数据；普通仿真运行中不能准备实验。
          </div>

          <div class="experiment-placement-preview">
            <div class="experiment-placement-card experiment-placement-card--wide">
              <span>车辆初始化规则</span>
              <strong>{{ formatExperimentPlacementPolicy(experimentPrep.placementPolicy) }}</strong>
              <small>后端统一分配，前端不再手动选择 POI</small>
            </div>
            <div class="experiment-placement-card">
              <span>实验车辆</span>
              <strong>{{ experimentPrep.vehicleCount || experimentPrep.vehicles.length || 0 }}</strong>
              <small>全部车辆参与初始化</small>
            </div>
            <div class="experiment-placement-card">
              <span>候选初始 POI</span>
              <strong>{{ experimentPrep.candidateInitialPoiCount || 0 }}</strong>
              <small>仓库 / 配送中心</small>
            </div>
          </div>

          <div v-if="experimentPrep.current" class="experiment-summary">
            <div class="dashboard-section-title">当前实验场景</div>
            <div class="experiment-summary-grid">
              <div class="dashboard-metric-row">
                <span>实验ID</span>
                <strong>{{ experimentPrep.current.experimentId }}</strong>
              </div>
              <div class="dashboard-metric-row">
                <span>准备时间</span>
                <strong>{{ formatExperimentDateTime(experimentPrep.current.preparedAt) }}</strong>
              </div>
              <div class="dashboard-metric-row">
                <span>车辆数</span>
                <strong>{{ experimentPrep.current.vehicleCount || 0 }}</strong>
              </div>
              <div class="dashboard-metric-row">
                <span>实验运单数</span>
                <strong>{{ experimentPrep.current.shipmentCount || 0 }}</strong>
              </div>
            </div>

            <div class="experiment-placement-list">
              <div class="experiment-table-head experiment-table-head--placement">
                <span>Vehicle</span>
                <span>Initial POI</span>
                <span>Type</span>
              </div>
              <div
                v-for="position in experimentScenarioPositionPreview"
                :key="position.vehicleId"
                class="experiment-table-row experiment-table-row--placement"
              >
                <span>{{ position.licensePlate || `Vehicle ${position.vehicleId}` }}</span>
                <span>{{ position.poiName || `POI ${position.poiId}` }}</span>
                <span>{{ position.poiType || '--' }}</span>
              </div>
              <div v-if="experimentScenarioHiddenPositionCount > 0" class="dashboard-empty">
                还有 {{ experimentScenarioHiddenPositionCount }} 辆车的初始位置未展开显示
              </div>
            </div>

            <div class="experiment-shipment-list">
              <div class="experiment-table-head experiment-table-head--shipment">
                <span>模板</span>
                <span>路线</span>
                <span>货物</span>
                <span>数量</span>
                <span>任务项</span>
              </div>
              <div
                v-for="shipment in experimentPrep.current.shipments || []"
                :key="shipment.shipmentItemId || shipment.templateCode"
                class="experiment-table-row experiment-table-row--shipment"
              >
                <span>{{ shipment.templateCode }}</span>
                <span>{{ shipment.originPoiName }} → {{ shipment.destinationPoiName }}</span>
                <span>{{ shipment.goodsName || shipment.goodsSku }}</span>
                <strong>{{ shipment.quantity }}</strong>
                <span>{{ shipment.shipmentItemId }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="dashboard-panel experiment-run-panel">
          <div class="dashboard-panel-head">
            <div>
              <h3>实验运行与结果对比</h3>
              <p>按 ORIGINAL 到 HEURISTIC 自动运行同一实验场景</p>
            </div>
            <div class="experiment-actions">
              <ElButton size="small" :loading="experimentRun.loading" @click="refreshExperimentRunState">
                刷新运行状态
              </ElButton>
            </div>
          </div>

          <div v-if="experimentRun.error" class="dashboard-alert experiment-alert">
            {{ experimentRun.error }}
          </div>

          <div class="experiment-run-grid">
            <div class="experiment-run-status-card">
              <span>运行状态</span>
              <strong>{{ experimentRunStatusText }}</strong>
              <small>{{ experimentRun.status?.currentStrategy || '等待启动' }}</small>
            </div>
            <div class="experiment-run-status-card">
              <span>实验进度</span>
              <strong>{{ experimentRunProgressText }}</strong>
              <small>Loop {{ experimentRun.status?.currentLoop ?? 0 }} / {{ experimentRun.status?.maxLoops ?? '--' }}</small>
            </div>
            <div class="experiment-run-status-card">
              <span>当前成本指数</span>
              <strong>{{ formatNormalizedValue(experimentRun.status?.latestNormalizedAllCost) }}</strong>
              <small>AllCost {{ formatCostValue(experimentRun.status?.latestAllCost, 4) }}</small>
            </div>
          </div>

          <div class="experiment-run-actions">
            <ElButton
              type="primary"
              :loading="experimentRun.loading"
              :disabled="!canStartExperimentVisualRun"
              @click="startExperimentVisualRun"
            >
              开始实验运行
            </ElButton>
            <ElButton
              :disabled="!canPauseExperimentVisualRun"
              @click="pauseExperimentVisualRun"
            >
              暂停
            </ElButton>
            <ElButton
              :disabled="!canResumeExperimentVisualRun"
              @click="resumeExperimentVisualRun"
            >
              继续
            </ElButton>
            <ElButton
              type="danger"
              plain
              :disabled="!isExperimentRunActive || experimentRun.loading"
              @click="abortExperimentVisualRun"
            >
              中止
            </ElButton>
          </div>

          <div v-if="experimentRun.result" class="experiment-result-table">
            <div class="experiment-result-head">
              <span>策略</span>
              <span>实验级成本指数</span>
              <span>AllCost</span>
              <span>Cost A/B/C/D/E</span>
              <span>完成</span>
              <span>用车/任务</span>
            </div>
            <div
              v-for="row in experimentResultRows"
              :key="row.strategy"
              class="experiment-result-row"
            >
              <strong>{{ row.strategy }}</strong>
              <span>{{ row.normalizedAllCost }}</span>
              <span>{{ row.allCost }}</span>
              <span>{{ row.costs }}</span>
              <span>{{ row.completed }}</span>
              <span>{{ row.vehicleAndAssignment }}</span>
            </div>
          </div>
          <div v-else class="dashboard-empty">
            完成 ORIGINAL 与 HEURISTIC 两次运行后显示基础对比结果
          </div>

          <div class="experiment-optimization-panel">
            <div class="experiment-optimization-head">
              <div>
                <h4>调度优化分析</h4>
                <p>以实验级归一化 AllCost 成本指数为主指标，统一基准为 ORIGINAL P95，数值越低表示调度成本越低</p>
              </div>
              <span
                class="experiment-optimization-badge"
                :class="`experiment-optimization-badge--${experimentOptimizationSummary.level}`"
              >
                {{ experimentOptimizationSummary.label }}
              </span>
            </div>

            <div v-if="experimentOptimizationSummary.available" class="experiment-optimization-content">
              <div class="experiment-optimization-cards">
                <div
                  v-for="card in experimentOptimizationCards"
                  :key="card.label"
                  class="experiment-optimization-card"
                >
                  <span>{{ card.label }}</span>
                  <strong>{{ card.value }}</strong>
                  <small>{{ card.note }}</small>
                </div>
              </div>

              <div class="experiment-optimization-table experiment-optimization-table--dimension">
                <div class="experiment-optimization-table-head">
                  <span>实验级归一化维度</span>
                  <span>ORIGINAL</span>
                  <span>HEURISTIC</span>
                  <span>差值</span>
                  <span>改善率</span>
                  <span>原始差值</span>
                  <span>归因</span>
                </div>
                <div
                  v-for="row in experimentDimensionComparisonRows"
                  :key="row.key"
                  class="experiment-optimization-table-row"
                >
                  <strong>{{ row.label }}</strong>
                  <span>{{ row.original }}</span>
                  <span>{{ row.heuristic }}</span>
                  <span :class="row.deltaClass">{{ row.delta }}</span>
                  <span :class="row.rateClass">{{ row.improvementRate }}</span>
                  <span>{{ row.rawDelta }}</span>
                  <span>{{ row.badge }}</span>
                </div>
              </div>

              <div class="experiment-optimization-table experiment-optimization-table--efficiency">
                <div class="experiment-optimization-table-head">
                  <span>效率参考</span>
                  <span>ORIGINAL</span>
                  <span>HEURISTIC</span>
                  <span>差值</span>
                </div>
                <div
                  v-for="row in experimentEfficiencyComparisonRows"
                  :key="row.key"
                  class="experiment-optimization-table-row"
                >
                  <strong>{{ row.label }}</strong>
                  <span>{{ row.original }}</span>
                  <span>{{ row.heuristic }}</span>
                  <span>{{ row.delta }}</span>
                </div>
              </div>
            </div>
            <div v-else class="dashboard-empty">
              暂无实验级归一化结果：需要 ORIGINAL 与 HEURISTIC 均完成，且 ORIGINAL P95 基准和实验级成本指数有效
            </div>

            <div ref="experimentOptimizationTrendRef" class="dashboard-chart experiment-optimization-chart"></div>
          </div>
        </section>

        <div class="dashboard-grid dashboard-grid--charts">
          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>归一化综合成本趋势</h3>
                <p>相对 P95 基准的成本指数</p>
              </div>
            </div>
            <div ref="dashboardNormalizedCostTrendRef" class="dashboard-chart"></div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>原始 AllCost 趋势</h3>
                <p>后端 A-E 加权原始成本参考</p>
              </div>
            </div>
            <div ref="dashboardAllCostTrendRef" class="dashboard-chart"></div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>归一化维度分解</h3>
                <p>1.0 为 P95 基准线</p>
              </div>
            </div>
            <div ref="dashboardNormalizedRef" class="dashboard-chart"></div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>Cost 维度占比</h3>
                <p>A-E 聚合值占比，不等同公式参数</p>
              </div>
            </div>
            <div ref="dashboardCostShareRef" class="dashboard-chart"></div>
          </section>
        </div>

        <div class="dashboard-grid dashboard-grid--detail">
          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>调度窗口</h3>
                <p>归一化窗口计算链路</p>
              </div>
            </div>
            <div v-if="runtimeCostDetail.window" class="dashboard-metric-list">
              <div v-for="item in windowDetailMetrics" :key="item.label" class="dashboard-metric-row">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
            <div v-else class="dashboard-empty">等待完整调度窗口</div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>P95 基准</h3>
                <p>当前策略基准与权重</p>
              </div>
            </div>
            <div v-if="runtimeCostDetail.baseline" class="dashboard-metric-list">
              <div v-for="item in baselineDetailMetrics" :key="item.label" class="dashboard-metric-row">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
            <div v-else class="dashboard-empty">暂无基准详情</div>
          </section>

          <section class="dashboard-panel dashboard-panel--wide">
            <div class="dashboard-panel-head">
              <div>
                <h3>Cost A-E 对照</h3>
                <p>原始值、归一化值和基准值</p>
              </div>
            </div>
            <div class="dashboard-cost-table">
              <div class="dashboard-cost-table-head">
                <span>维度</span>
                <span>原始值</span>
                <span>归一化</span>
                <span>基准</span>
                <span>权重</span>
              </div>
              <div v-for="row in dashboardCostRows" :key="row.key" class="dashboard-cost-table-row">
                <span>{{ row.name }}</span>
                <strong>{{ row.raw }}</strong>
                <strong>{{ row.normalized }}</strong>
                <span>{{ row.baseline }}</span>
                <span>{{ row.weight }}</span>
              </div>
            </div>
          </section>
        </div>

        <div class="dashboard-section-title">公式参数贡献</div>
        <div class="dashboard-grid dashboard-grid--formula">
          <section v-for="group in formulaContributionGroups" :key="group.key" class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>{{ group.title }}</h3>
                <p>{{ group.subtitle }}</p>
              </div>
            </div>
            <div :ref="setFormulaChartRef(group.key)" class="dashboard-chart dashboard-chart--pie"></div>
            <div class="dashboard-metric-list dashboard-metric-list--compact">
              <div v-for="metric in group.metrics" :key="metric.label" class="dashboard-metric-row">
                <span>{{ metric.label }}</span>
                <strong>{{ metric.display }}</strong>
              </div>
            </div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>CostE 工作负载</h3>
                <p>不伪装为饼图，直接展示统计量</p>
              </div>
            </div>
            <div class="workload-metric-grid">
              <div v-for="item in costEWorkloadMetrics" :key="item.label" class="workload-metric">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>
        </div>

        <div class="dashboard-section-title">全局运行态势</div>
        <div class="dashboard-grid dashboard-grid--charts">
          <section class="dashboard-panel dashboard-panel--wide">
            <div class="dashboard-panel-head">
              <div>
                <h3>活跃对象趋势</h3>
                <p>运单、任务、车辆与任务项状态</p>
              </div>
            </div>
            <div ref="dashboardMonitorTrendRef" class="dashboard-chart"></div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>运单任务分布</h3>
                <p>等待、进行中、完成</p>
              </div>
            </div>
            <div ref="dashboardShipmentStateRef" class="dashboard-chart"></div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>任务状态分布</h3>
                <p>活跃 Assignment 状态</p>
              </div>
            </div>
            <div ref="dashboardAssignmentStatusRef" class="dashboard-chart"></div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>车辆状态分布</h3>
                <p>活跃车辆运行状态</p>
              </div>
            </div>
            <div ref="dashboardVehicleStatusRef" class="dashboard-chart"></div>
          </section>
        </div>

        <div class="dashboard-grid dashboard-grid--top">
          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>最高成本维度</h3>
                <p>归一化 A-E 最大项</p>
              </div>
            </div>
            <div class="dashboard-focus-card">
              <strong>{{ highestNormalizedCostDimension.name }}</strong>
              <span>{{ highestNormalizedCostDimension.value }}</span>
              <small>{{ highestNormalizedCostDimension.desc }}</small>
            </div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>等待运单 Top N</h3>
                <p>按 waitingItems 排序</p>
              </div>
            </div>
            <div class="dashboard-top-list">
              <div v-for="item in topWaitingShipments" :key="item.shipmentId" class="dashboard-top-row">
                <span>{{ item.refNo || `运单 ${item.shipmentId}` }}</span>
                <strong>{{ item.waitingItems || 0 }}</strong>
              </div>
              <div v-if="topWaitingShipments.length === 0" class="dashboard-empty">暂无等待运单</div>
            </div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>低进度运单 Top N</h3>
                <p>活跃运单进度最低</p>
              </div>
            </div>
            <div class="dashboard-top-list">
              <div v-for="item in topLowProgressShipments" :key="item.shipmentId" class="dashboard-top-row">
                <span>{{ item.refNo || `运单 ${item.shipmentId}` }}</span>
                <strong>{{ formatPercent(item.progressPercentage) }}</strong>
              </div>
              <div v-if="topLowProgressShipments.length === 0" class="dashboard-empty">暂无活跃运单</div>
            </div>
          </section>

          <section class="dashboard-panel">
            <div class="dashboard-panel-head">
              <div>
                <h3>高负载车辆 Top N</h3>
                <p>载重/载容利用率最大</p>
              </div>
            </div>
            <div class="dashboard-top-list">
              <div v-for="item in topUtilizedVehicles" :key="item.vehicleId" class="dashboard-top-row">
                <span>{{ item.licensePlate || `车辆 ${item.vehicleId}` }}</span>
                <strong>{{ item.display }}</strong>
              </div>
              <div v-if="topUtilizedVehicles.length === 0" class="dashboard-empty">暂无活跃车辆</div>
            </div>
          </section>
        </div>
      </div>
    </div>

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
  ElSlider,
  ElDialog,
  ElSwitch,
  ElTabs,
  ElTabPane,
  ElInputNumber
} from "element-plus";

let map = null;
let AMapLib = null; // 保存加载后的 AMap 构造对象
const shipmentCount = ref(1);
const shipments = ref([]);
const router = useRouter()
const goToPOIManager = () => {
  router.push('/poi-manager')
}
const gotoMain = () => {
  router.push('./')
}

// --- 车辆监控面板滚动相关 ---
const vehiclePanelScroll = ref(null); // 车辆监控滚动容器引用
const highlightedVehicleId = ref(null); // 当前高亮的车辆ID
const highlightedShipmentId = ref(null);
const highlightedAssignmentId = ref(null);
let highlightTimer = null; // 高亮定时器
const activeMainView = ref('map');

const handleVehicleClick = (vehicle) => {
  focusVehicleFromPanel(vehicle);
};

import { useVehicleArrivalMonitor } from '@/composables/useVehicleArrivalMonitor';

// 在您的组件逻辑中添加以下内容
// 假设您已经有以下响应式数据
// const vehiclePositions = ref([]); // 车辆位置数组
// const currentPOIs = ref([]); // POI列表

// 创建监控实例
const arrivalMonitor = useVehicleArrivalMonitor({
  checkInterval: 1000, // 每秒检查一次
  arrivalThreshold: 50, // 50米内视为到达
  preventDuplicateReports: true, // 防止重复上报
  duplicateTimeout: 30000 // 30秒内不上报重复事件
});

/**
 * 获取所有车辆的当前位置 - 修复版本
 * 从动画管理器中获取车辆实时位置
 */
const getVehiclePositions = () => {
  const positions = [];

  console.log(`[调试] animationManager 状态:`, {
    exists: !!animationManager,
    animationsCount: animationManager?.animations?.size || 0
  });

  if (animationManager && animationManager.animations) {
    console.log(`[调试] 动画列表:`, Array.from(animationManager.animations.entries()));

    animationManager.animations.forEach((animation, assignmentId) => {
      if (animation && animation.currentPosition && !animation.isCompleted) {
        console.log(`[调试] 找到动画:`, {
          assignmentId,
          vehicleId: animation.vehicleId,
          position: animation.currentPosition,
          isCompleted: animation.isCompleted
        });

        // ... 原有的处理逻辑
      }
    });
  }

  console.log(`[到达检测] 获取到 ${positions.length} 辆车辆的位置`);
  return positions;
};

// 获取POI列表的函数
const getPOIList = () => {
  // 根据您的实际数据结构进行调整
  return currentPOIs.value.map(poi => ({
    id: poi.id,
    name: poi.name,
    longitude: poi.longitude,
    latitude: poi.latitude,
    radius: poi.radius // 如果有自定义半径
  }));
};

// 在仿真开始时启动监控
const startSimulationWithMonitoring = async () => {
  // 调用您现有的启动仿真方法
  // await yourExistingStartSimulationFunction();

  // 启动车辆到达监控
  arrivalMonitor.startMonitoring(getVehiclePositions, getPOIList);

  console.log('车辆到达监控已启动');
};

// 在仿真停止时停止监控
const stopSimulationWithMonitoring = () => {
  // 调用您现有的停止仿真方法
  // yourExistingStopSimulationFunction();

  // 停止车辆到达监控
  arrivalMonitor.stopMonitoring();
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

    if (vehicleElement && vehiclePanelScroll.value) {
      // 计算滚动位置
      const scrollContainer = vehiclePanelScroll.value;
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

      // 设置定时器，短暂保留高亮，方便在大量车辆中定位
      highlightTimer = setTimeout(() => {
        highlightedVehicleId.value = null;
      }, 1500);
    } else {
      console.warn(`未找到车辆 ${vehicleId} 的元素或滚动容器未初始化`);
    }
  });
};

// --- 右侧监控面板相关状态 ---
const isMonitorPanelVisible = ref(false);
const activeMonitorTab = ref('vehicles');

const resizeMapAfterPanelChange = () => {
  setTimeout(() => {
    map?.resize?.();
  }, 80);
};

const openMonitorPanel = async (tab = 'vehicles') => {
  if (tab === 'costs') {
    await openRuntimeDashboard();
    return;
  }
  activeMainView.value = 'map';
  disposeRuntimeDashboardCharts();
  activeMonitorTab.value = tab;
  isMonitorPanelVisible.value = true;
  if (['vehicles', 'shipments', 'assignments'].includes(tab)) {
    try {
      await updateVehicleInfo();
    } catch (error) {
      console.error('刷新运输监控数据失败:', error);
    }
  }
  await nextTick();
  resizeMapAfterPanelChange();
};

const closeMonitorPanel = async () => {
  isMonitorPanelVisible.value = false;
  await nextTick();
  resizeMapAfterPanelChange();
};

const toggleCostPanel = async () => {
  await openRuntimeDashboard();
};

const simulationCosts = reactive({
  costA: 0.0,
  costB: 0.0,
  costC: 0.0,
  costD: 0.0,
  costE: 0.0,
  allCost: 0.0,
  normalizedCostA: null,
  normalizedCostB: null,
  normalizedCostC: null,
  normalizedCostD: null,
  normalizedCostE: null,
  normalizedAllCost: null,
  baselinePercentile: '',
  baselineStrategy: ''
});

// 获取实时成本的接口请求
const costHistory = reactive({
  times: [],
  costA: [],
  costB: [],
  costC: [],
  costD: [],
  costE: [],
  allCost: [],
  normalizedCostA: [],
  normalizedCostB: [],
  normalizedCostC: [],
  normalizedCostD: [],
  normalizedCostE: [],
  normalizedAllCost: []
});

const runtimeCostDetail = reactive({
  generatedAt: '',
  summary: null,
  costA: null,
  costB: null,
  costC: null,
  costD: null,
  costE: null,
  window: null,
  baseline: null,
  error: ''
});

const experimentPrep = reactive({
  shipmentCount: 10,
  vehicles: [],
  vehicleCount: 0,
  candidateInitialPoiCount: 0,
  placementPolicy: '',
  current: null,
  loading: false,
  error: ''
});

const experimentRun = reactive({
  status: null,
  result: null,
  loading: false,
  error: ''
});

const dashboardHistory = reactive({
  times: [],
  activeShipments: [],
  activeAssignments: [],
  activeVehicles: [],
  waitingItems: [],
  inProgressItems: [],
  completedItems: [],
  avgLoadUsage: [],
  avgVolumeUsage: []
});

const DASHBOARD_HISTORY_LIMIT = 60;

const unwrapApiData = (response) => {
  const payload = response?.data;
  if (payload && Object.prototype.hasOwnProperty.call(payload, 'success')) {
    return payload.data;
  }
  return payload;
};

const hasPreparedExperimentScenario = computed(() => !!experimentPrep.current?.experimentId);

const EXPERIMENT_ACTIVE_STATUSES = new Set([
  'RUNNING_ORIGINAL',
  'PAUSED_ORIGINAL',
  'REBUILDING_FOR_HEURISTIC',
  'RUNNING_HEURISTIC',
  'PAUSED_HEURISTIC'
]);

const EXPERIMENT_PAUSED_STATUSES = new Set([
  'PAUSED_ORIGINAL',
  'PAUSED_HEURISTIC'
]);

const EXPERIMENT_RUNNING_STATUSES = new Set([
  'RUNNING_ORIGINAL',
  'RUNNING_HEURISTIC'
]);

const isExperimentRunActive = computed(() => {
  const status = experimentRun.status?.status;
  return EXPERIMENT_ACTIVE_STATUSES.has(status);
});

const isExperimentRunExecuting = computed(() => {
  const status = experimentRun.status?.status;
  return EXPERIMENT_RUNNING_STATUSES.has(status);
});

const canStartExperimentVisualRun = computed(() => {
  return hasPreparedExperimentScenario.value
      && !experimentRun.loading
      && !isExperimentRunActive.value
      && !isSimulationRunning.value;
});

const canPauseExperimentVisualRun = computed(() => {
  const status = experimentRun.status?.status;
  return !experimentRun.loading && (status === 'RUNNING_ORIGINAL' || status === 'RUNNING_HEURISTIC');
});

const canResumeExperimentVisualRun = computed(() => {
  const status = experimentRun.status?.status;
  return !experimentRun.loading && EXPERIMENT_PAUSED_STATUSES.has(status);
});

const experimentRunStatusText = computed(() => {
  const status = experimentRun.status?.status || 'IDLE';
  const labels = {
    IDLE: '未启动',
    PREPARED: '已准备',
    RUNNING_ORIGINAL: 'ORIGINAL 运行中',
    PAUSED_ORIGINAL: 'ORIGINAL 已暂停',
    REBUILDING_FOR_HEURISTIC: '重建 HEURISTIC 场景',
    RUNNING_HEURISTIC: 'HEURISTIC 运行中',
    PAUSED_HEURISTIC: 'HEURISTIC 已暂停',
    COMPLETED: '实验完成',
    ABORTED: '已中止',
    FAILED: '运行失败'
  };
  return labels[status] || status;
});

const experimentRunProgressText = computed(() => {
  const completed = experimentRun.status?.completedItems ?? 0;
  const total = experimentRun.status?.totalItems ?? 0;
  return `${completed} / ${total}`;
});

const experimentResultRows = computed(() => {
  const result = experimentRun.result;
  if (!result) {
    return [];
  }
  return [result.original, result.heuristic]
      .filter(Boolean)
      .map((item) => ({
        strategy: item.strategy,
        normalizedAllCost: formatNormalizedValue(item.experimentNormalizedAllCost),
        allCost: formatCostValue(item.allCost, 4),
        costs: [
          formatCostValue(item.costA, 2),
          formatCostValue(item.costB, 2),
          formatCostValue(item.costC, 2),
          formatCostValue(item.costD, 2),
          formatCostValue(item.costE, 4)
        ].join(' / '),
        completed: `${item.completedItems || 0} / ${item.totalItems || 0}`,
        vehicleAndAssignment: `${item.vehicleUsedCount || 0} / ${item.assignmentCount || 0}`
      }));
});

const EXPERIMENT_OPTIMIZATION_NEUTRAL_THRESHOLD = 0.01;

const experimentNumber = (value) => {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
};

const isCompletedStrategyResult = (item) => item?.status === 'COMPLETED';

const calculateImprovementRate = (originalValue, heuristicValue) => {
  const originalNumber = experimentNumber(originalValue);
  const heuristicNumber = experimentNumber(heuristicValue);
  if (originalNumber === null || heuristicNumber === null || originalNumber === 0) {
    return null;
  }
  return (originalNumber - heuristicNumber) / originalNumber;
};

const formatExperimentSignedNumber = (value, digits = 4) => {
  const numberValue = experimentNumber(value);
  if (numberValue === null) {
    return '--';
  }
  const sign = numberValue > 0 ? '+' : '';
  return `${sign}${numberValue.toFixed(digits)}`;
};

const formatExperimentRate = (rate, digits = 2) => {
  const numberValue = experimentNumber(rate);
  if (numberValue === null) {
    return '--';
  }
  const percentage = numberValue * 100;
  const sign = percentage > 0 ? '+' : '';
  return `${sign}${percentage.toFixed(digits)}%`;
};

const experimentOptimizationSummary = computed(() => {
  const original = experimentRun.result?.original;
  const heuristic = experimentRun.result?.heuristic;
  const originalCost = experimentNumber(original?.experimentNormalizedAllCost);
  const heuristicCost = experimentNumber(heuristic?.experimentNormalizedAllCost);
  if (!isCompletedStrategyResult(original)
      || !isCompletedStrategyResult(heuristic)
      || originalCost === null
      || heuristicCost === null
      || originalCost === 0) {
    return {
      available: false,
      level: 'pending',
      label: '暂无实验级结果',
      normalizedAllCostDelta: null,
      normalizedAllCostImprovementRate: null
    };
  }

  const improvementRate = calculateImprovementRate(originalCost, heuristicCost);
  const delta = heuristicCost - originalCost;
  let level = 'neutral';
  let label = '基本持平';
  if (improvementRate > EXPERIMENT_OPTIMIZATION_NEUTRAL_THRESHOLD) {
    level = 'good';
    label = '启发式优化';
  } else if (improvementRate < -EXPERIMENT_OPTIMIZATION_NEUTRAL_THRESHOLD) {
    level = 'danger';
    label = '启发式退化';
  }

  return {
    available: true,
    level,
    label,
    normalizedAllCostDelta: delta,
    normalizedAllCostImprovementRate: improvementRate,
    original,
    heuristic
  };
});

const experimentOptimizationCards = computed(() => {
  const summary = experimentOptimizationSummary.value;
  if (!summary.available) {
    return [];
  }
  const original = summary.original;
  const heuristic = summary.heuristic;
  const allCostRate = calculateImprovementRate(original?.allCost, heuristic?.allCost);
  const originalAllCost = experimentNumber(original?.allCost);
  const heuristicAllCost = experimentNumber(heuristic?.allCost);
  const allCostDelta = originalAllCost === null || heuristicAllCost === null
      ? null
      : heuristicAllCost - originalAllCost;
  return [
    {
      label: '实验级成本指数优化率',
      value: formatExperimentRate(summary.normalizedAllCostImprovementRate),
      note: '正值表示 HEURISTIC 更优'
    },
    {
      label: '实验级成本指数差值',
      value: formatExperimentSignedNumber(summary.normalizedAllCostDelta, 4),
      note: 'HEURISTIC - ORIGINAL，负值更优'
    },
    {
      label: '原始 AllCost 改善率',
      value: formatExperimentRate(allCostRate),
      note: `差值 ${formatExperimentSignedNumber(allCostDelta, 4)}`
    }
  ];
});

const experimentCostDimensionDefs = [
  { key: 'A', label: 'Cost A', normalizedKey: 'experimentNormalizedCostA', rawKey: 'costA', digits: 4 },
  { key: 'B', label: 'Cost B', normalizedKey: 'experimentNormalizedCostB', rawKey: 'costB', digits: 4 },
  { key: 'C', label: 'Cost C', normalizedKey: 'experimentNormalizedCostC', rawKey: 'costC', digits: 4 },
  { key: 'D', label: 'Cost D', normalizedKey: 'experimentNormalizedCostD', rawKey: 'costD', digits: 4 },
  { key: 'E', label: 'Cost E', normalizedKey: 'experimentNormalizedCostE', rawKey: 'costE', digits: 4 }
];

const experimentDimensionComparisonRows = computed(() => {
  const summary = experimentOptimizationSummary.value;
  if (!summary.available) {
    return [];
  }
  const rows = experimentCostDimensionDefs.map(def => {
    const originalValue = experimentNumber(summary.original?.[def.normalizedKey]);
    const heuristicValue = experimentNumber(summary.heuristic?.[def.normalizedKey]);
    const delta = originalValue === null || heuristicValue === null ? null : heuristicValue - originalValue;
    const improvementRate = calculateImprovementRate(originalValue, heuristicValue);
    const rawOriginal = experimentNumber(summary.original?.[def.rawKey]);
    const rawHeuristic = experimentNumber(summary.heuristic?.[def.rawKey]);
    const rawDelta = rawOriginal === null || rawHeuristic === null ? null : rawHeuristic - rawOriginal;
    return {
      key: def.key,
      label: def.label,
      original: originalValue === null ? '--' : originalValue.toFixed(def.digits),
      heuristic: heuristicValue === null ? '--' : heuristicValue.toFixed(def.digits),
      delta: formatExperimentSignedNumber(delta, def.digits),
      deltaValue: delta,
      improvementRate: formatExperimentRate(improvementRate),
      improvementRateValue: improvementRate,
      rawDelta: formatExperimentSignedNumber(rawDelta, def.key === 'E' ? 4 : 2),
      badge: '--'
    };
  });

  const validRows = rows.filter(row => row.improvementRateValue !== null);
  if (validRows.length > 0) {
    const bestRow = validRows.reduce((best, row) =>
      row.improvementRateValue > best.improvementRateValue ? row : best
    );
    const worstRow = validRows.reduce((worst, row) =>
      row.improvementRateValue < worst.improvementRateValue ? row : worst
    );
    if (bestRow.improvementRateValue > 0) {
      bestRow.badge = '改善最大';
    }
    if (worstRow.improvementRateValue < 0) {
      worstRow.badge = worstRow.badge === '改善最大' ? '波动最大' : '退化最大';
    }
  }

  return rows.map(row => ({
    ...row,
    deltaClass: row.deltaValue < 0 ? 'is-good' : row.deltaValue > 0 ? 'is-danger' : '',
    rateClass: row.improvementRateValue > 0 ? 'is-good' : row.improvementRateValue < 0 ? 'is-danger' : ''
  }));
});

const experimentEfficiencyComparisonRows = computed(() => {
  const summary = experimentOptimizationSummary.value;
  if (!summary.available) {
    return [];
  }
  return [
    { key: 'loopCount', label: '完成轮次', digits: 0 },
    { key: 'vehicleUsedCount', label: '用车数', digits: 0 },
    { key: 'assignmentCount', label: '派车任务数', digits: 0 }
  ].map(item => {
    const originalValue = experimentNumber(summary.original?.[item.key]);
    const heuristicValue = experimentNumber(summary.heuristic?.[item.key]);
    const delta = originalValue === null || heuristicValue === null ? null : heuristicValue - originalValue;
    return {
      key: item.key,
      label: item.label,
      original: originalValue === null ? '--' : originalValue.toFixed(item.digits),
      heuristic: heuristicValue === null ? '--' : heuristicValue.toFixed(item.digits),
      delta: formatExperimentSignedNumber(delta, item.digits)
    };
  });
});

const canPrepareExperimentScenario = computed(() => {
  return !experimentPrep.loading
      && !isSimulationRunning.value
      && !isExperimentRunActive.value
      && !hasPreparedExperimentScenario.value
      && experimentPrep.shipmentCount >= 1
      && experimentPrep.shipmentCount <= 20
      && (experimentPrep.vehicleCount > 0 || experimentPrep.vehicles.length > 0)
      && experimentPrep.candidateInitialPoiCount > 0;
});

const experimentScenarioPositionPreview = computed(() => {
  const positions = experimentPrep.current?.vehicleInitialPositions;
  return Array.isArray(positions) ? positions.slice(0, 12) : [];
});

const experimentScenarioHiddenPositionCount = computed(() => {
  const positions = experimentPrep.current?.vehicleInitialPositions;
  return Array.isArray(positions) ? Math.max(0, positions.length - experimentScenarioPositionPreview.value.length) : 0;
});

const formatExperimentPlacementPolicy = (policy) => {
  if (policy === 'VEHICLE_ID_AND_INITIAL_POI_ID_ROUND_ROBIN') {
    return '车辆ID排序 + 初始POI排序轮转分配';
  }
  return policy || '等待实验选项数据';
};

const formatExperimentDateTime = (value) => {
  if (!value) {
    return '--';
  }
  return String(value).replace('T', ' ').slice(0, 19);
};

const fetchExperimentVehiclesAndPois = async () => {
  const response = await request.get('/api/simulation/experiments/dispatch-comparison/options');
  const optionsData = unwrapApiData(response) || {};
  const vehiclesData = optionsData.vehicles || [];
  experimentPrep.vehicles = Array.isArray(vehiclesData)
      ? [...vehiclesData].sort((a, b) => (a.vehicleId || 0) - (b.vehicleId || 0))
      : [];
  experimentPrep.vehicleCount = Number(optionsData.vehicleCount ?? experimentPrep.vehicles.length) || 0;
  experimentPrep.candidateInitialPoiCount = Number(optionsData.candidateInitialPoiCount ?? 0) || 0;
  experimentPrep.placementPolicy = optionsData.placementPolicy || '';
};

const fetchCurrentExperimentScenario = async () => {
  const response = await request.get('/api/simulation/experiments/dispatch-comparison/current');
  experimentPrep.current = unwrapApiData(response) || null;
  return experimentPrep.current;
};

const refreshExperimentPreparation = async () => {
  experimentPrep.loading = true;
  experimentPrep.error = '';
  try {
    await Promise.all([
      fetchExperimentVehiclesAndPois(),
      fetchCurrentExperimentScenario()
    ]);
  } catch (error) {
    experimentPrep.error = error?.response?.data?.message || error?.response?.data?.error || '实验准备数据读取失败';
    console.error('实验准备数据读取失败:', error);
  } finally {
    experimentPrep.loading = false;
  }
};

const prepareExperimentScenario = async () => {
  if (!canPrepareExperimentScenario.value) {
    if (isSimulationRunning.value) {
      ElMessage.warning('普通仿真运行中，无法准备实验场景');
    } else if (hasPreparedExperimentScenario.value) {
      ElMessage.warning('已存在实验场景，请先清除实验标记并手动重置');
    } else {
      ElMessage.warning('实验准备条件不完整，请先刷新实验状态');
    }
    return;
  }

  experimentPrep.loading = true;
  experimentPrep.error = '';
  try {
    const payload = {
      shipmentCount: experimentPrep.shipmentCount
    };
    const response = await request.post('/api/simulation/experiments/dispatch-comparison/prepare', payload);
    experimentPrep.current = unwrapApiData(response) || null;
    ElMessage.success('实验场景准备完成');
    await fetchExperimentVehiclesAndPois();
  } catch (error) {
    const message = error?.response?.data?.message || error?.response?.data?.error || '实验场景准备失败';
    experimentPrep.error = message;
    ElMessage.error(message);
    console.error('实验场景准备失败:', error);
  } finally {
    experimentPrep.loading = false;
  }
};

const clearExperimentScenario = async () => {
  experimentPrep.loading = true;
  experimentPrep.error = '';
  try {
    await request.delete('/api/simulation/experiments/dispatch-comparison/current');
    experimentPrep.current = null;
    ElMessage.success('实验场景标记已清除');
  } catch (error) {
    const message = error?.response?.data?.message || error?.response?.data?.error || '清除实验标记失败';
    experimentPrep.error = message;
    ElMessage.error(message);
    console.error('清除实验标记失败:', error);
  } finally {
    experimentPrep.loading = false;
  }
};

const clearExperimentAssignmentVisuals = () => {
  if (animationManager) {
    animationManager.stopAll();
  }
  cleanupAllActiveRoutes();
  routePlanningCache.clear();
  assignmentStates.clear();
  drawnAssignmentIds.value = new Set();
  arrivalAckInFlight.clear();
  arrivalAckCompleted.clear();
  syncRegisteredVehicleStats();
};

const handleExperimentRunStatusTransition = (previousStatus, previousStrategy, nextStatusPayload) => {
  const nextStatus = nextStatusPayload?.status || null;
  const nextStrategy = nextStatusPayload?.currentStrategy || null;
  if (!nextStatus || (previousStatus === nextStatus && previousStrategy === nextStrategy)) {
    return;
  }

  if (nextStatus === 'REBUILDING_FOR_HEURISTIC') {
    invalidateSimulationGeneration();
    clearExperimentAssignmentVisuals();
    return;
  }

  if (['COMPLETED', 'ABORTED', 'FAILED'].includes(nextStatus)) {
    invalidateSimulationGeneration();
    return;
  }

  if ((nextStatus === 'RUNNING_ORIGINAL' || nextStatus === 'RUNNING_HEURISTIC')
      && (previousStatus !== nextStatus || previousStrategy !== nextStrategy)) {
    if (previousStatus) {
      clearExperimentAssignmentVisuals();
    }
    const runGeneration = beginSimulationGeneration();
    scheduleAssignmentDrawing(fetchCurrentAssignments, runGeneration, `experiment ${nextStrategy || 'strategy'} assignments`);
  }
};

const fetchExperimentRunStatus = async () => {
  const previousStatus = experimentRun.status?.status || null;
  const previousStrategy = experimentRun.status?.currentStrategy || null;
  const response = await request.get('/api/simulation/experiments/dispatch-comparison/run-status');
  experimentRun.status = unwrapApiData(response) || null;
  handleExperimentRunStatusTransition(previousStatus, previousStrategy, experimentRun.status);
  return experimentRun.status;
};

const fetchLatestExperimentRunResult = async () => {
  const response = await request.get('/api/simulation/experiments/dispatch-comparison/latest-result');
  experimentRun.result = unwrapApiData(response) || null;
  updateRuntimeDashboardCharts();
  return experimentRun.result;
};

const showExperimentResultDashboard = async () => {
  activeMainView.value = 'costDashboard';
  isMonitorPanelVisible.value = false;
  await nextTick();
  if (Object.keys(runtimeDashboardChartInstances).length === 0) {
    initRuntimeDashboardCharts();
  } else {
    updateRuntimeDashboardCharts();
  }
};

const refreshExperimentRunState = async () => {
  experimentRun.loading = true;
  experimentRun.error = '';
  try {
    await Promise.all([
      fetchExperimentRunStatus(),
      fetchLatestExperimentRunResult()
    ]);
  } catch (error) {
    const message = error?.response?.data?.message || error?.response?.data?.error || '实验运行状态读取失败';
    experimentRun.error = message;
    console.error('实验运行状态读取失败:', error);
  } finally {
    experimentRun.loading = false;
  }
};

const syncExperimentRunAfterStatusRefresh = async () => {
  try {
    const previousStatus = experimentRun.status?.status || null;
    await fetchExperimentRunStatus();
    const status = experimentRun.status?.status;
    if (['COMPLETED', 'ABORTED', 'FAILED'].includes(status)) {
      isSimulationRunning.value = false;
      stopSimulationTimer();
      invalidateSimulationGeneration();
      clearFrontendSimulationVisuals();
      await fetchLatestExperimentRunResult();
      if (status === 'COMPLETED' && experimentRun.result) {
        await showExperimentResultDashboard();
      }
      return;
    }

    if (previousStatus === 'RUNNING_HEURISTIC' && status === 'PREPARED') {
      const result = await fetchLatestExperimentRunResult();
      if (result) {
        isSimulationRunning.value = false;
        stopSimulationTimer();
        invalidateSimulationGeneration();
        clearFrontendSimulationVisuals();
        await showExperimentResultDashboard();
      }
    }
  } catch (error) {
    console.error('实验运行状态同步失败:', error);
  }
};

const startExperimentVisualRun = async () => {
  if (!canStartExperimentVisualRun.value) {
    ElMessage.warning('实验运行条件不满足，请先准备实验场景并停止普通仿真');
    return;
  }
  experimentRun.loading = true;
  experimentRun.error = '';
  try {
    activeMainView.value = 'map';
    disposeRuntimeDashboardCharts();
    await nextTick();
    resizeMapAfterPanelChange();

    speedFactor.value = 100;
    onSpeedChange(100);

    arrivalAckInFlight.clear();
    arrivalAckCompleted.clear();
    const runGeneration = beginSimulationGeneration();
    const response = await request.post('/api/simulation/experiments/dispatch-comparison/start-visual-run');
    experimentRun.status = unwrapApiData(response) || null;
    experimentRun.result = null;
    isSimulationRunning.value = true;
    if (animationManager) {
      animationManager.resumeAll();
    }
    startSimulationTimer();
    arrivalMonitor.startMonitoring(getVehiclePositions, getPOIList);
    scheduleAssignmentDrawing(fetchCurrentAssignments, runGeneration, 'experiment initial assignments');
    ElMessage.success('实验运行已启动');
  } catch (error) {
    const message = error?.response?.data?.message || error?.response?.data?.error || '实验运行启动失败';
    experimentRun.error = message;
    isSimulationRunning.value = false;
    invalidateSimulationGeneration();
    ElMessage.error(message);
    console.error('实验运行启动失败:', error);
  } finally {
    experimentRun.loading = false;
  }
};

const pauseExperimentVisualRun = async () => {
  if (!canPauseExperimentVisualRun.value) {
    return;
  }
  experimentRun.loading = true;
  experimentRun.error = '';
  try {
    const response = await request.post('/api/simulation/experiments/dispatch-comparison/pause');
    experimentRun.status = unwrapApiData(response) || null;
    isSimulationRunning.value = false;
    stopSimulationTimer();
    invalidateSimulationGeneration();
    if (animationManager) {
      animationManager.pauseAll();
    }
    ElMessage.success('实验运行已暂停');
  } catch (error) {
    const message = error?.response?.data?.message || error?.response?.data?.error || '暂停实验失败';
    experimentRun.error = message;
    ElMessage.error(message);
  } finally {
    experimentRun.loading = false;
  }
};

const resumeExperimentVisualRun = async () => {
  if (!canResumeExperimentVisualRun.value) {
    return;
  }
  experimentRun.loading = true;
  experimentRun.error = '';
  try {
    const response = await request.post('/api/simulation/experiments/dispatch-comparison/resume');
    experimentRun.status = unwrapApiData(response) || null;
    const runGeneration = beginSimulationGeneration();
    isSimulationRunning.value = true;
    startSimulationTimer();
    if (animationManager) {
      animationManager.resumeAll();
    }
    scheduleAssignmentDrawing(fetchCurrentAssignments, runGeneration, 'experiment resumed assignments');
    ElMessage.success('实验运行已继续');
  } catch (error) {
    const message = error?.response?.data?.message || error?.response?.data?.error || '继续实验失败';
    experimentRun.error = message;
    ElMessage.error(message);
  } finally {
    experimentRun.loading = false;
  }
};

const abortExperimentVisualRun = async () => {
  if (!isExperimentRunActive.value) {
    return;
  }
  experimentRun.loading = true;
  experimentRun.error = '';
  try {
    const response = await request.post('/api/simulation/experiments/dispatch-comparison/abort');
    experimentRun.status = unwrapApiData(response) || null;
    isSimulationRunning.value = false;
    stopSimulationTimer();
    invalidateSimulationGeneration();
    clearFrontendSimulationVisuals();
    ElMessage.success('实验运行已中止');
  } catch (error) {
    const message = error?.response?.data?.message || error?.response?.data?.error || '中止实验失败';
    experimentRun.error = message;
    ElMessage.error(message);
  } finally {
    experimentRun.loading = false;
  }
};

const resetRuntimeCostDetail = () => {
  runtimeCostDetail.generatedAt = '';
  runtimeCostDetail.summary = null;
  runtimeCostDetail.costA = null;
  runtimeCostDetail.costB = null;
  runtimeCostDetail.costC = null;
  runtimeCostDetail.costD = null;
  runtimeCostDetail.costE = null;
  runtimeCostDetail.window = null;
  runtimeCostDetail.baseline = null;
  runtimeCostDetail.error = '';
};

const clearDashboardHistory = () => {
  dashboardHistory.times.splice(0);
  dashboardHistory.activeShipments.splice(0);
  dashboardHistory.activeAssignments.splice(0);
  dashboardHistory.activeVehicles.splice(0);
  dashboardHistory.waitingItems.splice(0);
  dashboardHistory.inProgressItems.splice(0);
  dashboardHistory.completedItems.splice(0);
  dashboardHistory.avgLoadUsage.splice(0);
  dashboardHistory.avgVolumeUsage.splice(0);
};

const resetSimulationCostDisplay = () => {
  simulationCosts.costA = 0.0;
  simulationCosts.costB = 0.0;
  simulationCosts.costC = 0.0;
  simulationCosts.costD = 0.0;
  simulationCosts.costE = 0.0;
  simulationCosts.allCost = 0.0;
  simulationCosts.normalizedCostA = null;
  simulationCosts.normalizedCostB = null;
  simulationCosts.normalizedCostC = null;
  simulationCosts.normalizedCostD = null;
  simulationCosts.normalizedCostE = null;
  simulationCosts.normalizedAllCost = null;
  simulationCosts.baselinePercentile = '';
  simulationCosts.baselineStrategy = '';

  costHistory.times.splice(0);
  costHistory.costA.splice(0);
  costHistory.costB.splice(0);
  costHistory.costC.splice(0);
  costHistory.costD.splice(0);
  costHistory.costE.splice(0);
  costHistory.allCost.splice(0);
  costHistory.normalizedCostA.splice(0);
  costHistory.normalizedCostB.splice(0);
  costHistory.normalizedCostC.splice(0);
  costHistory.normalizedCostD.splice(0);
  costHistory.normalizedCostE.splice(0);
  costHistory.normalizedAllCost.splice(0);
  resetRuntimeCostDetail();
  clearDashboardHistory();
  activeMainView.value = 'map';
  disposeRuntimeDashboardCharts();

  if (chartVisible.value && chartInstance) {
    updateChart();
  }
};

const isFiniteCostValue = (value) => typeof value === 'number' && Number.isFinite(value);

const readCostNumber = (value) => {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : 0;
};

const readOptionalCostNumber = (value) => {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
};

const formatCostValue = (value, digits = 2) => readCostNumber(value).toFixed(digits);

const formatNormalizedValue = (value) => {
  if (!isFiniteCostValue(value)) {
    return '--';
  }
  return value.toFixed(4);
};

const hasNormalizedCost = computed(() => isFiniteCostValue(simulationCosts.normalizedAllCost));

const normalizedLevel = computed(() => {
  if (!hasNormalizedCost.value) {
    return 'pending';
  }
  if (simulationCosts.normalizedAllCost < 1.0) {
    return 'good';
  }
  if (simulationCosts.normalizedAllCost <= 1.05) {
    return 'warning';
  }
  return 'danger';
});

const normalizedStatusText = computed(() => {
  if (!hasNormalizedCost.value) {
    return '等待完整调度窗口';
  }
  if (simulationCosts.normalizedAllCost < 1.0) {
    return '低于P95基准';
  }
  if (simulationCosts.normalizedAllCost <= 1.05) {
    return '接近P95基准';
  }
  return '超过P95基准';
});

const normalizedCostMetrics = computed(() => [
  {
    key: 'normalizedCostA',
    name: '直接成本 A',
    desc: '等待与空驶窗口单位成本',
    value: simulationCosts.normalizedCostA,
    normalizedChartType: 'NA'
  },
  {
    key: 'normalizedCostB',
    name: '效率成本 B',
    desc: '空驶率、等待率与空等惩罚',
    value: simulationCosts.normalizedCostB,
    normalizedChartType: 'NB'
  },
  {
    key: 'normalizedCostC',
    name: '运能损耗 C',
    desc: '理论与实际运能窗口单位差',
    value: simulationCosts.normalizedCostC,
    normalizedChartType: 'NC'
  },
  {
    key: 'normalizedCostD',
    name: '经济损耗 D',
    desc: '窗口经济损耗代理单位值',
    value: simulationCosts.normalizedCostD,
    normalizedChartType: 'ND'
  },
  {
    key: 'normalizedCostE',
    name: '负载均衡 E',
    desc: '车队工作量离散度',
    value: simulationCosts.normalizedCostE,
    normalizedChartType: 'NE'
  }
]);

const rawCostMetrics = computed(() => [
  {
    key: 'costA',
    name: '直接成本 (A)',
    desc: '等待时间与空驶里程',
    display: formatCostValue(simulationCosts.costA, 2),
    chartType: 'A'
  },
  {
    key: 'costB',
    name: '效率成本 (B)',
    desc: '空驶率与等待率',
    display: formatCostValue(simulationCosts.costB, 2),
    chartType: 'B'
  },
  {
    key: 'costC',
    name: '运能损耗 (C)',
    desc: '理论与实际运能差',
    display: formatCostValue(simulationCosts.costC, 2),
    chartType: 'C'
  },
  {
    key: 'costD',
    name: '经济损耗 (D)',
    desc: '油耗与固定损耗',
    display: formatCostValue(simulationCosts.costD, 2),
    chartType: 'D'
  },
  {
    key: 'costE',
    name: '负载均衡 (E)',
    desc: '车队工作量离散度',
    display: formatCostValue(simulationCosts.costE, 4),
    chartType: 'E'
  },
  {
    key: 'allCost',
    name: '综合成本 (All)',
    desc: '后端A-E加权计算',
    display: formatCostValue(simulationCosts.allCost, 4),
    chartType: 'ALL',
    total: true
  }
]);

const formatPercent = (value, digits = 1) => `${readCostNumber(value).toFixed(digits)}%`;

const formatDateTime = (value) => {
  if (!value) {
    return '--';
  }
  const text = String(value);
  return text.includes('T') ? text.replace('T', ' ').slice(0, 19) : text;
};

const toDashboardNumber = (value, digits = 2) => {
  const optional = readOptionalCostNumber(value);
  return optional === null ? '--' : optional.toFixed(digits);
};

const toPositivePieValue = (value) => {
  const numberValue = readCostNumber(value);
  return numberValue > 0 ? numberValue : 0;
};

const sumBy = (items, field) => items.reduce((sum, item) => sum + readCostNumber(item?.[field]), 0);

const currentVehicleDataset = computed(() => {
  if (monitorVehicles.length > 0) {
    return monitorVehicles;
  }
  return vehicles.map(vehicle => ({
    vehicleId: vehicle.id,
    licensePlate: vehicle.licensePlate,
    status: vehicle.status,
    statusText: statusMap[vehicle.status]?.text || vehicle.status,
    currentLoad: vehicle.currentLoad,
    maxLoadCapacity: vehicle.maxLoadCapacity,
    currentVolume: vehicle.currentVolume,
    maxVolumeCapacity: vehicle.maxVolumeCapacity
  }));
});

const getUsagePercent = (current, max) => {
  const maxValue = readCostNumber(max);
  if (maxValue <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(0, (readCostNumber(current) / maxValue) * 100));
};

const averageUsage = (items, currentField, maxField) => {
  const values = items
      .map(item => getUsagePercent(item?.[currentField], item?.[maxField]))
      .filter(value => Number.isFinite(value));
  if (values.length === 0) {
    return 0;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
};

const shipmentItemStateSummary = computed(() => ({
  waiting: sumBy(monitorShipments, 'waitingItems'),
  inProgress: sumBy(monitorShipments, 'inProgressItems'),
  completed: sumBy(monitorShipments, 'completedItems')
}));

const dashboardKpis = computed(() => [
  {
    key: 'shipments',
    label: '活跃运单',
    value: monitorSummary.activeShipmentCount || monitorShipments.length,
    note: 'Shipment'
  },
  {
    key: 'assignments',
    label: '活跃任务',
    value: monitorSummary.activeAssignmentCount || monitorAssignments.length,
    note: 'Assignment'
  },
  {
    key: 'vehicles',
    label: '活跃车辆',
    value: monitorSummary.activeVehicleCount || currentVehicleDataset.value.length,
    note: 'Vehicle'
  },
  {
    key: 'waiting',
    label: '等待任务项',
    value: shipmentItemStateSummary.value.waiting,
    note: 'Waiting'
  },
  {
    key: 'load',
    label: '平均载重率',
    value: formatPercent(averageUsage(currentVehicleDataset.value, 'currentLoad', 'maxLoadCapacity')),
    note: 'Load'
  },
  {
    key: 'volume',
    label: '平均载容率',
    value: formatPercent(averageUsage(currentVehicleDataset.value, 'currentVolume', 'maxVolumeCapacity')),
    note: 'Volume'
  }
]);

const dashboardCostRows = computed(() => {
  const baseline = runtimeCostDetail.baseline || {};
  return normalizedCostMetrics.value.map(metric => {
    const suffix = metric.key.replace('normalizedCost', '');
    const rawKey = `cost${suffix}`;
    return {
      key: metric.key,
      name: metric.name,
      raw: formatCostValue(simulationCosts[rawKey], suffix === 'E' ? 4 : 2),
      normalized: formatNormalizedValue(metric.value),
      baseline: toDashboardNumber(baseline[`baselineCost${suffix}`], 4),
      weight: toDashboardNumber(baseline[`runtimeWeight${suffix}`], 2)
    };
  });
});

const windowDetailMetrics = computed(() => {
  const windowDetail = runtimeCostDetail.window || {};
  return [
    { label: '窗口ID', value: windowDetail.windowId || '--' },
    { label: '策略', value: windowDetail.strategy || '--' },
    { label: '开始时间', value: formatDateTime(windowDetail.windowStartTime) },
    { label: '结束时间', value: formatDateTime(windowDetail.windowEndTime) },
    { label: '任务规模', value: toDashboardNumber(windowDetail.taskScale, 2) },
    { label: '新增任务项', value: windowDetail.generatedShipmentItems ?? '--' },
    { label: '窗口初始未分配', value: windowDetail.notAssignedItemsAtStart ?? '--' },
    { label: '单位CostA', value: toDashboardNumber(windowDetail.unitCostA, 4) },
    { label: '单位CostB', value: toDashboardNumber(windowDetail.unitCostB, 4) },
    { label: '单位CostC', value: toDashboardNumber(windowDetail.unitCostC, 4) },
    { label: '单位CostD', value: toDashboardNumber(windowDetail.unitCostD, 4) },
    { label: '单位CostE', value: toDashboardNumber(windowDetail.unitCostE, 4) }
  ];
});

const baselineDetailMetrics = computed(() => {
  const baseline = runtimeCostDetail.baseline || {};
  return [
    { label: '策略', value: baseline.baselineStrategy || '--' },
    { label: '分位', value: baseline.baselinePercentile || '--' },
    { label: '基准CostA', value: toDashboardNumber(baseline.baselineCostA, 4) },
    { label: '基准CostB', value: toDashboardNumber(baseline.baselineCostB, 4) },
    { label: '基准CostC', value: toDashboardNumber(baseline.baselineCostC, 4) },
    { label: '基准CostD', value: toDashboardNumber(baseline.baselineCostD, 4) },
    { label: '基准CostE', value: toDashboardNumber(baseline.baselineCostE, 4) },
    { label: '权重A-E', value: ['A', 'B', 'C', 'D', 'E'].map(key => toDashboardNumber(baseline[`runtimeWeight${key}`], 2)).join(' / ') }
  ];
});

const formulaContributionGroups = computed(() => {
  const detail = runtimeCostDetail;
  const costA = detail.costA || {};
  const costB = detail.costB || {};
  const costC = detail.costC || {};
  const costD = detail.costD || {};
  return [
    {
      key: 'costA',
      title: 'CostA 公式参数贡献',
      subtitle: '等待时间与空驶里程',
      metrics: [
        { label: '等待贡献', value: costA.waitingCostContribution, display: toDashboardNumber(costA.waitingCostContribution, 4) },
        { label: '空驶贡献', value: costA.emptyDistanceCostContribution, display: toDashboardNumber(costA.emptyDistanceCostContribution, 4) },
        { label: '等待小时', value: costA.totalWaitingHours, display: toDashboardNumber(costA.totalWaitingHours, 2) },
        { label: '空驶公里', value: costA.totalEmptyDistanceKm, display: toDashboardNumber(costA.totalEmptyDistanceKm, 2) }
      ]
    },
    {
      key: 'costB',
      title: 'CostB 公式参数贡献',
      subtitle: '效率、等待与闲置惩罚',
      metrics: [
        { label: '空驶率贡献', value: costB.emptyMileageContribution, display: toDashboardNumber(costB.emptyMileageContribution, 4) },
        { label: '等待运输贡献', value: costB.waitingTransportContribution, display: toDashboardNumber(costB.waitingTransportContribution, 4) },
        { label: '最差等待贡献', value: costB.worstWaitingContribution, display: toDashboardNumber(costB.worstWaitingContribution, 4) },
        { label: '闲置等待贡献', value: costB.idleWaitContribution, display: toDashboardNumber(costB.idleWaitContribution, 4) }
      ]
    },
    {
      key: 'costC',
      title: 'CostC 公式参数贡献',
      subtitle: '运能差与最差运能差',
      metrics: [
        { label: '运能差贡献', value: costC.capacityGapContribution, display: toDashboardNumber(costC.capacityGapContribution, 4) },
        { label: '最差运能贡献', value: costC.worstCapacityContribution, display: toDashboardNumber(costC.worstCapacityContribution, 4) },
        { label: '理论运能', value: costC.totalTheoryCapacity, display: toDashboardNumber(costC.totalTheoryCapacity, 2) },
        { label: '实际运能', value: costC.totalActualCapacity, display: toDashboardNumber(costC.totalActualCapacity, 2) }
      ]
    },
    {
      key: 'costD',
      title: 'CostD 公式参数贡献',
      subtitle: '经济损失代理项',
      metrics: [
        { label: '利用损失贡献', value: costD.utilizationWasteContribution, display: toDashboardNumber(costD.utilizationWasteContribution, 4) },
        { label: '时间损失贡献', value: costD.assignedTimeContribution, display: toDashboardNumber(costD.assignedTimeContribution, 4) },
        { label: '闲置空间贡献', value: costD.idleSpaceContribution, display: toDashboardNumber(costD.idleSpaceContribution, 4) },
        { label: '最差经济贡献', value: costD.worstEconomicContribution, display: toDashboardNumber(costD.worstEconomicContribution, 4) }
      ]
    }
  ];
});

const costEWorkloadMetrics = computed(() => {
  const costE = runtimeCostDetail.costE || {};
  return [
    { label: '平均工作负载', value: toDashboardNumber(costE.averageWorkload, 4) },
    { label: '工作负载标准差', value: toDashboardNumber(costE.workloadStandardDeviation, 4) },
    { label: '工作负载变异系数', value: toDashboardNumber(costE.workloadVariationCoefficient, 4) }
  ];
});

const highestNormalizedCostDimension = computed(() => {
  const validMetrics = normalizedCostMetrics.value.filter(metric => isFiniteCostValue(metric.value));
  if (validMetrics.length === 0) {
    return { name: '等待窗口数据', value: '--', desc: '暂无可定位的归一化维度' };
  }
  const metric = validMetrics.reduce((max, item) => item.value > max.value ? item : max, validMetrics[0]);
  return {
    name: metric.name,
    value: formatNormalizedValue(metric.value),
    desc: metric.desc
  };
});

const topWaitingShipments = computed(() => [...monitorShipments]
    .filter(item => readCostNumber(item.waitingItems) > 0)
    .sort((a, b) => readCostNumber(b.waitingItems) - readCostNumber(a.waitingItems))
    .slice(0, 5));

const topLowProgressShipments = computed(() => [...monitorShipments]
    .filter(item => readCostNumber(item.totalItems) > 0)
    .sort((a, b) => readCostNumber(a.progressPercentage) - readCostNumber(b.progressPercentage))
    .slice(0, 5));

const topUtilizedVehicles = computed(() => currentVehicleDataset.value
    .map(vehicle => {
      const loadUsage = getUsagePercent(vehicle.currentLoad, vehicle.maxLoadCapacity);
      const volumeUsage = getUsagePercent(vehicle.currentVolume, vehicle.maxVolumeCapacity);
      const maxUsage = Math.max(loadUsage, volumeUsage);
      return {
        ...vehicle,
        loadUsage,
        volumeUsage,
        maxUsage,
        display: `${formatPercent(loadUsage)} / ${formatPercent(volumeUsage)}`
      };
    })
    .filter(vehicle => vehicle.maxUsage > 0)
    .sort((a, b) => b.maxUsage - a.maxUsage)
    .slice(0, 5));

const NORMALIZED_BAR_MAX = 1.5;

const getNormalizedBarWidth = (value) => {
  if (!isFiniteCostValue(value)) {
    return '0%';
  }
  const capped = Math.max(0, Math.min(value, NORMALIZED_BAR_MAX));
  return `${(capped / NORMALIZED_BAR_MAX) * 100}%`;
};

const getNormalizedBarClass = (value) => {
  if (!isFiniteCostValue(value)) {
    return 'normalized-bar-fill--pending';
  }
  if (value < 1.0) {
    return 'normalized-bar-fill--good';
  }
  if (value <= 1.05) {
    return 'normalized-bar-fill--warning';
  }
  return 'normalized-bar-fill--danger';
};

const chartVisible = ref(false);
const chartTitle = ref('归一化综合成本趋势');
const chartRef = ref(null);
const currentChartType = ref('NALL');
let chartInstance = null;

const costTitles = {
  'A': '直接成本 (A) 趋势',
  'B': '效率成本 (B) 趋势',
  'C': '运能损耗 (C) 趋势',
  'D': '经济损耗 (D) 趋势',
  'NA': '归一化直接成本 (A) 趋势',
  'NB': '归一化效率成本 (B) 趋势',
  'NC': '归一化运能损耗 (C) 趋势',
  'ND': '归一化经济损耗 (D) 趋势',
  'NE': '归一化负载均衡 (E) 趋势',
  'NALL': '归一化综合成本趋势'
};

costTitles.E = '负载均衡 (E) 趋势';
costTitles.ALL = '综合成本 (All) 趋势';

const showChart = (type) => {
  currentChartType.value = type;
  chartTitle.value = costTitles[type];
  chartVisible.value = true;
  
  // 确保在对话框显示并且数据存在时更新图表
  nextTick(() => {
    if (chartInstance) {
      updateChart();
    }
  });
};

const initChart = () => {
  if (!chartRef.value) return;
  // 先销毁旧的实例
  if (chartInstance) {
    chartInstance.dispose();
  }
  chartInstance = window.echarts.init(chartRef.value);
  updateChart();
};

const disposeChart = () => {
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
};

const updateChart = () => {
  if (!chartInstance) return;
  
  const typeMap = {
    'A': costHistory.costA,
    'B': costHistory.costB,
    'C': costHistory.costC,
    'D': costHistory.costD,
    'E': costHistory.costE,
    'ALL': costHistory.allCost,
    'NA': costHistory.normalizedCostA,
    'NB': costHistory.normalizedCostB,
    'NC': costHistory.normalizedCostC,
    'ND': costHistory.normalizedCostD,
    'NE': costHistory.normalizedCostE,
    'NALL': costHistory.normalizedAllCost
  };
  
  const data = typeMap[currentChartType.value] || [];
  const hasChartData = data.some(value => value !== null && value !== undefined && value !== '');
  
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>{a}: {c}'
    },
    xAxis: {
      type: 'category',
      data: costHistory.times,
      name: '时间'
    },
    yAxis: {
      type: 'value',
      name: '数值'
    },
    graphic: hasChartData ? [] : [
      {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: {
          text: currentChartType.value.startsWith('N')
              ? '等待完整调度窗口或基准数据'
              : '暂无趋势数据',
          fill: '#909399',
          fontSize: 14
        }
      }
    ],
    series: [
      {
        name: chartTitle.value,
        type: 'line',
        data: data,
        smooth: true,
        itemStyle: {
          color: '#409eff'
        },
        areaStyle: {
          color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.3)' },
            { offset: 1, color: 'rgba(64,158,255,0.05)' }
          ])
        }
      }
    ]
  };
  
  chartInstance.setOption(option);
  
  // 必须调用 resize 确保图表正常显示
  setTimeout(() => {
    if (chartInstance) {
      chartInstance.resize();
    }
  }, 100);
};

const dashboardNormalizedCostTrendRef = ref(null);
const dashboardAllCostTrendRef = ref(null);
const dashboardNormalizedRef = ref(null);
const dashboardCostShareRef = ref(null);
const dashboardMonitorTrendRef = ref(null);
const dashboardShipmentStateRef = ref(null);
const dashboardAssignmentStatusRef = ref(null);
const dashboardVehicleStatusRef = ref(null);
const experimentOptimizationTrendRef = ref(null);
const formulaChartRefs = reactive({});
let runtimeDashboardChartInstances = {};

const setFormulaChartRef = (key) => (el) => {
  if (el) {
    formulaChartRefs[key] = el;
  } else {
    delete formulaChartRefs[key];
  }
};

const hasValues = (items) => items.some(item => readCostNumber(item?.value) > 0);

const emptyChartGraphic = (text) => [
  {
    type: 'text',
    left: 'center',
    top: 'middle',
    style: {
      text,
      fill: '#909399',
      fontSize: 14
    }
  }
];

const dashboardPalette = ['#2f80ed', '#27ae60', '#f2994a', '#eb5757', '#9b51e0', '#00a8a8', '#6f7d95'];

const buildPieOption = (title, items, emptyText = '暂无可计算占比') => {
  const data = items
      .map(item => ({ name: item.name, value: toPositivePieValue(item.value) }))
      .filter(item => item.value > 0);
  return {
    color: dashboardPalette,
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} ({d}%)' },
    legend: {
      bottom: 0,
      type: 'scroll',
      textStyle: { color: '#606266', fontSize: 11 }
    },
    graphic: data.length > 0 ? [] : emptyChartGraphic(emptyText),
    series: [
      {
        name: title,
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{d}%' },
        data
      }
    ]
  };
};

const buildLineTrendOption = ({ title, data, color, emptyText, yName = '数值' }) => {
  const lineData = data.map(value => readOptionalCostNumber(value));
  const hasData = lineData.some(value => value !== null);
  return {
    color: [color],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: [title] },
    grid: { left: 45, right: 24, top: 44, bottom: 34 },
    xAxis: { type: 'category', data: costHistory.times },
    yAxis: { type: 'value', name: yName },
    graphic: hasData ? [] : emptyChartGraphic(emptyText),
    series: [
      {
        name: title,
        type: 'line',
        smooth: true,
        data: lineData,
        connectNulls: false,
        areaStyle: { opacity: 0.08 }
      }
    ]
  };
};

const buildNormalizedCostTrendOption = () => buildLineTrendOption({
  title: '归一化综合成本',
  data: costHistory.normalizedAllCost,
  color: '#2f80ed',
  emptyText: '等待完整调度窗口或基准数据',
  yName: '成本指数'
});

const buildAllCostTrendOption = () => buildLineTrendOption({
  title: '原始AllCost',
  data: costHistory.allCost,
  color: '#27ae60',
  emptyText: '暂无原始 AllCost 趋势数据',
  yName: 'AllCost'
});

const buildExperimentOptimizationTrendOption = () => {
  const originalTrend = Array.isArray(experimentRun.result?.original?.costTrend)
      ? experimentRun.result.original.costTrend
      : [];
  const heuristicTrend = Array.isArray(experimentRun.result?.heuristic?.costTrend)
      ? experimentRun.result.heuristic.costTrend
      : [];
  const loops = [...new Set([
    ...originalTrend.map(point => point?.loopCount),
    ...heuristicTrend.map(point => point?.loopCount)
  ].filter(loop => loop !== null && loop !== undefined))]
      .sort((a, b) => Number(a) - Number(b));

  const toTrendMap = (trend) => {
    const map = new Map();
    trend.forEach(point => {
      const loop = point?.loopCount;
      if (loop !== null && loop !== undefined) {
        map.set(loop, readOptionalCostNumber(point?.normalizedAllCost));
      }
    });
    return map;
  };
  const originalMap = toTrendMap(originalTrend);
  const heuristicMap = toTrendMap(heuristicTrend);
  const originalData = loops.map(loop => originalMap.has(loop) ? originalMap.get(loop) : null);
  const heuristicData = loops.map(loop => heuristicMap.has(loop) ? heuristicMap.get(loop) : null);
  const hasData = [...originalData, ...heuristicData].some(value => value !== null);

  return {
    color: ['#2f80ed', '#eb5757'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['ORIGINAL', 'HEURISTIC'] },
    grid: { left: 48, right: 24, top: 44, bottom: 34 },
    xAxis: { type: 'category', name: 'Loop', data: loops.map(loop => String(loop)) },
    yAxis: { type: 'value', name: '成本指数' },
    graphic: hasData ? [] : emptyChartGraphic('等待 ORIGINAL 与 HEURISTIC 成本趋势数据'),
    series: [
      {
        name: 'ORIGINAL',
        type: 'line',
        smooth: true,
        data: originalData,
        connectNulls: false,
        areaStyle: { opacity: 0.06 }
      },
      {
        name: 'HEURISTIC',
        type: 'line',
        smooth: true,
        data: heuristicData,
        connectNulls: false,
        areaStyle: { opacity: 0.06 }
      }
    ]
  };
};

const buildNormalizedBreakdownOption = () => {
  const metrics = normalizedCostMetrics.value;
  const data = metrics.map(metric => readOptionalCostNumber(metric.value));
  const hasData = data.some(value => value !== null);
  return {
    color: ['#2f80ed'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 92, right: 35, top: 18, bottom: 28 },
    xAxis: {
      type: 'value',
      min: 0,
      splitLine: { lineStyle: { color: '#edf0f5' } }
    },
    yAxis: {
      type: 'category',
      data: metrics.map(metric => metric.name),
      axisLabel: { fontSize: 11 }
    },
    graphic: hasData ? [] : emptyChartGraphic('等待完整调度窗口或基准数据'),
    series: [
      {
        name: '归一化成本',
        type: 'bar',
        data,
        barMaxWidth: 18,
        markLine: {
          symbol: 'none',
          label: { formatter: 'P95=1.0' },
          lineStyle: { color: '#eb5757', type: 'dashed' },
          data: [{ xAxis: 1 }]
        }
      }
    ]
  };
};

const buildCostShareOption = () => buildPieOption(
    'Cost A-E 维度占比',
    rawCostMetrics.value
        .filter(metric => !metric.total)
        .map(metric => ({ name: metric.name, value: simulationCosts[metric.key] })),
    '暂无 Cost 维度占比数据'
);

const buildFormulaPieOption = (group) => buildPieOption(
    group.title,
    group.metrics
        .filter(metric => metric.label.includes('贡献'))
        .map(metric => ({ name: metric.label, value: metric.value })),
    '暂无正向公式贡献值'
);

const groupStatusCounts = (items, statusKey = 'status', textKey = 'statusText') => {
  const counts = new Map();
  items.forEach(item => {
    const status = item?.[statusKey] || 'UNKNOWN';
    const label = item?.[textKey] || statusMap[status]?.text || status || '未知';
    counts.set(label, (counts.get(label) || 0) + 1);
  });
  return [...counts.entries()].map(([name, value]) => ({ name, value }));
};

const buildMonitorTrendOption = () => {
  const hasData = dashboardHistory.times.length > 0;
  return {
    color: ['#2f80ed', '#27ae60', '#f2994a', '#eb5757', '#9b51e0', '#00a8a8'],
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      type: 'scroll',
      data: ['活跃运单', '活跃任务', '活跃车辆', '等待任务项', '平均载重率', '平均载容率']
    },
    grid: { left: 45, right: 24, top: 48, bottom: 34 },
    xAxis: { type: 'category', data: dashboardHistory.times },
    yAxis: { type: 'value' },
    graphic: hasData ? [] : emptyChartGraphic('暂无全局态势趋势数据'),
    series: [
      { name: '活跃运单', type: 'line', smooth: true, data: dashboardHistory.activeShipments },
      { name: '活跃任务', type: 'line', smooth: true, data: dashboardHistory.activeAssignments },
      { name: '活跃车辆', type: 'line', smooth: true, data: dashboardHistory.activeVehicles },
      { name: '等待任务项', type: 'line', smooth: true, data: dashboardHistory.waitingItems },
      { name: '平均载重率', type: 'line', smooth: true, data: dashboardHistory.avgLoadUsage },
      { name: '平均载容率', type: 'line', smooth: true, data: dashboardHistory.avgVolumeUsage }
    ]
  };
};

const buildShipmentStateOption = () => buildPieOption('运单任务项分布', [
  { name: '等待', value: shipmentItemStateSummary.value.waiting },
  { name: '进行中', value: shipmentItemStateSummary.value.inProgress },
  { name: '完成', value: shipmentItemStateSummary.value.completed }
], '暂无运单任务项数据');

const buildAssignmentStatusOption = () => buildPieOption(
    '任务状态分布',
    groupStatusCounts(monitorAssignments),
    '暂无活跃任务状态数据'
);

const buildVehicleStatusOption = () => buildPieOption(
    '车辆状态分布',
    groupStatusCounts(currentVehicleDataset.value),
    '暂无活跃车辆状态数据'
);

const initRuntimeDashboardCharts = () => {
  if (!window.echarts) {
    return;
  }
  disposeRuntimeDashboardCharts();
  const refs = {
    normalizedCostTrend: dashboardNormalizedCostTrendRef.value,
    allCostTrend: dashboardAllCostTrendRef.value,
    normalized: dashboardNormalizedRef.value,
    costShare: dashboardCostShareRef.value,
    monitorTrend: dashboardMonitorTrendRef.value,
    shipmentState: dashboardShipmentStateRef.value,
    assignmentStatus: dashboardAssignmentStatusRef.value,
    vehicleStatus: dashboardVehicleStatusRef.value,
    experimentOptimizationTrend: experimentOptimizationTrendRef.value,
    ...formulaContributionGroups.value.reduce((acc, group) => {
      acc[group.key] = formulaChartRefs[group.key];
      return acc;
    }, {})
  };
  Object.entries(refs).forEach(([key, el]) => {
    if (el) {
      runtimeDashboardChartInstances[key] = window.echarts.init(el);
    }
  });
  updateRuntimeDashboardCharts();
};

const updateRuntimeDashboardCharts = () => {
  if (activeMainView.value !== 'costDashboard' || !window.echarts) {
    return;
  }
  const instances = runtimeDashboardChartInstances;
  instances.normalizedCostTrend?.setOption(buildNormalizedCostTrendOption(), true);
  instances.allCostTrend?.setOption(buildAllCostTrendOption(), true);
  instances.normalized?.setOption(buildNormalizedBreakdownOption(), true);
  instances.costShare?.setOption(buildCostShareOption(), true);
  instances.monitorTrend?.setOption(buildMonitorTrendOption(), true);
  instances.shipmentState?.setOption(buildShipmentStateOption(), true);
  instances.assignmentStatus?.setOption(buildAssignmentStatusOption(), true);
  instances.vehicleStatus?.setOption(buildVehicleStatusOption(), true);
  instances.experimentOptimizationTrend?.setOption(buildExperimentOptimizationTrendOption(), true);
  formulaContributionGroups.value.forEach(group => {
    instances[group.key]?.setOption(buildFormulaPieOption(group), true);
  });
  setTimeout(() => {
    Object.values(runtimeDashboardChartInstances).forEach(instance => instance?.resize());
  }, 80);
};

function disposeRuntimeDashboardCharts() {
  Object.values(runtimeDashboardChartInstances).forEach(instance => instance?.dispose());
  runtimeDashboardChartInstances = {};
}

const assignRuntimeCostDetail = (data = {}) => {
  runtimeCostDetail.generatedAt = data.generatedAt || '';
  runtimeCostDetail.summary = data.summary || null;
  runtimeCostDetail.costA = data.costA || null;
  runtimeCostDetail.costB = data.costB || null;
  runtimeCostDetail.costC = data.costC || null;
  runtimeCostDetail.costD = data.costD || null;
  runtimeCostDetail.costE = data.costE || null;
  runtimeCostDetail.window = data.window || null;
  runtimeCostDetail.baseline = data.baseline || null;
  runtimeCostDetail.error = '';
};

const fetchRuntimeCostDetail = async () => {
  try {
    const response = await request.get('/api/simulation/costs/detail');
    if (response.data) {
      assignRuntimeCostDetail(response.data);
      updateRuntimeDashboardCharts();
    }
    return response.data;
  } catch (error) {
    runtimeCostDetail.error = '成本详情接口读取失败';
    console.error('获取成本详情数据失败:', error);
    updateRuntimeDashboardCharts();
    return null;
  }
};

const openRuntimeDashboard = async () => {
  activeMainView.value = 'costDashboard';
  isMonitorPanelVisible.value = false;
  await Promise.allSettled([
    fetchRuntimeCostDetail(),
    fetchTransportMonitor(),
    refreshExperimentPreparation(),
    refreshExperimentRunState()
  ]);
  await nextTick();
  if (Object.keys(runtimeDashboardChartInstances).length === 0) {
    initRuntimeDashboardCharts();
  } else {
    updateRuntimeDashboardCharts();
  }
};

const returnToMapView = async () => {
  activeMainView.value = 'map';
  disposeRuntimeDashboardCharts();
  await nextTick();
  resizeMapAfterPanelChange();
};

const fetchSimulationCosts = async () => {
  try {
    const response = await request.get('/api/simulation/costs');
    if (response.data) {
      simulationCosts.costA = response.data.costA || 0;
      simulationCosts.costB = response.data.costB || 0;
      simulationCosts.costC = response.data.costC || 0;
      simulationCosts.costD = response.data.costD || 0;
      simulationCosts.costE = response.data.costE || 0;
      simulationCosts.allCost = response.data.allCost || 0;
      simulationCosts.normalizedCostA = readOptionalCostNumber(response.data.normalizedCostA);
      simulationCosts.normalizedCostB = readOptionalCostNumber(response.data.normalizedCostB);
      simulationCosts.normalizedCostC = readOptionalCostNumber(response.data.normalizedCostC);
      simulationCosts.normalizedCostD = readOptionalCostNumber(response.data.normalizedCostD);
      simulationCosts.normalizedCostE = readOptionalCostNumber(response.data.normalizedCostE);
      simulationCosts.normalizedAllCost = readOptionalCostNumber(response.data.normalizedAllCost);
      simulationCosts.baselinePercentile = response.data.baselinePercentile || '';
      simulationCosts.baselineStrategy = response.data.baselineStrategy || '';
      
      // 记录历史数据用于折线图
      const now = new Date();
      const timeStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
      
      costHistory.times.push(timeStr);
      costHistory.costA.push(simulationCosts.costA.toFixed(2));
      costHistory.costB.push(simulationCosts.costB.toFixed(2));
      costHistory.costC.push(simulationCosts.costC.toFixed(2));
      costHistory.costD.push(simulationCosts.costD.toFixed(2));
      costHistory.costE.push(simulationCosts.costE.toFixed(4));
      costHistory.allCost.push(simulationCosts.allCost.toFixed(4));
      costHistory.normalizedCostA.push(isFiniteCostValue(simulationCosts.normalizedCostA) ? simulationCosts.normalizedCostA.toFixed(4) : null);
      costHistory.normalizedCostB.push(isFiniteCostValue(simulationCosts.normalizedCostB) ? simulationCosts.normalizedCostB.toFixed(4) : null);
      costHistory.normalizedCostC.push(isFiniteCostValue(simulationCosts.normalizedCostC) ? simulationCosts.normalizedCostC.toFixed(4) : null);
      costHistory.normalizedCostD.push(isFiniteCostValue(simulationCosts.normalizedCostD) ? simulationCosts.normalizedCostD.toFixed(4) : null);
      costHistory.normalizedCostE.push(isFiniteCostValue(simulationCosts.normalizedCostE) ? simulationCosts.normalizedCostE.toFixed(4) : null);
      costHistory.normalizedAllCost.push(isFiniteCostValue(simulationCosts.normalizedAllCost) ? simulationCosts.normalizedAllCost.toFixed(4) : null);
      
      // 保持最多 60 个数据点 (比如8秒更新一次，即8分钟的数据)
      if (costHistory.times.length > 60) {
        costHistory.times.shift();
        costHistory.costA.shift();
        costHistory.costB.shift();
        costHistory.costC.shift();
        costHistory.costD.shift();
        costHistory.costE.shift();
        costHistory.allCost.shift();
        costHistory.normalizedCostA.shift();
        costHistory.normalizedCostB.shift();
        costHistory.normalizedCostC.shift();
        costHistory.normalizedCostD.shift();
        costHistory.normalizedCostE.shift();
        costHistory.normalizedAllCost.shift();
      }
      
      if (chartVisible.value && chartInstance) {
        updateChart();
      }
      await fetchRuntimeCostDetail();
    }
  } catch (error) {
    console.error('获取成本数据失败:', error);
  }
};

// 清除高亮
const clearHighlight = () => {
  if (highlightTimer) {
    clearTimeout(highlightTimer);
    highlightTimer = null;
  }
  highlightedVehicleId.value = null;
};

// 生成运单（批量）
const generateShipments = async () => {
  if (isExperimentRunActive.value) {
    ElMessage.warning('实验运行中不能生成普通运单');
    return;
  }
  if (shipmentCount.value <= 0) return;
  try {
    const res = await request.post('/api/shipments/batch-generate', { count: shipmentCount.value });
    shipments.value = res.data;
  } catch (error) {
    console.error("生成运单失败", error);
    alert("生成运单失败，请检查控制台日志");
  }
};

// --- 仿真控制 ---
const speedFactor = ref(1);
const formattedSpeed = computed(() => `${speedFactor.value.toFixed(1)}x`);
const speedSliderMarks = {
  100: '100x'
};

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
const simulationInterval = ref(4000); // 4秒更新一次

// --- 原有POI功能 ---
const poiMarkers = ref([]); // 存储POI标记
const currentPOIs = ref([]); // 当前显示的POI数据
const isSimulationRunning = ref(false); // 仿真运行状态
const resetInProgress = ref(false); // 重置过程中禁止重复操作
const useHeuristicDispatch = ref(false); // 是否启用启发式调度
const simulationGeneration = ref(0);
let routePlanningAbortController = null;
let assignmentDrawInProgress = false;

// 响应式数据
const drawnPairIds = ref(new Set()); // 已绘制的配对ID (可以删除)
const drawnAssignmentIds = ref(new Set()); // 已绘制的Assignment ID
const drawnVehicleIconIds = ref(new Set()); // Vehicle IDs with rendered, locatable vehicle markers.
const activeRoutes = ref(new Map()); // 当前活动的路线映射，key为assignmentId

// 路线规划缓存
const routePlanningCache = new Map();

const abortRoutePlanningRequests = () => {
  if (routePlanningAbortController) {
    routePlanningAbortController.abort();
    routePlanningAbortController = null;
  }
};

const beginSimulationGeneration = () => {
  simulationGeneration.value += 1;
  abortRoutePlanningRequests();
  routePlanningAbortController = new AbortController();
  return simulationGeneration.value;
};

const invalidateSimulationGeneration = () => {
  simulationGeneration.value += 1;
  abortRoutePlanningRequests();
};

const isActiveSimulationGeneration = (generation) => {
  return isSimulationRunning.value && !resetInProgress.value && generation === simulationGeneration.value;
};

const isTransportAnimationActive = () => {
  return (isSimulationRunning.value || isExperimentRunExecuting.value) && !resetInProgress.value;
};

const isActiveTransportGeneration = (generation) => {
  return isTransportAnimationActive() && generation === simulationGeneration.value;
};

function getVehicleIconId(vehicleId) {
  return vehicleId === null || vehicleId === undefined ? null : String(vehicleId);
}

function markerHasValidPosition(marker) {
  return Boolean(marker && normalizeMapPosition(marker.getPosition?.()));
}

function syncRegisteredVehicleStats() {
  stats.running = vehicleMonitorDisplayVehicles.value.length;
}

function markDrawnVehicleIcon(vehicleId, marker) {
  const iconId = getVehicleIconId(vehicleId);
  if (!iconId || !markerHasValidPosition(marker)) {
    return;
  }
  const nextIds = new Set(drawnVehicleIconIds.value);
  nextIds.add(iconId);
  drawnVehicleIconIds.value = nextIds;
  syncRegisteredVehicleStats();
}

function unmarkDrawnVehicleIcon(vehicleId) {
  const iconId = getVehicleIconId(vehicleId);
  if (iconId) {
    const nextIds = new Set(drawnVehicleIconIds.value);
    nextIds.delete(iconId);
    drawnVehicleIconIds.value = nextIds;
    syncRegisteredVehicleStats();
  }
}

const scheduleAssignmentDrawing = (drawer, runGeneration, label) => {
  if (!isActiveTransportGeneration(runGeneration)) {
    return;
  }
  if (assignmentDrawInProgress) {
    console.log(`[AssignmentDraw] skip ${label}: drawing already in progress`);
    return;
  }

  assignmentDrawInProgress = true;
  Promise.resolve()
      .then(() => drawer(runGeneration))
      .catch(error => {
        console.error(`[AssignmentDraw] ${label} failed`, error);
      })
      .finally(() => {
        assignmentDrawInProgress = false;
      });
};

const cleanupRouteData = (routeData) => {
  if (routeData && typeof routeData.cleanup === 'function') {
    routeData.cleanup();
  }
};

const discardRouteData = (assignmentId, routeData) => {
  unmarkDrawnVehicleIcon(routeData?.assignment?.vehicleId);
  cleanupRouteData(routeData);
  activeRoutes.value.delete(assignmentId);
  drawnAssignmentIds.value.delete(assignmentId);
};

const cleanupAllActiveRoutes = () => {
  activeRoutes.value.forEach(routeData => {
    cleanupRouteData(routeData);
  });
  activeRoutes.value.clear();
  drawnAssignmentIds.value.clear();
  drawnVehicleIconIds.value = new Set();
  syncRegisteredVehicleStats();
};

// Assignment状态跟踪
const assignmentStates = new Map();

// 图标配置 - 根据POI类型使用不同的图标
const poiIcons = {
  'WAREHOUSE': warehouseIcon,
  'GAS_STATION': gasStationIcon,
  'MAINTENANCE_CENTER': maintenanceIcon,
  'REST_AREA': restAreaIcon,
  'DISTRIBUTION_CENTER': transportIcon,
  'TEST': testIcon,
  'TIMBER_YARD': timberYardIcon,
  'SAWMILL': sawmillIcon,
  'BOARD_FACTORY': boardFactoryIcon,
  'IRON_MINE': ironMineIcon,
  'STEEL_MILL': steelMillIcon,
  'STEEL_PROCESSING_PLANT': steelProcessingPlantIcon,
  'FURNITURE_FACTORY': furnitureFactoryIcon,
  'RUBBER_PROCESSING_PLANT': factoryIcon, // 橡胶加工厂复用工厂
  'TIRE_MANUFACTURING_PLANT': tireManufacturingPlant,
  'AUTO_ASSEMBLY_PLANT': autoAssemblyPlant
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
    const vehicle = this.vehicles[this.findVehicleIndex(vehicleId)];
    if (vehicle && vehicle.status) {
      this.updateVehicleIcon(vehicleId, vehicle.status);
    }
  }

  /**
   * 更新车辆状态（核心方法）
   */
  toNumber(value, fallback = 0) {
    const numberValue = Number(value);
    return Number.isFinite(numberValue) ? numberValue : fallback;
  }

  findVehicleIndex(vehicleId) {
    return this.vehicles.findIndex(v => String(v.id) === String(vehicleId));
  }

  ensureVehicleRecord(vehicleId, status, additionalData = {}) {
    let vehicleIndex = this.findVehicleIndex(vehicleId);
    if (vehicleIndex !== -1) {
      return this.vehicles[vehicleIndex];
    }

    const assignment = additionalData.assignment || this.assignmentData.get(vehicleId);
    if (!assignment) {
      return null;
    }

    if (additionalData.assignment) {
      this.assignmentData.set(vehicleId, additionalData.assignment);
    }

    const position = additionalData.position || null;
    const currentLoad = Math.max(0, this.toNumber(
        additionalData.currentLoad !== undefined ? additionalData.currentLoad : assignment.currentLoad,
        0
    ));
    const currentVolume = Math.max(0, this.toNumber(
        additionalData.currentVolume !== undefined ? additionalData.currentVolume : assignment.currentVolume,
        0
    ));
    const maxLoadCapacity = Math.max(0, this.toNumber(assignment.maxLoadCapacity, 0));
    const maxVolumeCapacity = Math.max(0, this.toNumber(assignment.maxVolumeCapacity, 0));

    const vehicle = {
      id: vehicleId,
      licensePlate: assignment.licensePlate || `车辆${vehicleId}`,
      status: status || assignment.vehicleStatus || 'IDLE',
      currentAssignment: assignment.routeName || assignment.currentAssignment || `任务 ${assignment.assignmentId || ''}`.trim(),
      goodsInfo: assignment.goodsName || '',
      quantity: assignment.quantity || 0,
      startPOI: assignment.startPOIName || null,
      endPOI: assignment.endPOIName || null,
      currentLoad,
      maxLoadCapacity,
      currentVolume,
      maxVolumeCapacity,
      loadPercentage: maxLoadCapacity > 0 ? Math.min(100, (currentLoad / maxLoadCapacity) * 100) : 0,
      volumePercentage: maxVolumeCapacity > 0 ? Math.min(100, (currentVolume / maxVolumeCapacity) * 100) : 0,
      actionDescription: additionalData.actionDescription || assignment.actionDescription || null,
      currentLongitude: position ? position[0] : this.toNumber(
          assignment.vehicleCurrentLon ?? assignment.currentLng ?? assignment.vehicleStartLng ?? assignment.startLng,
          null
      ),
      currentLatitude: position ? position[1] : this.toNumber(
          assignment.vehicleCurrentLat ?? assignment.currentLat ?? assignment.vehicleStartLat ?? assignment.startLat,
          null
      ),
      vrpProgress: additionalData.vrpProgress || assignment.vrpProgress || null
    };

    this.vehicles.push(vehicle);
    console.log(`[VehicleStatusManager] 已创建临时车辆监控记录: ${vehicleId}`);
    return vehicle;
  }

  updateVehicleStatus(vehicleId, status, additionalData = {}) {
    console.log(`[VehicleStatusManager] 更新车辆状态: ${vehicleId} -> ${status}`, additionalData);

    // 1. 更新车辆列表中的状态
    const vehicle = this.ensureVehicleRecord(vehicleId, status, additionalData);
    if (vehicle) {
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

      console.log(`[VehicleStatusManager] 车辆 ${vehicle.licensePlate} 状态已更新: ${oldStatus} -> ${status}`);
      return true;
    } else {
      console.warn(`[VehicleStatusManager] 车辆ID ${vehicleId} 未找到`);
      this.updateVehicleIcon(vehicleId, status);
      return false;
    }
  }

  /**
   * 更新车辆载重信息
   */
  updateVehicleLoadInfo(vehicle, status, data) {
    const assignment = data.assignment || this.assignmentData.get(vehicle.id);
    const isVrp = Boolean(data.vrp || assignment?.vrp);
    const toNumber = (value, fallback = 0) => {
      const numberValue = Number(value);
      return Number.isFinite(numberValue) ? numberValue : fallback;
    };
    const resolvedLoad = toNumber(
        data.currentLoad !== undefined ? data.currentLoad :
            assignment?.currentLoad !== undefined ? assignment.currentLoad :
                vehicle.currentLoad,
        0
    );
    const resolvedVolume = toNumber(
        data.currentVolume !== undefined ? data.currentVolume :
            assignment?.currentVolume !== undefined ? assignment.currentVolume :
                vehicle.currentVolume,
        0
    );
    const applyLoad = (load, volume) => {
      vehicle.currentLoad = Math.max(0, toNumber(load, 0));
      vehicle.currentVolume = Math.max(0, toNumber(volume, 0));

      vehicle.loadPercentage = vehicle.maxLoadCapacity > 0 ?
          Math.min(100, (vehicle.currentLoad / vehicle.maxLoadCapacity) * 100) : 0;
      vehicle.volumePercentage = vehicle.maxVolumeCapacity > 0 ?
          Math.min(100, (vehicle.currentVolume / vehicle.maxVolumeCapacity) * 100) : 0;
    };

    if (data.vrpProgress || assignment?.vrpProgress) {
      vehicle.vrpProgress = {
        ...(vehicle.vrpProgress || {}),
        ...(assignment?.vrpProgress || {}),
        ...(data.vrpProgress || {})
      };
    }

    switch (status) {
      case 'ORDER_DRIVING':
        // 普通任务前往装货点为空车；VRP 中可能已经部分装载，需要保留实时载重。
        if (isVrp && data.preserveLoad) {
          applyLoad(resolvedLoad, resolvedVolume);
        } else {
          applyLoad(0, 0);
        }
        vehicle.actionDescription = data.actionDescription || `前往装货点: ${assignment?.startPOIName || '未知'}`;
        break;

      case 'LOADING':
        applyLoad(resolvedLoad, resolvedVolume);
        vehicle.actionDescription = data.actionDescription || `正在装货...`;
        break;

      case 'TRANSPORT_DRIVING':
        applyLoad(resolvedLoad, resolvedVolume);
        vehicle.actionDescription = data.actionDescription || `运输至: ${assignment?.endPOIName || '未知'}`;
        break;

      case 'UNLOADING':
        applyLoad(resolvedLoad, resolvedVolume);
        vehicle.actionDescription = data.actionDescription || `正在卸货...`;
        break;

      case 'WAITING':
      case 'IDLE':
        if (data.preserveLoad) {
          applyLoad(resolvedLoad, resolvedVolume);
        } else {
          applyLoad(0, 0);
        }
        vehicle.actionDescription = data.actionDescription || '等待任务';
        if (!data.preserveLoad && !data.vrpProgress) {
          vehicle.vrpProgress = null;
        }
        break;

      case 'BREAKDOWN':
        // 故障：保持当前载重
        vehicle.actionDescription = data.actionDescription || '车辆故障';
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
    const vehicle = this.vehicles[this.findVehicleIndex(vehicleId)];
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

    const vrpProgress = vehicle?.vrpProgress || this.assignmentData.get(vehicleId)?.vrpProgress;
    const iconMeta = {
      vrp: Boolean(vrpProgress),
      carriedCount: vrpProgress?.carriedCount || 0,
      loadPercentage: vehicle?.loadPercentage || 0
    };

    // 创建新图标
    const newIcon = createVehicleIcon(32, status, color, iconMeta);

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
    const vehicle = this.vehicles[this.findVehicleIndex(vehicleId)];
    return vehicle?.status || null;
  }

  /**
   * 获取车辆详细信息（包含位置和载重）
   */
  getVehicleInfo(vehicleId) {
    const vehicle = this.vehicles[this.findVehicleIndex(vehicleId)];
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
    this.baseSpeed = 20; // 米/秒

    // 标记引用
    this.movingMarker = routeData.movingMarker;
    this.startMarker = routeData.startMarker;

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

  _toNumber(value, fallback = 0) {
    const numberValue = Number(value);
    return Number.isFinite(numberValue) ? numberValue : fallback;
  }

  _calculateLoadedCargo() {
    const assignment = this.routeData.assignment || {};
    const quantity = Math.max(0, this._toNumber(assignment.quantity, 0));
    const weightPerUnit = Math.max(0, this._toNumber(assignment.goodsWeightPerUnit, 0));
    const volumePerUnit = Math.max(0, this._toNumber(assignment.goodsVolumePerUnit, 0));

    return {
      currentLoad: weightPerUnit * quantity,
      currentVolume: volumePerUnit * quantity
    };
  }

  _applyLoadedCargo(cargo) {
    const currentLoad = Math.max(0, this._toNumber(cargo?.currentLoad, 0));
    const currentVolume = Math.max(0, this._toNumber(cargo?.currentVolume, 0));

    this.routeData.assignment.currentLoad = currentLoad;
    this.routeData.assignment.currentVolume = currentVolume;

    if (this.statusManager) {
      this.statusManager.updateVehicleStatus(this.vehicleId, 'TRANSPORT_DRIVING', {
        assignment: this.routeData.assignment,
        position: this.currentPosition,
        currentLoad,
        currentVolume,
        isLoaded: true
      });
    }
  }

  async _reportLoadingCompleted(localCargo) {
    try {
      const response = await request.post('/api/simulation/assignment-loaded', {
        assignmentId: this.assignmentId,
        vehicleId: this.vehicleId
      });
      const payload = response.data?.data || response.data || {};
      const correctedCargo = {
        currentLoad: this._toNumber(payload.currentLoad, localCargo.currentLoad),
        currentVolume: this._toNumber(payload.currentVolume, localCargo.currentVolume)
      };
      if (this.isCompleted) {
        return correctedCargo;
      }
      this._applyLoadedCargo(correctedCargo);
      console.log(`[VehicleAnimation] ${this.licensePlate} loading payload synced`, correctedCargo);
      return correctedCargo;
    } catch (error) {
      const message = error.response?.data?.message || error.message;
      if (typeof message === 'string' && message.includes('Assignment is closed')) {
        console.warn(`[VehicleAnimation] ${this.licensePlate} loading payload sync skipped: ${message}`);
        return localCargo;
      }
      console.error(`[VehicleAnimation] ${this.licensePlate} loading payload sync failed`, error);
      ElMessage.error(`车辆 ${this.licensePlate} 装货载重同步失败: ${error.response?.data?.message || error.message}`);
      return localCargo;
    }
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

      const loadedCargo = this._calculateLoadedCargo();
      this._applyLoadedCargo(loadedCargo);
      void this._reportLoadingCompleted(loadedCargo);

      // 切换到第二阶段
      this.currentStage = 2;
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId, 'TRANSPORT_DRIVING', {
          assignment: this.routeData.assignment,
          position: this.currentPosition,
          currentLoad: loadedCargo.currentLoad,
          currentVolume: loadedCargo.currentVolume,
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
      this.routeData.assignment.currentLoad = 0;
      this.routeData.assignment.currentVolume = 0;
      if (this.statusManager) {
        this.statusManager.updateVehicleStatus(this.vehicleId, 'WAITING', {
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

// ==================== VRP 专属动画类 (多段节点插值版) ====================
class VrpVehicleAnimation extends VehicleAnimation {
  constructor(assignment, routeData, statusManager) {
    super(assignment, routeData, statusManager);

    // VRP 专有属性
    this.stages = routeData.stages || [];
    this.currentStageIndex = 0; // 当前跑到了第几段
    this.runtimeLoad = 0;
    this.runtimeVolume = 0;
    this.loadedCount = 0;
    this.unloadedCount = 0;
    this.carriedCount = 0;
    this.nextNode = this.stages[0]?.nodeInfo || null;

    // 计算每一段的 segments (用于物理插值)
    this.stageSegments = this.stages.map(stage => this._calculateSegments(stage.path));
    this._syncRuntimeState();
  }

  // 重写：获取当前路径
  _getCurrentPath() {
    if (this.currentStageIndex >= this.stages.length) return [];
    return this.stages[this.currentStageIndex].path;
  }

  // 重写：获取当前路段数据
  _getCurrentSegments() {
    if (this.currentStageIndex >= this.stages.length) return { segments: [], totalLength: 0 };
    return this.stageSegments[this.currentStageIndex];
  }

  _getNextNode() {
    return this.stages[this.currentStageIndex]?.nodeInfo || null;
  }

  _getTravelStatus() {
    return this.runtimeLoad > 0 ? 'TRANSPORT_DRIVING' : 'ORDER_DRIVING';
  }

  _getActionDescription(node = this._getNextNode()) {
    if (!node) {
      return this.runtimeLoad > 0 ? '运输中' : '等待任务';
    }
    return node.actionType === 'LOAD'
        ? `前往装货点: ${node.poiName || '未知'}`
        : `前往卸货点: ${node.poiName || '未知'}`;
  }

  _syncRuntimeState(extra = {}) {
    const nextNode = this._getNextNode();
    this.nextNode = nextNode;
    const progress = {
      currentStageIndex: this.currentStageIndex,
      totalStages: this.stages.length,
      loadedCount: this.loadedCount,
      unloadedCount: this.unloadedCount,
      carriedCount: this.carriedCount,
      currentLoad: this.runtimeLoad,
      currentVolume: this.runtimeVolume,
      nextNode,
      ...extra
    };

    this.routeData.assignment.currentLoad = this.runtimeLoad;
    this.routeData.assignment.currentVolume = this.runtimeVolume;
    this.routeData.assignment.vrpProgress = progress;
    return progress;
  }

  _updateMarkerPosition() {
    if (!this.movingMarker || !this.currentPosition) return;

    try {
      this.movingMarker.setPosition([
        this.currentPosition[0],
        this.currentPosition[1]
      ]);

      if (this.statusManager) {
        const progress = this._syncRuntimeState();
        this.statusManager.updateVehicleStatus(this.vehicleId, this._getTravelStatus(), {
          assignment: this.routeData.assignment,
          position: this.currentPosition,
          currentLoad: this.runtimeLoad,
          currentVolume: this.runtimeVolume,
          actionDescription: this._getActionDescription(),
          vrpProgress: progress,
          preserveLoad: true
        });
      }
    } catch (error) {
      console.warn(`[VrpAnimation] 更新车辆标记位置失败: ${this.licensePlate}`, error);
    }
  }

  // 重写：单段完成后的节点处理逻辑
  async _completeCurrentStage() {
    const currentStage = this.stages[this.currentStageIndex];
    const nodeInfo = currentStage.nodeInfo;

    const currentPath = this._getCurrentPath();
    if (currentPath && currentPath.length > 0) {
      this.currentPosition = [...currentPath[currentPath.length - 1]];
      this._updateMarkerPosition();
    }

    // 累加 Delta 值 (装货是正数，卸货是负数)
    this.runtimeLoad = Math.max(0, this.runtimeLoad + (nodeInfo.weightDelta || 0));
    this.runtimeVolume = Math.max(0, this.runtimeVolume + (nodeInfo.volumeDelta || 0));

    if (nodeInfo.actionType === 'LOAD') {
      this.loadedCount += 1;
      this.carriedCount += 1;
    } else if (nodeInfo.actionType === 'UNLOAD') {
      this.unloadedCount += 1;
      this.carriedCount = Math.max(0, this.carriedCount - 1);
    }

    // 触发装卸货动作
    const actionStatus = nodeInfo.actionType === 'LOAD' ? 'LOADING' : 'UNLOADING';
    if (this.statusManager) {
      const progress = this._syncRuntimeState({
        activeNode: nodeInfo,
        activeAction: nodeInfo.actionType
      });
      this.statusManager.updateVehicleStatus(this.vehicleId, actionStatus, {
        assignment: this.routeData.assignment,
        position: this.currentPosition,
        currentLoad: this.runtimeLoad,
        currentVolume: this.runtimeVolume,
        actionDescription: `${nodeInfo.actionType === 'LOAD' ? '正在装货' : '正在卸货'}: ${nodeInfo.poiName || '未知'}`,
        vrpProgress: progress,
        preserveLoad: true
      });
    }

    console.log(`[VrpAnimation] ${this.licensePlate} 到达 ${nodeInfo.poiName}, 动作: ${nodeInfo.actionType}`);

    // 模拟装卸停留 2 秒 (受全局速度因子影响)
    await this._waitWithSpeedFactor(2000);

    // 推进到下一段
    this.currentStageIndex++;

    if (this.currentStageIndex < this.stages.length) {
      // 还没跑完，继续跑下一段
      if (this.statusManager) {
        const progress = this._syncRuntimeState();
        this.statusManager.updateVehicleStatus(this.vehicleId, this._getTravelStatus(), {
          assignment: this.routeData.assignment,
          position: this.currentPosition,
          currentLoad: this.runtimeLoad,
          currentVolume: this.runtimeVolume,
          actionDescription: this._getActionDescription(),
          vrpProgress: progress,
          preserveLoad: true
        });
      }
      this.animationTime = 0;
      this.lastUpdateTime = performance.now();
      this.currentProgress = 0;
      this._animate();
    } else {
      // 所有阶段全部跑完，任务结束！
      this.runtimeLoad = 0;
      this.runtimeVolume = 0;
      this.carriedCount = 0;
      if (this.statusManager) {
        const progress = this._syncRuntimeState({
          currentStageIndex: this.stages.length,
          nextNode: null
        });
        this.statusManager.updateVehicleStatus(this.vehicleId, 'WAITING', {
          assignment: {
            ...this.routeData.assignment,
            currentLoad: 0,
            currentVolume: 0
          },
          position: this.currentPosition,
          currentLoad: 0,
          currentVolume: 0,
          actionDescription: '等待任务',
          vrpProgress: progress
        });
      }

      this.isCompleted = true;

      // 通知后端到达！
      await handleVehicleArrived(this.assignmentId, this.vehicleId, nodeInfo.poiId, this.licensePlate);

      // ================= 🌟 核心修复：清理整条路线的视觉残留 =================
      setTimeout(() => {
        // 1. 调用父类的清理（负责清理移动的小车本身）
        this.cleanup();

        // 2. 调用我们在绘制时精心准备的 routeData.cleanup
        // 它会遍历 elements 数组，把所有的紫线、沿途的工厂Marker全部从地图上抹去
        if (this.routeData && typeof this.routeData.cleanup === 'function') {
          this.routeData.cleanup();
        }

        // 3. 从前端的全局活跃路线池中彻底抹除这条记录，防止重复渲染
        if (activeRoutes.value && activeRoutes.value.has(this.assignmentId)) {
          activeRoutes.value.delete(this.assignmentId);
        }

        // 4. 从动画管理器中注销
        this.manager.removeAnimation(this.assignmentId);

        console.log(`🧹 [VRP 清理] 任务 ${this.assignmentId} 的路线及节点已从地图上移除`);
      }, 1000); // 延迟 1 秒消失，给用户一个“到达终点”的视觉缓冲期
      // =========================================================================

      this.onCompleteCallbacks.forEach(callback => callback(this));
    }
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
    let animation;
    if (assignment.vrp) {
      animation = new VrpVehicleAnimation(assignment, routeData, this.statusManager);
    } else {
      animation = new VehicleAnimation(assignment, routeData, this.statusManager);
    }
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
    this.globalSpeedFactor = Math.max(1, Math.min(200, factor));
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
const arrivalAckInFlight = new Set();
const arrivalAckCompleted = new Set();

// --- 车辆到达处理函数 ---
const acknowledgeExperimentVisualArrival = async (assignmentId, vehicleId) => {
  if (!isExperimentRunActive.value || !assignmentId) {
    return;
  }
  try {
    const response = await request.post('/api/simulation/experiments/dispatch-comparison/visual-arrival-ack', {
      assignmentId,
      vehicleId
    });
    experimentRun.status = unwrapApiData(response) || experimentRun.status;
  } catch (error) {
    console.warn('[ExperimentVisualAck] failed', error?.response?.data?.message || error?.message || error);
  }
};

const shouldCompensateExperimentVisualAck = (assignmentId) => {
  if (!isExperimentRunActive.value || assignmentId === null || assignmentId === undefined) {
    return false;
  }
  const missingIds = experimentRun.status?.missingVisualArrivalAssignmentIds;
  if (!Array.isArray(missingIds) || missingIds.length === 0) {
    return false;
  }
  const targetId = String(assignmentId);
  return missingIds.some(id => String(id) === targetId);
};

const arrivalMessageOf = (error) => {
  return error?.response?.data?.message
      || error?.response?.data?.error
      || error?.message
      || '';
};

const isIgnorableExperimentArrivalError = (error) => {
  if (!isExperimentRunActive.value) {
    return false;
  }
  const message = arrivalMessageOf(error).toLowerCase();
  return error?.response?.status === 404
      || message.includes('closed assignment')
      || message.includes('assignment is closed')
      || message.includes('already completed')
      || message.includes('no active visual experiment run')
      || message.includes('duplicate');
};

const handleVehicleArrived = async (assignmentId, vehicleId, endPOIId, licensePlate) => {
  const arrivalKey = String(assignmentId || '');
  if (!arrivalKey) {
    return false;
  }
  if (arrivalAckCompleted.has(arrivalKey)) {
    console.info(`[VehicleArrival] duplicate completed arrival ignored: ${arrivalKey}`);
    return true;
  }
  if (arrivalAckInFlight.has(arrivalKey)) {
    console.info(`[VehicleArrival] duplicate in-flight arrival ignored: ${arrivalKey}`);
    return true;
  }
  arrivalAckInFlight.add(arrivalKey);
  try {
    console.log(`处理车辆到达: ${licensePlate} (Assignment: ${assignmentId})`);

    // 1. 调用车辆到达接口
    await request.post('/api/simulation/vehicle-arrived', {
      assignmentId: assignmentId,
      vehicleId: vehicleId,
      endPOIId: endPOIId
    });

    console.log(`车辆 ${licensePlate} 到达处理完成`);

    // 2. 立即更新车辆状态为 WAITING，载重归零
    await acknowledgeExperimentVisualArrival(assignmentId, vehicleId);
    arrivalAckCompleted.add(arrivalKey);
    clearRouteByAssignmentId(assignmentId, vehicleId);

    // 2. 等待后端处理完成
    setTimeout(async () => {
      await updateVehicleInfo();
      console.log(`车辆 ${licensePlate} 状态已刷新`);
    }, 500);
    return true;
  } catch (error) {
    if (isIgnorableExperimentArrivalError(error)) {
      console.info('[VehicleArrival] ignored experiment arrival lifecycle error:', arrivalMessageOf(error));
      arrivalAckCompleted.add(arrivalKey);
      return true;
    }
    console.error('车辆到达处理失败:', error);
    ElMessage.error(`车辆 ${licensePlate} 状态更新失败: ${arrivalMessageOf(error) || error}`);
    return false;
  } finally {
    arrivalAckInFlight.delete(arrivalKey);
  }
};

// --- 核心仿真方法 ---

/**
 * 启动仿真
 */
const startSimulation = async () => {
  if (resetInProgress.value) {
    return;
  }
  if (isExperimentRunActive.value) {
    ElMessage.warning('实验运行中不能启动普通仿真');
    return;
  }
  if (hasPreparedExperimentScenario.value) {
    ElMessage.warning('已准备实验场景，请清除实验标记后再启动普通仿真');
    return;
  }

  try {
    const runGeneration = beginSimulationGeneration();
    console.log("开始仿真");

    // 启动后端仿真
    await simulationController.startSimulation({
      useHeuristic: useHeuristicDispatch.value,
      strategy: useHeuristicDispatch.value ? 'HEURISTIC' : 'ORIGINAL'
    });
    isSimulationRunning.value = true;

    await updateVehicleInfo();

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
      scheduleAssignmentDrawing(fetchCurrentAssignments, runGeneration, 'initial assignments');
    }

    // 启动定时更新
    startSimulationTimer();

    // 初始化车辆信息

    ElMessage.success('仿真已启动');

  } catch (error) {
    console.error("启动仿真模拟失败：", error);
    ElMessage.error('启动仿真失败：' + error.message);
    isSimulationRunning.value = false;
    invalidateSimulationGeneration();
  }
  arrivalMonitor.startMonitoring(getVehiclePositions, getPOIList);
};

/**
 * 暂停仿真
 */
const pauseSimulation = async () => {
  if (resetInProgress.value) {
    return;
  }
  if (isExperimentRunActive.value) {
    ElMessage.warning('实验运行中请使用实验暂停按钮');
    return;
  }

  try {
    console.log("已暂停仿真");

    // 暂停动画管理器
    isSimulationRunning.value = false;
    stopSimulationTimer();
    invalidateSimulationGeneration();
    animationManager.pauseAll();

    // 暂停后端仿真
    await simulationController.stopSimulation();

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
  if (resetInProgress.value) {
    return;
  }
  if (isExperimentRunActive.value) {
    ElMessage.warning('实验运行中不能执行普通重置');
    return;
  }

  let confirmResult;
  try {
    // 简洁版确认对话框
    confirmResult = await ElMessageBox.confirm(
        '确定要重置仿真吗？',
        '确认重置',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    );
  } catch (_) {
    ElMessage.info('已取消重置操作');
    return;
  }

  resetInProgress.value = true;
  try {
    if (confirmResult === 'confirm') {
      console.log("重置仿真");

      // 先停稳前端本地生命周期，避免等待后端时继续绘制旧路线
      isSimulationRunning.value = false;
      invalidateSimulationGeneration();
      stopSimulationTimer();
      resetSimulationCostDisplay();

      // 停止并清理所有动画
      if (animationManager) {
        animationManager.stopAll();
      }

      // 清除缓存
      routePlanningCache.clear();
      assignmentStates.clear();

      // 清理所有绘制的路线
      cleanupAllActiveRoutes();

      // 清除所有可视化元素
      clearPOIMarkers();
      clearDrawnRoutes();

      // 重置数据
      currentPOIs.value = [];
      vehicles.splice(0, vehicles.length);
      syncTransportMonitorData({});

      // 重置统计信息
      stats.running = 0;
      stats.poiCount = 0;
      stats.tasks = 0;
      stats.anomalyRate = 0;

      speedFactor.value = 1;
      if (animationManager) {
        animationManager.setGlobalSpeedFactor(1);
      }

      const resetResult = await simulationController.resetSimulation();
      syncTransportMonitorData({});
      if (!resetResult?.success) {
        throw new Error(resetResult?.message || '后端清理失败，请重试 reset');
      }

      ElMessage.success('仿真已重置');
    }

  } catch (error) {
    console.error('重置仿真失败:', error);
    ElMessage.error(`后端清理失败，请重试 reset：${error.message || error}`);
  } finally {
    resetInProgress.value = false;
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
    if (isTransportAnimationActive()) {
      const runGeneration = simulationGeneration.value;
      await fetchSimulationCosts();

      // Refresh monitor data before route drawing.
      await updateVehicleInfo();

      // Cleanup after the local monitor snapshot is stable.
      await checkAndCleanupCompletedAssignments();

      if (isExperimentRunActive.value) {
        await syncExperimentRunAfterStatusRefresh();
      }

      // Draw new assignments asynchronously so route planning does not block monitor data.
      if (isActiveTransportGeneration(runGeneration)) {
        scheduleAssignmentDrawing(fetchAndDrawNewAssignments, runGeneration, 'new assignments');
      }

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
  arrivalMonitor.stopMonitoring();
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
    console.error("更新POI数据失败：", error);
    ElMessage.error('更新POI数据失败');
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

const formatCoordinate = (value) => {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue.toFixed(6) : '未知';
};

const formatDelta = (value, unit) => {
  const numberValue = Number(value || 0);
  const sign = numberValue > 0 ? '+' : '';
  return `${sign}${numberValue.toFixed(2)}${unit}`;
};

// 显示任务起点/终点/VRP节点信息窗口
const showTaskPointInfoWindow = (pointInfo) => {
  if (!map || !AMapLib || !pointInfo) return;

  const lng = Number(pointInfo.lng);
  const lat = Number(pointInfo.lat);
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    console.warn('任务点坐标无效，无法打开信息窗口:', pointInfo);
    return;
  }

  const assignment = pointInfo.assignment || {};
  const actionText = pointInfo.actionType === 'LOAD' || pointInfo.role === 'start'
      ? '装货'
      : pointInfo.actionType === 'UNLOAD' || pointInfo.role === 'end'
          ? '卸货'
          : '途经';
  const typeText = pointInfo.poiType ? getPOITypeText(pointInfo.poiType) : '未知';
  const sequenceText = pointInfo.sequenceIndex !== undefined && pointInfo.sequenceIndex !== null
      ? `<p style="margin: 4px 0; color: #000;"><strong>节点序号:</strong> ${Number(pointInfo.sequenceIndex) + 1}</p>`
      : '';
  const deltaText = pointInfo.weightDelta !== undefined || pointInfo.volumeDelta !== undefined
      ? `
        <p style="margin: 4px 0; color: #000;"><strong>重量变化:</strong> ${formatDelta(pointInfo.weightDelta, 't')}</p>
        <p style="margin: 4px 0; color: #000;"><strong>体积变化:</strong> ${formatDelta(pointInfo.volumeDelta, 'm³')}</p>
      `
      : '';

  const content = `
    <div style="padding: 12px; min-width: 260px; color: #000;">
      <h3 style="margin: 0 0 8px 0; color: #000; font-size: 16px;">${actionText}: ${pointInfo.poiName || '未知节点'}</h3>
      ${sequenceText}
      <p style="margin: 4px 0; color: #000;"><strong>类型:</strong> ${typeText}</p>
      <p style="margin: 4px 0; color: #000;"><strong>坐标:</strong> ${formatCoordinate(lng)}, ${formatCoordinate(lat)}</p>
      ${deltaText}
      <div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #eee;">
        <p style="margin: 4px 0; color: #000;"><strong>Assignment ID:</strong> ${assignment.assignmentId || '-'}</p>
        <p style="margin: 4px 0; color: #000;"><strong>车辆:</strong> ${assignment.licensePlate || '未知'}</p>
        <p style="margin: 4px 0; color: #000;"><strong>路线:</strong> ${assignment.routeName || '未命名路线'}</p>
        <p style="margin: 4px 0; color: #000;"><strong>货物:</strong> ${assignment.goodsName || '未知'} (${assignment.quantity || 0}件)</p>
      </div>
    </div>
  `;

  const infoWindow = new AMapLib.InfoWindow({
    content,
    offset: new AMapLib.Pixel(0, -34)
  });
  infoWindow.open(map, [lng, lat]);
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
const monitorShipments = reactive([]);
const monitorAssignments = reactive([]);
const monitorVehicles = reactive([]);
const monitorLinks = reactive([]);
const monitorSummary = reactive({
  activeShipmentCount: 0,
  activeAssignmentCount: 0,
  activeVehicleCount: 0
});

const syncTransportMonitorData = (monitorData = {}) => {
  monitorShipments.splice(0, monitorShipments.length, ...(monitorData.shipments || []));
  monitorAssignments.splice(0, monitorAssignments.length, ...(monitorData.assignments || []));
  monitorVehicles.splice(0, monitorVehicles.length, ...(monitorData.vehicles || []));
  monitorLinks.splice(0, monitorLinks.length, ...(monitorData.links || []));

  const summary = monitorData.summary || {};
  monitorSummary.activeShipmentCount = summary.activeShipmentCount || 0;
  monitorSummary.activeAssignmentCount = summary.activeAssignmentCount || 0;
  monitorSummary.activeVehicleCount = summary.activeVehicleCount || 0;

  if (monitorData.summary || monitorData.shipments || monitorData.assignments || monitorData.vehicles) {
    recordDashboardMonitorSnapshot();
    updateRuntimeDashboardCharts();
  }
};

const recordDashboardMonitorSnapshot = () => {
  const now = new Date();
  const timeStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
  const vehiclesForStats = currentVehicleDataset.value;

  dashboardHistory.times.push(timeStr);
  dashboardHistory.activeShipments.push(monitorSummary.activeShipmentCount || monitorShipments.length);
  dashboardHistory.activeAssignments.push(monitorSummary.activeAssignmentCount || monitorAssignments.length);
  dashboardHistory.activeVehicles.push(monitorSummary.activeVehicleCount || vehiclesForStats.length);
  dashboardHistory.waitingItems.push(shipmentItemStateSummary.value.waiting);
  dashboardHistory.inProgressItems.push(shipmentItemStateSummary.value.inProgress);
  dashboardHistory.completedItems.push(shipmentItemStateSummary.value.completed);
  dashboardHistory.avgLoadUsage.push(Number(averageUsage(vehiclesForStats, 'currentLoad', 'maxLoadCapacity').toFixed(2)));
  dashboardHistory.avgVolumeUsage.push(Number(averageUsage(vehiclesForStats, 'currentVolume', 'maxVolumeCapacity').toFixed(2)));

  if (dashboardHistory.times.length > DASHBOARD_HISTORY_LIMIT) {
    dashboardHistory.times.shift();
    dashboardHistory.activeShipments.shift();
    dashboardHistory.activeAssignments.shift();
    dashboardHistory.activeVehicles.shift();
    dashboardHistory.waitingItems.shift();
    dashboardHistory.inProgressItems.shift();
    dashboardHistory.completedItems.shift();
    dashboardHistory.avgLoadUsage.shift();
    dashboardHistory.avgVolumeUsage.shift();
  }
};

const fetchTransportMonitor = async () => {
  const response = await request.get('/api/simulation/monitor/active');
  const monitorData = response.data || {};
  syncTransportMonitorData(monitorData);
  return monitorData;
};

/**
 * 更新车辆信息 - 修复版本
 * 从有效的后端接口 `/api/assignments/active` 获取任务数据，并提取车辆信息。
 * 此函数为侧边栏车辆列表和统计信息提供数据。
 */
const updateVehicleInfo = async () => {
  try {
    console.log('[运输监控] 正在从 /api/simulation/monitor/active 获取数据...');

    const monitorData = await fetchTransportMonitor();
    const activeAssignments = Array.isArray(monitorData.assignments) ? monitorData.assignments : [];
    const activeMonitorVehicles = Array.isArray(monitorData.vehicles) ? monitorData.vehicles : [];
    const previousVehicleMap = new Map(vehicles.map(vehicle => [vehicle.id, { ...vehicle }]));
    const toNumber = (value, fallback = 0) => {
      const numberValue = Number(value);
      return Number.isFinite(numberValue) ? numberValue : fallback;
    };
    const isKnownStatus = status => Boolean(status && status !== 'UNKNOWN');

    console.log(`[车辆信息] 获取到 ${activeAssignments?.length || 0} 个活动任务`);

    // Build the next snapshot first, then replace vehicles atomically.

    const vehicleMap = new Map(); // 用于按车辆ID去重

    if (activeAssignments && Array.isArray(activeAssignments)) {
      activeAssignments.forEach(assignment => {
        if (assignment.vehicleId) {
          const previous = previousVehicleMap.get(assignment.vehicleId) || {};
          const vehicle = {
            id: assignment.vehicleId,
            licensePlate: assignment.licensePlate || `车辆${assignment.vehicleId}`,
            status: isKnownStatus(assignment.vehicleStatus)
                ? assignment.vehicleStatus
                : (isKnownStatus(previous.status) ? previous.status : 'IDLE'),
            currentAssignment: assignment.routeName,
            goodsInfo: assignment.goodsName,
            quantity: assignment.quantity,
            startPOI: assignment.startPOIName,
            endPOI: assignment.endPOIName,
            // 载重信息
            currentLoad: Math.max(0, toNumber(assignment.currentLoad, previous.currentLoad || 0)),
            maxLoadCapacity: Math.max(0, toNumber(assignment.maxLoadCapacity, previous.maxLoadCapacity || 0)),
            // 载容信息
            currentVolume: Math.max(0, toNumber(assignment.currentVolume, previous.currentVolume || 0)),
            maxVolumeCapacity: Math.max(0, toNumber(assignment.maxVolumeCapacity, previous.maxVolumeCapacity || 0)),
            // 货物单位信息
            goodsWeightPerUnit: toNumber(assignment.goodsWeightPerUnit, 0),
            goodsVolumePerUnit: toNumber(assignment.goodsVolumePerUnit, 0),
            // 为"到达检测"功能预留的字段
            currentPOIName: assignment.currentPOIName || previous.currentPOIName || null,
            lastArrivalPOI: previous.lastArrivalPOI || null,
            recentlyArrived: previous.recentlyArrived || false,
            actionDescription: previous.actionDescription || null,
            vrpProgress: previous.vrpProgress || null
          };

          if (animationManager && animationManager.animations.has(assignment.assignmentId)) {
            const activeAnimation = animationManager.animations.get(assignment.assignmentId);

            // Merge local animation status without overriding backend load for normal assignments.
            if (vehicleStatusManager.value) {
              const localStatus = vehicleStatusManager.value.getVehicleStatus(assignment.vehicleId);
              if (isKnownStatus(localStatus)) {
                vehicle.status = localStatus;
              }
            }

            if (activeAnimation.routeData && activeAnimation.routeData.assignment) {
              const localAssignment = activeAnimation.routeData.assignment;
              const progress = localAssignment.vrpProgress;
              if (progress) {
                vehicle.vrpProgress = progress;
                if (Number.isFinite(Number(progress.currentLoad))) {
                  vehicle.currentLoad = Math.max(0, toNumber(progress.currentLoad, vehicle.currentLoad));
                }
                if (Number.isFinite(Number(progress.currentVolume))) {
                  vehicle.currentVolume = Math.max(0, toNumber(progress.currentVolume, vehicle.currentVolume));
                }
                const nextNode = vehicle.vrpProgress.nextNode;
                if (nextNode) {
                  vehicle.actionDescription = nextNode.actionType === 'LOAD'
                      ? `前往装货点: ${nextNode.poiName || '未知'}`
                      : `前往卸货点: ${nextNode.poiName || '未知'}`;
                }
              } else {
                if (Number.isFinite(Number(localAssignment.currentLoad))) {
                  vehicle.currentLoad = Math.max(0, toNumber(localAssignment.currentLoad, vehicle.currentLoad));
                }
                if (Number.isFinite(Number(localAssignment.currentVolume))) {
                  vehicle.currentVolume = Math.max(0, toNumber(localAssignment.currentVolume, vehicle.currentVolume));
                }
              }
            }
          }

          // 计算载重/载容百分比
          vehicle.loadPercentage = vehicle.maxLoadCapacity > 0 ?
              Math.min(100, (vehicle.currentLoad / vehicle.maxLoadCapacity) * 100) : 0;
          vehicle.volumePercentage = vehicle.maxVolumeCapacity > 0 ?
              Math.min(100, (vehicle.currentVolume / vehicle.maxVolumeCapacity) * 100) : 0;

          // 通过Map去重，避免同一车辆在列表中出现多次
          vehicleMap.set(assignment.vehicleId, vehicle);
        }
      });
    }

    if (activeMonitorVehicles && Array.isArray(activeMonitorVehicles)) {
      activeMonitorVehicles.forEach(monitorVehicle => {
        if (!monitorVehicle.vehicleId || vehicleMap.has(monitorVehicle.vehicleId)) {
          return;
        }
        const previous = previousVehicleMap.get(monitorVehicle.vehicleId) || {};
        const vehicle = {
          id: monitorVehicle.vehicleId,
          licensePlate: monitorVehicle.licensePlate || `车辆${monitorVehicle.vehicleId}`,
          status: isKnownStatus(monitorVehicle.status)
              ? monitorVehicle.status
              : (isKnownStatus(previous.status) ? previous.status : 'IDLE'),
          currentAssignment: `任务 ${monitorVehicle.assignmentIds?.join(', ') || '无'}`,
          goodsInfo: '',
          quantity: 0,
          currentLoad: Math.max(0, toNumber(monitorVehicle.currentLoad, previous.currentLoad || 0)),
          maxLoadCapacity: Math.max(0, toNumber(monitorVehicle.maxLoadCapacity, previous.maxLoadCapacity || 0)),
          currentVolume: Math.max(0, toNumber(monitorVehicle.currentVolume, previous.currentVolume || 0)),
          maxVolumeCapacity: Math.max(0, toNumber(monitorVehicle.maxVolumeCapacity, previous.maxVolumeCapacity || 0)),
          currentPOIName: previous.currentPOIName || null,
          lastArrivalPOI: previous.lastArrivalPOI || null,
          recentlyArrived: previous.recentlyArrived || false,
          actionDescription: previous.actionDescription || null,
          vrpProgress: previous.vrpProgress || null
        };
        vehicle.loadPercentage = vehicle.maxLoadCapacity > 0 ?
            Math.min(100, (vehicle.currentLoad / vehicle.maxLoadCapacity) * 100) : 0;
        vehicle.volumePercentage = vehicle.maxVolumeCapacity > 0 ?
            Math.min(100, (vehicle.currentVolume / vehicle.maxVolumeCapacity) * 100) : 0;
        vehicleMap.set(monitorVehicle.vehicleId, vehicle);
      });
    }

    // 将处理好的车辆信息添加到响应式数组
    vehicles.splice(0, vehicles.length, ...Array.from(vehicleMap.values()));

    // 更新统计信息
    stats.running = vehicleMonitorDisplayVehicles.value.length;
    stats.tasks = monitorSummary.activeAssignmentCount || activeAssignments.length;

    console.log(`[车辆信息] 更新完成，共 ${vehicles.length} 辆车`);
    return vehicles;

  } catch (error) {
    console.error('[车辆信息] 获取任务数据失败:', error);
    // 此处选择静默失败，不影响仿真主流程
    return [];
  }
};

// 获取车辆详细信息
const getVehicleDetail = async (vehicleId) => {
  try {
    const response = await request.get(`/api/vehicles/${vehicleId}`);
    return response.data;
  } catch (error) {
    console.error(`获取车辆${vehicleId}详细信息失败:`, error);
    ElMessage.error(`获取车辆信息失败: ${vehicleId}`);
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
  drawnVehicleIconIds.value = new Set();
  syncRegisteredVehicleStats();
};

// Frontend-only visual cleanup for experiment abort and terminal states.
const clearFrontendSimulationVisuals = () => {
  if (animationManager) {
    animationManager.stopAll();
  }
  cleanupAllActiveRoutes();
  clearPOIMarkers();
  clearDrawnRoutes();
  routePlanningCache.clear();
  assignmentStates.clear();
  arrivalAckInFlight.clear();
  arrivalAckCompleted.clear();
  syncTransportMonitorData({});
  currentPOIs.value = [];
  vehicles.splice(0, vehicles.length);
  stats.running = 0;
  stats.poiCount = 0;
  stats.tasks = 0;
  stats.anomalyRate = 0;
};

// Create vehicle icons with status-aware styling.
const createVehicleIcon = (size = 32, status = 'IDLE', color = null, meta = {}) => {
  const el = document.createElement('div');
  el.style.width = `${size}px`;
  el.style.height = `${size}px`;
  el.style.borderRadius = '50%';
  el.style.display = 'flex';
  el.style.alignItems = 'center';
  el.style.justifyContent = 'center';
  el.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)';
  el.style.border = '2px solid white';
  el.style.position = 'relative';

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

  if (meta?.vrp && Number(meta.carriedCount) > 0) {
    const badge = document.createElement('span');
    badge.textContent = String(Math.min(99, Number(meta.carriedCount)));
    badge.style.position = 'absolute';
    badge.style.top = '-7px';
    badge.style.right = '-7px';
    badge.style.minWidth = '16px';
    badge.style.height = '16px';
    badge.style.padding = '0 4px';
    badge.style.borderRadius = '999px';
    badge.style.background = '#111827';
    badge.style.border = '2px solid #fff';
    badge.style.color = '#fff';
    badge.style.fontSize = '10px';
    badge.style.lineHeight = '14px';
    badge.style.fontWeight = '700';
    badge.style.textAlign = 'center';
    badge.style.boxSizing = 'border-box';
    el.appendChild(badge);
  }

  return el;
};

// 清除特定Assignment的路线
const clearRouteByAssignmentId = (assignmentId, vehicleId = null) => {
  const routeData = activeRoutes.value.get(assignmentId);
  const targetVehicleId = vehicleId || routeData?.assignment?.vehicleId || null;
  if (!routeData) {
    drawnAssignmentIds.value.delete(assignmentId);
    if (targetVehicleId && vehicleStatusManager.value) {
      const marker = vehicleStatusManager.value.vehicleMarkers.get(targetVehicleId);
      if (marker) {
        try {
          map?.remove(marker);
        } catch (error) {
          try {
            marker.setMap?.(null);
          } catch (innerError) {
            console.warn(`Failed to cleanup vehicle ${targetVehicleId} marker`, innerError);
          }
        }
      }
      vehicleStatusManager.value.vehicleMarkers.delete(targetVehicleId);
      vehicleStatusManager.value.assignmentData.delete(targetVehicleId);
      unmarkDrawnVehicleIcon(targetVehicleId);
    }
    return;
  }
  if (routeData) {
    // 清理动画
    if (animationManager) {
      animationManager.removeAnimation(assignmentId);
    }

    cleanupRouteData(routeData);

    // 从映射中移除
    activeRoutes.value.delete(assignmentId);
    drawnAssignmentIds.value.delete(assignmentId);
    unmarkDrawnVehicleIcon(targetVehicleId);

    console.log(`已清理Assignment ${assignmentId} 的路线`);
  }
};

// 获取当前活跃的Assignment（用于初始加载）
const fetchCurrentAssignments = async (runGeneration = simulationGeneration.value) => {
  try {
    if (!isActiveTransportGeneration(runGeneration)) return;
    const response = await request.get('/api/assignments/active');
    if (!isActiveTransportGeneration(runGeneration)) return;
    const assignments = response.data;

    if (assignments && assignments.length > 0) {
      // 为每个Assignment绘制两段路线
      for (const assignment of assignments) {
        if (assignment && assignment.assignmentId) {
          // 检查是否已有动画
          if (!animationManager.animations.has(assignment.assignmentId)) {
            let routeData = null;
            if (assignment.vrp === true) {
              routeData = await drawMultiStageRouteForVrpAssignment(assignment, runGeneration);
            } else {
              routeData = await drawTwoStageRouteForAssignment(assignment, runGeneration);
            }
            if (!isActiveTransportGeneration(runGeneration)) return;
            if (routeData) {
              drawnAssignmentIds.value.add(assignment.assignmentId);
            }
          }
        }
      }

      // 更新统计信息
      stats.tasks = drawnAssignmentIds.value.size;
    }
  } catch (error) {
    console.error('获取当前Assignment失败:', error);
    ElMessage.error('获取当前任务失败');
  }
};

// 增量获取并绘制新Assignment
const fetchAndDrawNewAssignments = async (runGeneration = simulationGeneration.value) => {
  try {
    if (!isActiveTransportGeneration(runGeneration)) return;
    const response = await request.get('/api/assignments/new');
    if (!isActiveTransportGeneration(runGeneration)) return;
    const newAssignments = response.data;

    if (!newAssignments || newAssignments.length === 0) {
      console.log('没有新增的Assignment');
      return;
    }

    console.log(`获取到 ${newAssignments.length} 个新增Assignment`);

    // 绘制新路线
    for (const assignment of newAssignments) {
      if (!isActiveTransportGeneration(runGeneration)) return;
      if (assignment && assignment.assignmentId) {
        if (!drawnAssignmentIds.value.has(assignment.assignmentId)) {
          let routeData = null;
          if (assignment.vrp === true) {
            routeData = await drawMultiStageRouteForVrpAssignment(assignment, runGeneration);
          } else {
            routeData = await drawTwoStageRouteForAssignment(assignment, runGeneration);
          }

          if (!isActiveTransportGeneration(runGeneration)) return;
          if (!routeData) {
            continue;
          }
          drawnAssignmentIds.value.add(assignment.assignmentId);

          try {
            await request.post(`/api/assignments/mark-drawn/${assignment.assignmentId}`);
          } catch (error) {
            console.error(`标记Assignment ${assignment.assignmentId} 为已绘制失败:`, error);
            ElMessage.error(`标记任务 ${assignment.assignmentId} 失败`);
          }
        }
      }
    }

    stats.tasks = drawnAssignmentIds.value.size;

  } catch (error) {
    console.error('获取并绘制新增Assignment失败:', error);
    ElMessage.error('获取新增任务失败');
  }
};

// 为Assignment绘制两段路线（修复版）
const drawTwoStageRouteForAssignment = async (assignment, runGeneration = simulationGeneration.value) => {
  if (!AMapLib || !map) return null;
  if (!isActiveTransportGeneration(runGeneration)) return null;

  let routeData = null;
  try {
    // 检查是否已有该Assignment的路线数据
    if (activeRoutes.value.has(assignment.assignmentId)) {
      const existingRoute = activeRoutes.value.get(assignment.assignmentId);
      if (existingRoute?.drawing) {
        return null;
      }
      console.log(`Assignment ${assignment.assignmentId} 已有路线数据，跳过绘制`);
      return existingRoute;
    }

    // 检查坐标有效性
    if (!isValidCoordinate(assignment.vehicleStartLng, assignment.vehicleStartLat) ||
        !isValidCoordinate(assignment.startLng, assignment.startLat) ||
        !isValidCoordinate(assignment.endLng, assignment.endLat)) {
      console.warn(`Assignment ${assignment.assignmentId} 坐标无效，跳过`);
      return null;
    }

    const elements = [];
    routeData = {
      assignment,
      stage1Path: [],
      stage2Path: [],
      movingMarker: null,
      startMarker: null,
      elements,
      animations: [],
      manager: animationManager,
      drawing: true,
      cleaned: false,
      cleanup: () => {
        if (routeData.cleaned) return;
        routeData.cleaned = true;

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

        if (vehicleStatusManager.value) {
          vehicleStatusManager.value.vehicleMarkers.delete(assignment.vehicleId);
          vehicleStatusManager.value.assignmentData.delete(assignment.vehicleId);
        }
        unmarkDrawnVehicleIcon(assignment.vehicleId);
      }
    };
    activeRoutes.value.set(assignment.assignmentId, routeData);

    // 规划两段路线
    const stage1Route = await computeSingleRouteWithCache(
        [assignment.vehicleStartLng, assignment.vehicleStartLat],
        [assignment.startLng, assignment.startLat],
        assignment.assignmentId + '_stage1',
        runGeneration
    );

    const stage2Route = await computeSingleRouteWithCache(
        [assignment.startLng, assignment.startLat],
        [assignment.endLng, assignment.endLat],
        assignment.assignmentId + '_stage2',
        runGeneration
    );

    if (!isActiveTransportGeneration(runGeneration)) {
      discardRouteData(assignment.assignmentId, routeData);
      return null;
    }

    if (isLifecycleCancelledRoute(stage1Route) || isLifecycleCancelledRoute(stage2Route)) {
      discardRouteData(assignment.assignmentId, routeData);
      return null;
    }

    if (!stage1Route || !stage2Route) {
      console.error(`Assignment ${assignment.assignmentId} 路线规划失败`);
      ElMessage.error(`任务 ${assignment.assignmentId} 路线规划失败`);
      discardRouteData(assignment.assignmentId, routeData);
      return null;
    }

    routeData.stage1Path = stage1Route.path;
    routeData.stage2Path = stage2Route.path;

    // 绘制第一段路线（空驶阶段）
    const stage1Poly = new AMapLib.Polyline({
      path: stage1Route.path,
      strokeColor: '#95a5a6', // 灰色表示空驶
      strokeOpacity: 0.6,
      strokeWeight: 3,
      strokeDasharray: [5, 5], // 虚线
      lineJoin: 'round',
    });
    elements.push(stage1Poly);
    stage1Poly.setMap(map);

    // 绘制第二段路线（运输阶段）
    const stage2Poly = new AMapLib.Polyline({
      path: stage2Route.path,
      strokeColor: '#3388ff', // 蓝色表示运输
      strokeOpacity: 0.8,
      strokeWeight: 4,
      lineJoin: 'round',
    });
    elements.push(stage2Poly);
    stage2Poly.setMap(map);

    // 起点标记（装货点）
    const startMarker = new AMapLib.Marker({
      position: [assignment.startLng, assignment.startLat],
      title: `装货点: ${assignment.startPOIName || '未知'}`,
      icon: new AMapLib.Icon({
        image: getPOIIcon(assignment.startPOIType),
        size: new AMapLib.Size(24, 24),
        imageSize: new AMapLib.Size(24, 24)
      })
    });
    elements.push(startMarker);
    startMarker.setMap(map);
    startMarker.on('click', () => {
      showTaskPointInfoWindow({
        role: 'start',
        actionType: 'LOAD',
        poiId: assignment.startPOIId,
        poiName: assignment.startPOIName,
        poiType: assignment.startPOIType,
        lng: assignment.startLng,
        lat: assignment.startLat,
        assignment
      });
    });

    // 终点标记（卸货点）
    const endMarker = new AMapLib.Marker({
      position: [assignment.endLng, assignment.endLat],
      title: `卸货点: ${assignment.endPOIName || '未知'}`,
      icon: new AMapLib.Icon({
        image: getPOIIcon(assignment.endPOIType),
        size: new AMapLib.Size(24, 24),
        imageSize: new AMapLib.Size(24, 24)
      })
    });
    elements.push(endMarker);
    endMarker.setMap(map);
    endMarker.on('click', () => {
      showTaskPointInfoWindow({
        role: 'end',
        actionType: 'UNLOAD',
        poiId: assignment.endPOIId,
        poiName: assignment.endPOIName,
        poiType: assignment.endPOIType,
        lng: assignment.endLng,
        lat: assignment.endLat,
        assignment
      });
    });

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
    elements.push(movingMarker);
    movingMarker.setMap(map);

    // 注册移动标记到状态管理器
    if (vehicleStatusManager.value) {
      vehicleStatusManager.value.registerVehicleMarker(
          assignment.vehicleId,
          movingMarker,
          assignment
      );
      markDrawnVehicleIcon(assignment.vehicleId, movingMarker);
    }

    // 车辆信息窗口
    movingMarker.on('click', () => {
      handleVehicleMarkerClick(assignment, movingMarker.getPosition());
    });

    routeData.movingMarker = movingMarker;
    routeData.startMarker = startMarker;

    // 添加到动画管理器
    if (animationManager) {
      animationManager.addAnimation(assignment, routeData);
    }

    routeData.drawing = false;
    console.log(`成功绘制Assignment ${assignment.assignmentId} 的两段路线`);
    return routeData;

  } catch (e) {
    if (routeData) {
      discardRouteData(assignment.assignmentId, routeData);
    }
    console.error('绘制两段路线错误', e);
    ElMessage.error(`绘制任务路线失败: ${assignment.assignmentId}`);
    return null;
  }
};

// ==================== VRP 专线：绘制多节点复杂路线 ====================
const drawMultiStageRouteForVrpAssignment = async (assignment, runGeneration = simulationGeneration.value) => {
  if (!AMapLib || !map) return null;
  if (!isActiveTransportGeneration(runGeneration)) return null;

  let routeData = null;
  try {
    if (activeRoutes.value.has(assignment.assignmentId)) {
      const existingRoute = activeRoutes.value.get(assignment.assignmentId);
      return existingRoute?.drawing ? null : existingRoute;
    }

    const nodes = assignment.nodes || [];
    if (nodes.length === 0) {
      console.warn(`VRP Assignment ${assignment.assignmentId} 没有节点数据`);
      return null;
    }

    let currentLng = assignment.vehicleStartLng;
    let currentLat = assignment.vehicleStartLat;
    const stages = [];
    const elements = [];
    routeData = {
      assignment,
      elements,
      movingMarker: null,
      stages,
      manager: animationManager,
      drawing: true,
      cleaned: false,
      cleanup: () => {
        if (routeData.cleaned) return;
        routeData.cleaned = true;

        elements.forEach(el => { try { el.setMap && el.setMap(null); } catch (_) {} });
        if (vehicleStatusManager.value) {
          vehicleStatusManager.value.vehicleMarkers.delete(assignment.vehicleId);
          vehicleStatusManager.value.assignmentData.delete(assignment.vehicleId);
        }
        unmarkDrawnVehicleIcon(assignment.vehicleId);
      }
    };
    activeRoutes.value.set(assignment.assignmentId, routeData);

    // 1. 逐段请求高德路线，把多点串联起来
    for (let i = 0; i < nodes.length; i++) {
      if (!isActiveTransportGeneration(runGeneration)) {
        discardRouteData(assignment.assignmentId, routeData);
        return null;
      }
      const targetNode = nodes[i];

      // 防并发限流缓冲
      await new Promise(resolve => setTimeout(resolve, 500));
      if (!isActiveTransportGeneration(runGeneration)) {
        discardRouteData(assignment.assignmentId, routeData);
        return null;
      }

      let path = [];
      let distance = 0;

      try {
        const routeResult = await computeSingleRouteWithCache(
            [currentLng, currentLat],
            [targetNode.lng, targetNode.lat],
            `${assignment.assignmentId}_vrp_stage_${i}`,
            runGeneration
        );
        if (isLifecycleCancelledRoute(routeResult)) {
          discardRouteData(assignment.assignmentId, routeData);
          return null;
        }
        if (routeResult && routeResult.path && routeResult.path.length > 0) {
          path = routeResult.path;
          distance = routeResult.distance;
        }
      } catch (err) {
        console.warn(`[VRP] 第 ${i} 段路线规划失败，使用直线兜底`);
      }

      // 【修复】：直线兜底，绝不瞬移
      if (!isActiveTransportGeneration(runGeneration)) {
        discardRouteData(assignment.assignmentId, routeData);
        return null;
      }

      if (path.length === 0) {
        discardRouteData(assignment.assignmentId, routeData);
        return null;
      }

      stages.push({
        stageIndex: i,
        path: path,
        distance: distance,
        nodeInfo: targetNode
      });

      // 画线：第一段(空驶接货)用灰色虚线，后面用紫色实线
      const isFirstStage = i === 0;
      const polyline = new AMapLib.Polyline({
        path: path,
        strokeColor: isFirstStage ? '#95a5a6' : '#9b59b6',
        strokeOpacity: 0.8,
        strokeWeight: isFirstStage ? 3 : 4,
        strokeDasharray: isFirstStage ? [5, 5] : [],
        lineJoin: 'round',
      });
      elements.push(polyline);
      polyline.setMap(map);

      // 画节点Marker
      let actualPoiType = targetNode.poiType;

      // 容错兜底：兼容旧的未透传 poiType 的缓存数据
      if (!actualPoiType) {
        actualPoiType = targetNode.actionType === 'LOAD' ? 'FURNITURE_FACTORY' : 'REST_AREA';
      }

      // 调用匹配引擎获取切图
      const iconType = getPOIIcon(actualPoiType);

      // 画节点Marker
      const nodeMarker = new AMapLib.Marker({
        position: [targetNode.lng, targetNode.lat],
        title: `${targetNode.actionType === 'LOAD' ? '装货' : '卸货'}: ${targetNode.poiName}`,
        icon: new AMapLib.Icon({
          image: iconType,
          // 尺寸可以根据你切图的实际情况微调，24x24 或 32x32 均可
          size: new AMapLib.Size(24,24),
          imageSize: new AMapLib.Size(24, 24)
        }),
        // offset: new AMapLib.Pixel(-16, -16) // 如果图标偏了，可以加这行调整中心锚点
      });
      elements.push(nodeMarker);
      nodeMarker.setMap(map);
      nodeMarker.on('click', () => {
        showTaskPointInfoWindow({
          ...targetNode,
          assignment
        });
      });

      currentLng = targetNode.lng;
      currentLat = targetNode.lat;
    }

    if (stages.length === 0) {
      discardRouteData(assignment.assignmentId, routeData);
      return null;
    }

    // 2. 车辆移动标记
    const movingEl = createVehicleIcon(32, 'ORDER_DRIVING', '#9b59b6');
    const movingMarker = new AMapLib.Marker({
      position: stages[0].path[0],
      content: movingEl,
      offset: new AMapLib.Pixel(-16, -16),
      title: `VRP拼单 - ${assignment.licensePlate}`,
      extData: {
        type: 'vehicle',
        vehicleId: assignment.vehicleId,
        assignmentId: assignment.assignmentId,
        licensePlate: assignment.licensePlate,
        status: 'ORDER_DRIVING'
      }
    });
    elements.push(movingMarker);
    routeData.movingMarker = movingMarker;
    movingMarker.setMap(map);

    if (vehicleStatusManager.value) {
      vehicleStatusManager.value.registerVehicleMarker(assignment.vehicleId, movingMarker, assignment);
      markDrawnVehicleIcon(assignment.vehicleId, movingMarker);
    }
    movingMarker.on('click', () => {
      handleVehicleMarkerClick(assignment, movingMarker.getPosition());
    });

    // 4. 将 VRP 动画加入你们原本的全局动画管理器！
    if (animationManager) {
      animationManager.addAnimation(assignment, routeData);
    }

    routeData.drawing = false;
    console.log(`成功绘制 VRP 拼载任务 ${assignment.assignmentId}，共 ${stages.length} 段路线`);
    return routeData;

  } catch (e) {
    if (routeData) {
      discardRouteData(assignment.assignmentId, routeData);
    }
    console.error('绘制 VRP 路线错误', e);
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
const computeSingleRouteWithCache = async (start, end, cacheKey, runGeneration = simulationGeneration.value) => {
  if (!isActiveTransportGeneration(runGeneration)) return null;
  // 检查缓存
  if (routePlanningCache.has(cacheKey)) {
    console.log(`使用缓存的路线: ${cacheKey}`);
    return routePlanningCache.get(cacheKey);
  }

  // 规划新路线
  const route = await computeSingleRoute(start, end, '0', runGeneration);

  if (route && !isLifecycleCancelledRoute(route) && isActiveTransportGeneration(runGeneration)) {
    // 缓存结果
    routePlanningCache.set(cacheKey, route);
  }

  return route;
};

const normalizeMapPosition = (position) => {
  if (!position) return null;
  if (Array.isArray(position)) {
    const lng = Number(position[0]);
    const lat = Number(position[1]);
    return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null;
  }
  if (typeof position.getLng === 'function' && typeof position.getLat === 'function') {
    return [position.getLng(), position.getLat()];
  }
  const lng = Number(position.lng ?? position.longitude);
  const lat = Number(position.lat ?? position.latitude);
  return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null;
};

const vehicleMonitorDisplayVehicles = computed(() => vehicles.filter(vehicle => {
  const iconId = getVehicleIconId(vehicle?.id);
  if (!iconId || !drawnVehicleIconIds.value.has(iconId)) {
    return false;
  }

  const marker = vehicleStatusManager.value?.vehicleMarkers?.get(vehicle.id);
  return markerHasValidPosition(marker);
}));

const normalizeDisplayId = (value) => {
  if (value === null || value === undefined) return null;
  return String(value);
};

const lookupMapByLooseId = (mapLike, id) => {
  if (!mapLike || id === null || id === undefined) return null;
  if (mapLike.has(id)) return mapLike.get(id);
  const stringId = String(id);
  if (mapLike.has(stringId)) return mapLike.get(stringId);
  const numberId = Number(id);
  if (Number.isFinite(numberId) && mapLike.has(numberId)) {
    return mapLike.get(numberId);
  }
  return null;
};

const getRouteDataForDisplayAssignment = (assignmentId) =>
    lookupMapByLooseId(activeRoutes.value, assignmentId);

const getVehicleMarkerForDisplay = (vehicleId) =>
    lookupMapByLooseId(vehicleStatusManager.value?.vehicleMarkers, vehicleId);

const visibleAssignmentIds = computed(() => {
  const ids = [];
  drawnAssignmentIds.value.forEach(assignmentId => {
    const routeData = getRouteDataForDisplayAssignment(assignmentId);
    if (!routeData || routeData.drawing || routeData.cleaned) {
      return;
    }

    const vehicleId = routeData.assignment?.vehicleId;
    if (vehicleId === null || vehicleId === undefined) {
      return;
    }

    const marker = getVehicleMarkerForDisplay(vehicleId);
    if (markerHasValidPosition(marker)) {
      ids.push(normalizeDisplayId(assignmentId));
    }
  });
  return ids;
});

const visibleAssignmentIdSet = computed(() => new Set(visibleAssignmentIds.value));
const displayTaskCount = computed(() => visibleAssignmentIds.value.length);

const displayStatusTextForVehicleStatus = (status) => {
  if (!status) return null;
  return statusMap[status]?.text || status;
};

const getFrontendAssignmentStatus = (assignment) => {
  if (!assignment) return null;
  const vehicleStatus = assignment.vehicleId
      ? vehicleStatusManager.value?.getVehicleStatus?.(assignment.vehicleId)
      : null;
  if (vehicleStatus) {
    return vehicleStatus;
  }

  const routeAssignment = getRouteDataForDisplayAssignment(assignment.assignmentId)?.assignment;
  return routeAssignment?.frontendStatus || routeAssignment?.vehicleStatus || routeAssignment?.status || null;
};

const displayMonitorAssignments = computed(() => {
  const visibleIds = visibleAssignmentIdSet.value;
  return monitorAssignments
      .filter(assignment => visibleIds.has(normalizeDisplayId(assignment.assignmentId)))
      .map(assignment => {
        const frontendStatus = getFrontendAssignmentStatus(assignment);
        if (!frontendStatus) {
          return { ...assignment };
        }

        return {
          ...assignment,
          displayVehicleStatus: frontendStatus,
          displayStatusText: displayStatusTextForVehicleStatus(frontendStatus)
        };
      });
});

const displayMonitorShipments = computed(() => {
  const displayAssignments = displayMonitorAssignments.value;
  return monitorShipments
      .map(shipment => {
        const shipmentId = normalizeDisplayId(shipment.shipmentId);
        const originalAssignmentIds = new Set((shipment.assignmentIds || []).map(normalizeDisplayId));
        const assignmentIds = [];
        const vehicleIds = [];

        displayAssignments.forEach(assignment => {
          const assignmentShipmentIds = (assignment.shipmentIds || []).map(normalizeDisplayId);
          const belongsToShipment = assignmentShipmentIds.includes(shipmentId)
              || originalAssignmentIds.has(normalizeDisplayId(assignment.assignmentId));
          if (!belongsToShipment) {
            return;
          }

          if (!assignmentIds.includes(assignment.assignmentId)) {
            assignmentIds.push(assignment.assignmentId);
          }
          if (assignment.vehicleId !== null
              && assignment.vehicleId !== undefined
              && !vehicleIds.includes(assignment.vehicleId)) {
            vehicleIds.push(assignment.vehicleId);
          }
        });

        return {
          ...shipment,
          assignmentIds,
          vehicleIds
        };
      })
      .filter(shipment => shipment.assignmentIds.length > 0);
});

const getAssignmentFallbackPosition = (assignment) => {
  if (!assignment) return null;

  return normalizeMapPosition([assignment.currentLng, assignment.currentLat]) ||
      normalizeMapPosition([assignment.vehicleStartLng, assignment.vehicleStartLat]) ||
      normalizeMapPosition([assignment.startLng, assignment.startLat]) ||
      normalizeMapPosition([assignment.endLng, assignment.endLat]);
};

const buildAssignmentFromVehicle = (vehicle) => ({
  vehicleId: vehicle.id,
  licensePlate: vehicle.licensePlate,
  vehicleStatus: vehicle.status,
  routeName: vehicle.currentAssignment,
  goodsName: vehicle.goodsInfo,
  quantity: vehicle.quantity,
  startPOIName: vehicle.startPOI,
  endPOIName: vehicle.endPOI,
  currentLoad: vehicle.currentLoad,
  maxLoadCapacity: vehicle.maxLoadCapacity,
  currentVolume: vehicle.currentVolume,
  maxVolumeCapacity: vehicle.maxVolumeCapacity,
  actionDescription: vehicle.actionDescription,
  vrpProgress: vehicle.vrpProgress
});

const findMonitorAssignment = (assignmentId) =>
    monitorAssignments.find(item => item.assignmentId === assignmentId);

const findDisplayMonitorAssignment = (assignmentId) =>
    displayMonitorAssignments.value.find(item =>
        normalizeDisplayId(item.assignmentId) === normalizeDisplayId(assignmentId));

const buildAssignmentFromMonitorAssignment = (assignment) => ({
  assignmentId: assignment.assignmentId,
  vehicleId: assignment.vehicleId,
  licensePlate: assignment.licensePlate,
  vehicleStatus: assignment.displayVehicleStatus || assignment.vehicleStatus,
  routeName: assignment.routeName,
  goodsName: assignment.goodsName,
  quantity: assignment.quantity,
  startPOIName: assignment.startPOIName,
  endPOIName: assignment.endPOIName,
  startLng: assignment.startLng,
  startLat: assignment.startLat,
  endLng: assignment.endLng,
  endLat: assignment.endLat,
  currentLoad: assignment.currentLoad,
  maxLoadCapacity: assignment.maxLoadCapacity,
  currentVolume: assignment.currentVolume,
  maxVolumeCapacity: assignment.maxVolumeCapacity
});

const centerMapOnVehicle = (position) => {
  const normalized = normalizeMapPosition(position);
  if (!normalized || !map) return;

  const currentZoom = typeof map.getZoom === 'function' ? map.getZoom() : null;
  if (typeof map.setZoomAndCenter === 'function' && (!currentZoom || currentZoom < 12)) {
    map.setZoomAndCenter(12, normalized);
    return;
  }

  if (typeof map.setCenter === 'function') {
    map.setCenter(normalized);
  }
};

const focusAssignmentFromPanel = async (assignment) => {
  if (!assignment?.assignmentId) return;

  await openMonitorPanel('assignments');
  highlightedAssignmentId.value = assignment.assignmentId;

  const infoAssignment = buildAssignmentFromMonitorAssignment(assignment);
  const marker = assignment.vehicleId
      ? vehicleStatusManager.value?.vehicleMarkers?.get(assignment.vehicleId)
      : null;
  const markerPosition = normalizeMapPosition(marker?.getPosition?.());
  const position = markerPosition || getAssignmentFallbackPosition(infoAssignment);

  if (!position) {
    ElMessage.warning('当前任务暂无可定位的地图位置');
    return;
  }

  centerMapOnVehicle(position);

  try {
    const vehicleDetail = assignment.vehicleId ? await getVehicleDetail(assignment.vehicleId) : null;
    showVehicleInfoWindowFromMarker(infoAssignment, vehicleDetail, position);
  } catch (error) {
    console.error('获取任务车辆信息失败:', error);
    showVehicleInfoWindowFromMarker(infoAssignment, null, position);
  }
};

const focusShipmentFromPanel = async (shipment) => {
  if (!shipment?.shipmentId) return;

  await openMonitorPanel('shipments');
  highlightedShipmentId.value = shipment.shipmentId;

  const assignmentIds = shipment.assignmentIds || [];
  const vehicleIds = shipment.vehicleIds || [];
  const firstAssignment = assignmentIds.length > 0 ? findDisplayMonitorAssignment(assignmentIds[0]) : null;

  if (firstAssignment) {
    highlightedAssignmentId.value = firstAssignment.assignmentId;
  }

  if (vehicleIds.length === 1) {
    const vehicle = vehicles.find(item => item.id === vehicleIds[0]);
    if (vehicle) {
      await focusVehicleFromPanel(vehicle);
      highlightedShipmentId.value = shipment.shipmentId;
      return;
    }
  }

  if (firstAssignment) {
    await focusAssignmentFromPanel(firstAssignment);
    highlightedShipmentId.value = shipment.shipmentId;
    return;
  }

  ElMessage.info('当前运单暂无可定位的关联任务或车辆');
};

const focusVehicleFromPanel = async (vehicle) => {
  if (!vehicle?.id) return;

  await openMonitorPanel('vehicles');
  scrollToVehicle(vehicle.id);

  const marker = vehicleStatusManager.value?.vehicleMarkers?.get(vehicle.id);
  const assignment = vehicleStatusManager.value?.assignmentData?.get(vehicle.id) ||
      buildAssignmentFromVehicle(vehicle);
  const markerPosition = normalizeMapPosition(marker?.getPosition?.());
  const fallbackPosition = getAssignmentFallbackPosition(assignment);
  const position = markerPosition || fallbackPosition;

  if (!position) {
    ElMessage.warning('当前车辆暂无可定位的地图位置');
    return;
  }

  centerMapOnVehicle(position);
  await handleVehicleMarkerClick(assignment, position);
};

// 处理车辆标记点击事件
const handleVehicleMarkerClick = async (assignment, positionOverride = null) => {
  console.log('点击车辆标记:', assignment);
  console.log('尝试滚动到车辆ID:', assignment.vehicleId);

  await openMonitorPanel('vehicles');

  // 滚动到车辆监控面板中的对应车辆
  if (assignment.vehicleId) {
    scrollToVehicle(assignment.vehicleId);
  }

  centerMapOnVehicle(positionOverride || getAssignmentFallbackPosition(assignment));

  try {
    // 获取车辆详细信息
    const vehicleDetail = await getVehicleDetail(assignment.vehicleId);

    // 显示车辆信息窗口
    showVehicleInfoWindowFromMarker(assignment, vehicleDetail, positionOverride);
  } catch (error) {
    console.error('获取车辆信息失败:', error);
    // 显示基本信息
    showVehicleInfoWindowFromMarker(assignment, null, positionOverride);
  }
};

// 从标记点击显示车辆信息窗口
const showVehicleInfoWindowFromMarker = (assignment, vehicleDetail, positionOverride = null) => {
  if (!map) return;

  // 获取车辆当前状态（从状态管理器）
  const currentStatus = vehicleStatusManager.value?.getVehicleStatus(assignment.vehicleId)
      || assignment.vehicleStatus
      || 'ORDER_DRIVING';

  const statusText = statusMap[currentStatus]?.text || currentStatus;
  const statusColor = statusMap[currentStatus]?.color || '#ccc';

  // 获取车辆详细信息（从状态管理器）
  const vehicleInfo = vehicleStatusManager.value?.getVehicleInfo(assignment.vehicleId)
      || assignment;

  // 构建信息窗口内容
  let content = `
    <div style="padding: 12px; min-width: 320px; color: #000;">
      <div style="display: flex; align-items: center; margin-bottom: 10px;">
        <div style="width: 32px; height: 32px; border-radius: 50%; background-color: ${statusColor}; display: flex; align-items: center; justify-content: center; margin-right: 10px; color: #fff; font-size: 18px;">
          ${currentStatus === 'TRANSPORT_DRIVING' ? '🚚' :
      currentStatus === 'ORDER_DRIVING' ? '🚗' :
          currentStatus === 'LOADING' ? '⏳' :
              currentStatus === 'UNLOADING' ? '📦' : '🚙'}
        </div>
        <div>
          <h3 style="margin: 0; color: #000; font-size: 16px;">${assignment.licensePlate || '未知车辆'}</h3>
          <p style="margin: 2px 0 0 0; color: #606266; font-size: 12px;">车辆ID: ${assignment.vehicleId}</p>
        </div>
      </div>
  `;

  // 状态信息
  content += `
    <div style="margin-bottom: 12px;">
      <div style="display: flex; align-items: center; margin-bottom: 4px;">
        <div style="width: 8px; height: 8px; border-radius: 50%; background-color: ${statusColor}; margin-right: 6px;"></div>
        <strong>状态:</strong> ${statusText}
      </div>
      ${vehicleInfo.actionDescription ? `<p style="margin: 4px 0; color: #000;"><strong>当前动作:</strong> ${vehicleInfo.actionDescription}</p>` : ''}
      <p style="margin: 4px 0; color: #000;"><strong>任务状态:</strong> ${assignment.status || 'ASSIGNED'}</p>
    </div>
  `;

  // 任务信息
  content += `
    <div style="margin-bottom: 12px; padding: 8px; background-color: #f8f9fa; border-radius: 4px;">
      <p style="margin: 4px 0; color: #000; font-weight: bold;">运输任务详情</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>Assignment ID:</strong> ${assignment.assignmentId}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>路线:</strong> ${assignment.routeName || '未命名路线'}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>装货点:</strong> ${assignment.startPOIName || '未知'}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>卸货点:</strong> ${assignment.endPOIName || '未知'}</p>
      <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>货物:</strong> ${assignment.goodsName || '未知'} (${assignment.quantity || 0}件)</p>
    </div>
  `;

  if (assignment.vrp === true && Array.isArray(assignment.nodes) && assignment.nodes.length > 0) {
    const progress = vehicleInfo.vrpProgress || assignment.vrpProgress || {};
    const currentStageIndex = Number.isFinite(Number(progress.currentStageIndex))
        ? Number(progress.currentStageIndex)
        : 0;
    const totalNodes = assignment.nodes.length;
    const nextNode = progress.nextNode || assignment.nodes[Math.min(currentStageIndex, totalNodes - 1)] || null;
    const nodeRows = assignment.nodes.map((node, index) => {
      const isDone = index < currentStageIndex;
      const isCurrent = index === currentStageIndex;
      const rowColor = isCurrent ? '#ecf5ff' : isDone ? '#f0f9eb' : '#fff';
      const stateText = isCurrent ? '当前' : isDone ? '已完成' : '待执行';
      const actionText = node.actionType === 'LOAD' ? '装' : '卸';
      return `
        <div style="display: grid; grid-template-columns: 36px 42px 1fr 72px 72px; gap: 4px; align-items: center; padding: 4px; background: ${rowColor}; border-bottom: 1px solid #ebeef5; font-size: 11px;">
          <span>#${index + 1}</span>
          <span>${actionText}/${stateText}</span>
          <span title="${node.poiName || ''}">${node.poiName || '未知'}</span>
          <span>${formatDelta(node.weightDelta, 't')}</span>
          <span>${formatDelta(node.volumeDelta, 'm³')}</span>
        </div>
      `;
    }).join('');

    content += `
      <div style="margin-bottom: 12px; padding: 8px; background-color: #fff7e6; border-radius: 4px;">
        <p style="margin: 4px 0; color: #000; font-weight: bold;">多运单进度</p>
        <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>当前节点:</strong> ${Math.min(currentStageIndex + 1, totalNodes)} / ${totalNodes}</p>
        <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>车上数量:</strong> ${progress.carriedCount || 0}</p>
        <p style="margin: 2px 0; color: #606266; font-size: 12px;"><strong>下一节点:</strong> ${nextNode ? `${nextNode.actionType === 'LOAD' ? '装货' : '卸货'} - ${nextNode.poiName || '未知'}` : '无'}</p>
        <div style="margin-top: 6px; border: 1px solid #ebeef5; border-radius: 4px; overflow: hidden;">
          ${nodeRows}
        </div>
      </div>
    `;
  }

  // 载重信息
  if (vehicleInfo.currentLoad !== undefined && vehicleInfo.maxLoadCapacity !== undefined) {
    const loadPercentage = vehicleInfo.maxLoadCapacity > 0 ?
        Math.min(100, (vehicleInfo.currentLoad / vehicleInfo.maxLoadCapacity) * 100) : 0;

    const loadColor = loadPercentage >= 70 ? '#67c23a' :
        loadPercentage >= 30 ? '#e6a23c' : '#f56c6c';

    content += `
      <div style="margin-bottom: 10px;">
        <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
          <span><strong>载重:</strong> ${vehicleInfo.currentLoad.toFixed(1)} / ${vehicleInfo.maxLoadCapacity.toFixed(1)} 吨</span>
          <span style="color: ${loadColor}; font-weight: bold;">${loadPercentage.toFixed(1)}%</span>
        </div>
        <div style="height: 6px; background-color: #ebeef5; border-radius: 3px; overflow: hidden;">
          <div style="width: ${loadPercentage}%; height: 100%; background-color: ${loadColor};"></div>
        </div>
      </div>
    `;
  }

  // 载容信息
  if (vehicleInfo.currentVolume !== undefined && vehicleInfo.maxVolumeCapacity !== undefined) {
    const volumePercentage = vehicleInfo.maxVolumeCapacity > 0 ?
        Math.min(100, (vehicleInfo.currentVolume / vehicleInfo.maxVolumeCapacity) * 100) : 0;

    const volumeColor = volumePercentage >= 70 ? '#409eff' :
        volumePercentage >= 30 ? '#e6a23c' : '#f56c6c';

    content += `
      <div style="margin-bottom: 10px;">
        <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
          <span><strong>载容:</strong> ${vehicleInfo.currentVolume.toFixed(1)} / ${vehicleInfo.maxVolumeCapacity.toFixed(1)} m³</span>
          <span style="color: ${volumeColor}; font-weight: bold;">${volumePercentage.toFixed(1)}%</span>
        </div>
        <div style="height: 6px; background-color: #ebeef5; border-radius: 3px; overflow: hidden;">
          <div style="width: ${volumePercentage}%; height: 100%; background-color: ${volumeColor};"></div>
        </div>
      </div>
    `;
  }

  // 车辆详细信息
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
  const normalizedOverride = normalizeMapPosition(positionOverride);
  const position = normalizedOverride ||
      (assignment.vehicleStartLng && assignment.vehicleStartLat ?
      [assignment.vehicleStartLng, assignment.vehicleStartLat] :
      [assignment.startLng, assignment.startLat]);

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
      let cleanedAssignments = 0;
      let skippedRunningAssignments = 0;
      let compensatedVisualAcks = 0;
      for (const assignmentId of assignmentIdsToCleanup) {
        const animation = animationManager?.animations?.get(assignmentId);
        if (animation && animation.isCompleted !== true) {
          skippedRunningAssignments += 1;
          continue;
        }
        if (shouldCompensateExperimentVisualAck(assignmentId)) {
          await acknowledgeExperimentVisualArrival(assignmentId);
          compensatedVisualAcks += 1;
        }
        clearRouteByAssignmentId(assignmentId);
        cleanedAssignments += 1;
      }
      if (skippedRunningAssignments > 0) {
        console.log(`[AssignmentCleanup] skipped ${skippedRunningAssignments} running assignments`);
      }
      if (compensatedVisualAcks > 0) {
        console.log(`[AssignmentCleanup] compensated ${compensatedVisualAcks} experiment visual arrivals`);
      }
      console.log(`清理了 ${cleanedAssignments} 个已完成的Assignment`);
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
    stats.running = vehicleMonitorDisplayVehicles.value.length;
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
const lifecycleCancelledRoute = { lifecycleCancelled: true };

const routePlanningLifecyclePatterns = [
  'gaode route planning queue is paused by simulation lifecycle',
  'discarded by simulation lifecycle',
  'discarded by simulation pause',
  'discarded by simulation reset',
  'interrupted by simulation pause',
  'task is stale',
  'route planning skipped during simulation reset'
];

const routePlanningMessageOf = (errorOrMessage) => {
  if (!errorOrMessage) {
    return '';
  }
  if (typeof errorOrMessage === 'string') {
    return errorOrMessage;
  }
  return errorOrMessage?.response?.data?.message
      || errorOrMessage?.response?.data?.error
      || errorOrMessage?.message
      || '';
};

const isRoutePlanningLifecycleCancellation = (errorOrMessage) => {
  if (errorOrMessage?.code === 'ERR_CANCELED' || errorOrMessage?.name === 'CanceledError') {
    return true;
  }
  const message = routePlanningMessageOf(errorOrMessage).toLowerCase();
  return routePlanningLifecyclePatterns.some(pattern => message.includes(pattern));
};

const isLifecycleCancelledRoute = (route) => {
  return Boolean(route?.lifecycleCancelled);
};

const computeSingleRoute = async (start, end, strategy = '0', runGeneration = simulationGeneration.value) => {
  if (!isActiveTransportGeneration(runGeneration)) return null;
  try {
    const params = {
      startLon: String(start[0]),
      startLat: String(start[1]),
      endLon: String(end[0]),
      endLat: String(end[1]),
      strategy: strategy
    };

    const res = await request.get(
        '/api/route-planning/gaode/plan-by-coordinates',
        { params, signal: routePlanningAbortController?.signal }
    );

    if (!isActiveTransportGeneration(runGeneration)) return null;

    const response = res.data;

    if (!response.success) {
      if (isRoutePlanningLifecycleCancellation(response.message)) {
        console.info('[RoutePlanning] lifecycle cancellation:', response.message);
        return lifecycleCancelledRoute;
      }
      console.error(`路线规划失败:`, response.message);
      ElMessage.error('路线规划失败');
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

    if (fullPath.length === 0) {
      console.error(`规划成功但未获取到路线坐标！请检查后端是否传了 show_fields=polyline 参数。`);
      return null;
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
    if (isRoutePlanningLifecycleCancellation(error)) {
      console.info('[RoutePlanning] lifecycle cancellation:', routePlanningMessageOf(error));
      return lifecycleCancelledRoute;
    }
    console.error('路线规划出错:', error);
    ElMessage.error('路线规划出错');
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
    stats.running = vehicleMonitorDisplayVehicles.value.length;
  });
};

onMounted(() => {
  fetchCurrentExperimentScenario().catch(error => {
    console.error('读取当前实验场景失败:', error);
  });
  window._AMapSecurityConfig = {
    securityJsCode: "9df38c185c95fa1dbf78a1082b64f668",
  };
  AMapLoader.load({
    key: "e0ea478e44e417b4c2fc9a54126debaa",
    version: "2.0",
    plugins: ["AMap.Scale", "AMap.Driving", "AMap.Marker", "AMap.Polyline", "AMap.InfoWindow", "AMap.MoveAnimation"],
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
  if (isExperimentRunActive.value) {
    try {
      const abortUrl = 'http://localhost:8080/api/simulation/experiments/dispatch-comparison/abort';
      if (navigator?.sendBeacon) {
        navigator.sendBeacon(abortUrl, new Blob([], { type: 'application/json' }));
      } else {
        request.post('/api/simulation/experiments/dispatch-comparison/abort').catch(() => {});
      }
    } catch (error) {
      console.warn('实验运行卸载中止请求发送失败:', error);
    }
  }
  invalidateSimulationGeneration();
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
  drawnAssignmentIds.value.clear();
  drawnVehicleIconIds.value = new Set();
  syncRegisteredVehicleStats();
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

/*运单*/
.shipment-control {
  margin-bottom: 16px;
}

.task-sidebar {
  border: 1px solid #ccc;
  padding: 8px;
  width: 200px;
  background-color: #fff; /* 确保背景是白色的 */
  color: #303133;         /* 强制设置文字为深灰色，解决隐形问题 */
  border-radius: 4px;     /* 稍微加个圆角好看点（可选）*/
}

.task-sidebar ul {
  padding-left: 20px; /* 调整列表缩进 */
  margin: 0;
}

.task-sidebar li {
  margin-bottom: 6px;
  font-size: 13px;    /* 调整一下字号更协调 */
  word-break: break-all; /* 防止运单号太长撑破容器 */
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
  position: relative;
}

.el-main {
  padding: 0;
  min-width: 0;
  overflow: hidden;
  position: relative;
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
.cost-info {
    border-bottom: 1px solid #e0e0e0;
    font-size: 12px;
    margin-bottom: 8px;
        display: flex;
    justify-content: space-between;
  transition: background-color 0.2s;
}
.cost-info:hover {
  background-color: #f5f5f5;   /* 深一些的颜色 */
}

/* ==================== 运单生成美化 ==================== */
.shipment-control-card {
  margin-bottom: 10px;
}

.shipment-control {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.shipment-control .control-label {
  color: #606266; /* 强制指定深灰色，避免与背景混合 */
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.custom-input {
  width: 70px;
  height: 26px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 0 8px;
  color: #303133;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.custom-input:focus {
  border-color: #409eff;
}

.task-sidebar {
  border: 1px solid #ebeef5;
  padding: 8px 12px;
  background-color: #fafafa;
  border-radius: 6px;
  max-height: 120px;
  overflow-y: auto;
  width: 100%;
  box-sizing: border-box;
}

.task-sidebar ul {
  padding-left: 0;
  margin: 0;
  list-style: none;
}

.task-sidebar li {
  margin-bottom: 6px;
  font-size: 12px;
  color: #606266;
  display: flex;
  justify-content: space-between;
  border-bottom: 1px dashed #e4e7ed;
  padding-bottom: 4px;
}

.task-sidebar li:last-child {
  margin-bottom: 0;
  border-bottom: none;
  padding-bottom: 0;
}

.shipment-no {
  font-family: monospace;
  color: #909399;
}

.shipment-status {
  color: #67c23a;
  font-weight: 500;
}

/* ==================== 右侧成本监控面板美化 ==================== */
.right-side-panel {
  flex: 0 0 340px;
  width: 340px;
  min-width: 340px;
  height: calc(100vh - 60px);
  position: fixed;
  top: 60px;
  right: 0;
  bottom: 0;
  background-color: #f7f8fa; /* 覆盖黑底色，统一侧边栏背景 */
  border-left: 1px solid #e6e6e6;
  display: flex;
  flex-direction: column;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.05); /* 左侧加一点阴影区分层次 */
  z-index: 3000;
}

.monitor-panel {
  padding: 0;
}

.monitor-header {
  height: 48px;
  padding: 0 14px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #303133;
  font-weight: 600;
  background: #fff;
  flex-shrink: 0;
}

.monitor-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.monitor-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  background: #fff;
}

.monitor-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.monitor-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.monitor-tab-body,
.vehicle-monitor-list {
  height: 100%;
  overflow-y: auto;
  padding: 12px;
  box-sizing: border-box;
}

.vehicle-monitor-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vehicle-monitor-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
  transition: background-color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.vehicle-monitor-item:hover {
  background: #f8fbff;
  border-color: #c6e2ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}

.vehicle-monitor-main {
  min-width: 0;
  flex: 1;
}

.vehicle-monitor-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.vehicle-status-text {
  color: #606266;
  font-size: 12px;
  white-space: nowrap;
}

.vehicle-monitor-desc {
  color: #606266;
  font-size: 12px;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vehicle-mini-metrics {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 5px 8px;
  align-items: center;
  margin-top: 8px;
  color: #909399;
  font-size: 11px;
}

.mini-progress {
  min-width: 0;
  height: 5px;
}

.monitor-empty {
  margin: 16px 0;
  padding: 18px 12px;
  background: #fff;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  color: #909399;
  font-size: 14px;
  font-style: italic;
  text-align: center;
}

.transport-monitor-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.transport-monitor-item {
  padding: 10px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.transport-monitor-item:hover {
  background: #f8fbff;
  border-color: #c6e2ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}

.transport-item-highlighted {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12);
}

.transport-item-title,
.transport-meta-row,
.transport-progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.transport-main-text {
  min-width: 0;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transport-status {
  color: #606266;
  font-size: 12px;
  white-space: nowrap;
}

.transport-item-desc {
  margin-top: 5px;
  color: #606266;
  font-size: 12px;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transport-progress-row {
  margin-top: 8px;
  color: #909399;
  font-size: 11px;
}

.transport-meta-row {
  margin-top: 8px;
  color: #909399;
  font-size: 11px;
}

.shipment-progress-fill {
  background-color: #67c23a;
}

.cost-card {
  background: transparent;
}

.cost-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dispatch-effect-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dispatch-score-card {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-left: 4px solid #909399;
  border-radius: 8px;
  padding: 14px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
}

.dispatch-score-card--good {
  border-left-color: #67c23a;
}

.dispatch-score-card--warning {
  border-left-color: #e6a23c;
}

.dispatch-score-card--danger {
  border-left-color: #f56c6c;
}

.dispatch-score-card--pending {
  border-left-color: #909399;
}

.dispatch-score-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.dispatch-score-title {
  color: #303133;
  font-size: 14px;
  font-weight: 700;
}

.dispatch-score-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.dispatch-score-value {
  margin-top: 14px;
  color: #303133;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.dispatch-score-status {
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
  font-weight: 600;
}

.dispatch-score-note {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.cost-section-title {
  color: #303133;
  font-size: 13px;
  font-weight: 700;
}

.normalized-breakdown {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.normalized-row {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
}

.normalized-row-head,
.normalized-row-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.normalized-name {
  color: #303133;
  font-size: 13px;
  font-weight: 700;
}

.normalized-value {
  color: #303133;
  font-size: 13px;
  font-weight: 700;
  max-width: 110px;
  overflow-wrap: anywhere;
  text-align: right;
}

.normalized-bar-track {
  position: relative;
  height: 10px;
  margin: 10px 0 8px;
  overflow: hidden;
  background: #f0f2f5;
  border-radius: 6px;
}

.normalized-baseline-mark {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 66.6667%;
  width: 2px;
  background: #303133;
  opacity: 0.35;
  z-index: 2;
}

.normalized-bar-fill {
  position: relative;
  height: 100%;
  min-width: 0;
  border-radius: 6px;
  z-index: 1;
}

.normalized-bar-fill--good {
  background: #67c23a;
}

.normalized-bar-fill--warning {
  background: #e6a23c;
}

.normalized-bar-fill--danger {
  background: #f56c6c;
}

.normalized-bar-fill--pending {
  background: #dcdfe6;
}

.normalized-row-foot {
  color: #909399;
  font-size: 12px;
}

.normalized-empty {
  padding: 10px 12px;
  color: #909399;
  font-size: 12px;
  text-align: center;
  background: #f5f7fa;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
}

.cost-reference-list {
  gap: 10px;
}

/* 单个成本卡片设计 */
.cost-item {
  background-color: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
  transition: all 0.3s ease;
  position: relative; /* 为伪元素绝对定位提供参照 */
  overflow: hidden;   /* 关键：防止内部元素超出圆角边界 */
}

.cost-item:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  transform: translateY(-2px);
  border-color: #dcdfe6;
}

.cost-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.cost-footer-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.cost-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.cost-value {
  font-size: 18px;
  font-weight: 700;
  color: #409eff; /* 蓝色数值 */
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', sans-serif;
  max-width: 140px;
  overflow-wrap: anywhere;
  text-align: right;
}

.cost-desc {
  font-size: 12px;
  color: #909399; /* 浅灰色副标题 */
  display: block;
}

.cost-card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.runtime-dashboard-shell {
  position: fixed;
  top: 60px;
  left: 320px;
  right: 0;
  bottom: 0;
  z-index: 2400;
  background: #f5f7fa;
  overflow: hidden;
}

.runtime-dashboard {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  padding: 18px;
  box-sizing: border-box;
  color: #303133;
}

.dashboard-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(20, 36, 64, 0.04);
}

.dashboard-topbar h2 {
  margin: 4px 0 0;
  color: #303133;
  font-size: 20px;
  line-height: 1.2;
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(360px, 0.9fr);
  gap: 16px;
  padding: 18px;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-left: 5px solid #909399;
  border-radius: 8px;
  box-shadow: 0 4px 14px rgba(20, 36, 64, 0.06);
}

.dashboard-hero--good {
  border-left-color: #67c23a;
}

.dashboard-hero--warning {
  border-left-color: #e6a23c;
}

.dashboard-hero--danger {
  border-left-color: #f56c6c;
}

.dashboard-hero-main {
  min-width: 0;
}

.dashboard-eyebrow {
  color: #909399;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.dashboard-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.dashboard-title {
  font-size: 20px;
  font-weight: 800;
}

.dashboard-status {
  padding: 3px 8px;
  border-radius: 4px;
  background: #f0f2f5;
  color: #606266;
  font-size: 12px;
  font-weight: 700;
}

.dashboard-score {
  margin-top: 12px;
  font-size: 44px;
  line-height: 1;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.dashboard-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
  color: #606266;
  font-size: 13px;
}

.dashboard-kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.dashboard-alert {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #f3d19e;
  border-radius: 8px;
  background: #fdf6ec;
  color: #b88230;
  font-size: 13px;
}

.experiment-start-lock {
  margin-top: 8px;
  color: #b88230;
  font-size: 12px;
  line-height: 1.5;
}

.experiment-prep-panel {
  margin-top: 14px;
}

.experiment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.experiment-alert {
  margin-bottom: 12px;
}

.experiment-control-grid {
  display: grid;
  grid-template-columns: minmax(160px, 0.7fr) minmax(140px, 0.5fr) minmax(180px, 0.8fr);
  gap: 18px;
  align-items: end;
}

.experiment-control-block {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.experiment-control-block span,
.experiment-note {
  color: #909399;
  font-size: 12px;
}

.experiment-control-block strong {
  color: #303133;
  font-size: 18px;
}

.experiment-note {
  margin-top: 10px;
}

.experiment-placement-preview,
.experiment-placement-list {
  margin-top: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.experiment-placement-preview {
  display: grid;
  grid-template-columns: minmax(280px, 1.4fr) repeat(2, minmax(150px, 0.6fr));
  gap: 12px;
  padding: 12px;
  background: #fafafa;
}

.experiment-placement-card {
  min-width: 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.experiment-placement-card span,
.experiment-placement-card small {
  display: block;
  color: #909399;
  font-size: 12px;
}

.experiment-placement-card strong {
  display: block;
  margin-top: 8px;
  color: #303133;
  font-size: 18px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.experiment-placement-card small {
  margin-top: 6px;
  line-height: 1.4;
}

.experiment-run-panel {
  margin-top: 16px;
}

.experiment-run-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.experiment-run-status-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}

.experiment-run-status-card span,
.experiment-run-status-card small {
  display: block;
  color: #909399;
  font-size: 12px;
}

.experiment-run-status-card strong {
  display: block;
  margin: 8px 0 4px;
  color: #303133;
  font-size: 20px;
}

.experiment-run-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.experiment-result-table {
  margin-top: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.experiment-result-head,
.experiment-result-row {
  display: grid;
  grid-template-columns: minmax(90px, 0.7fr) repeat(5, minmax(100px, 1fr));
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  font-size: 13px;
}

.experiment-result-head {
  background: #f5f7fa;
  color: #606266;
  font-weight: 700;
}

.experiment-result-row {
  border-top: 1px solid #ebeef5;
  color: #303133;
}

.experiment-optimization-panel {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #dbe7f5;
  border-radius: 8px;
  background: #fbfdff;
}

.experiment-optimization-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.experiment-optimization-head h4 {
  margin: 0;
  color: #303133;
  font-size: 15px;
}

.experiment-optimization-head p {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}

.experiment-optimization-badge {
  flex: 0 0 auto;
  padding: 5px 10px;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 700;
  background: #f4f4f5;
  color: #606266;
}

.experiment-optimization-badge--good {
  background: #ecf8f1;
  color: #1f8f4d;
}

.experiment-optimization-badge--danger {
  background: #fef0f0;
  color: #c45656;
}

.experiment-optimization-badge--neutral {
  background: #fdf6ec;
  color: #b88230;
}

.experiment-optimization-badge--pending {
  background: #f4f4f5;
  color: #909399;
}

.experiment-optimization-content {
  margin-top: 12px;
}

.experiment-optimization-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.experiment-optimization-card {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #ffffff;
}

.experiment-optimization-card span,
.experiment-optimization-card small {
  display: block;
  color: #909399;
  font-size: 12px;
}

.experiment-optimization-card strong {
  display: block;
  margin: 6px 0 4px;
  color: #303133;
  font-size: 22px;
  line-height: 1.2;
}

.experiment-optimization-table {
  margin-top: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
}

.experiment-optimization-table-head,
.experiment-optimization-table-row {
  display: grid;
  gap: 10px;
  align-items: center;
  padding: 9px 10px;
  font-size: 13px;
}

.experiment-optimization-table--dimension .experiment-optimization-table-head,
.experiment-optimization-table--dimension .experiment-optimization-table-row {
  grid-template-columns: minmax(80px, 0.6fr) repeat(6, minmax(90px, 1fr));
}

.experiment-optimization-table--efficiency .experiment-optimization-table-head,
.experiment-optimization-table--efficiency .experiment-optimization-table-row {
  grid-template-columns: minmax(120px, 1fr) repeat(3, minmax(90px, 1fr));
}

.experiment-optimization-table-head {
  background: #f5f7fa;
  color: #606266;
  font-weight: 700;
}

.experiment-optimization-table-row {
  border-top: 1px solid #ebeef5;
  color: #303133;
}

.experiment-optimization-table-row .is-good {
  color: #1f8f4d;
  font-weight: 700;
}

.experiment-optimization-table-row .is-danger {
  color: #c45656;
  font-weight: 700;
}

.experiment-optimization-chart {
  margin-top: 12px;
  height: 260px;
}

.experiment-shipment-list {
  margin-top: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.experiment-table-head,
.experiment-table-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(90px, 0.5fr) minmax(220px, 1.4fr);
  gap: 10px;
  align-items: center;
  padding: 9px 10px;
}

.experiment-table-head {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f5f7fa;
  color: #606266;
  font-size: 12px;
  font-weight: 700;
}

.experiment-table-row {
  border-top: 1px solid #ebeef5;
  color: #303133;
  font-size: 13px;
}

.experiment-table-row .el-select {
  width: 100%;
}

.experiment-summary {
  margin-top: 14px;
}

.experiment-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 10px;
}

.experiment-table-head--shipment,
.experiment-table-row--shipment {
  grid-template-columns: minmax(90px, 0.5fr) minmax(220px, 1.6fr) minmax(130px, 0.8fr) minmax(70px, 0.4fr) minmax(90px, 0.5fr);
}

.experiment-table-head--placement,
.experiment-table-row--placement {
  grid-template-columns: minmax(120px, 0.8fr) minmax(220px, 1.4fr) minmax(110px, 0.6fr);
}

.dashboard-kpi,
.dashboard-panel,
.dashboard-focus-card,
.workload-metric {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.dashboard-kpi {
  padding: 12px;
}

.dashboard-kpi span,
.dashboard-kpi small {
  display: block;
  color: #909399;
  font-size: 12px;
}

.dashboard-kpi strong {
  display: block;
  margin: 6px 0 2px;
  color: #303133;
  font-size: 22px;
  overflow-wrap: anywhere;
}

.dashboard-grid {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.dashboard-grid--charts {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard-grid--detail {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-grid--formula {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dashboard-grid--top {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-panel--wide {
  grid-column: span 2;
}

.dashboard-panel {
  min-width: 0;
  padding: 14px;
  box-shadow: 0 2px 10px rgba(20, 36, 64, 0.04);
}

.dashboard-panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.dashboard-panel-head h3 {
  margin: 0;
  color: #303133;
  font-size: 15px;
  font-weight: 800;
}

.dashboard-panel-head p {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}

.dashboard-chart {
  width: 100%;
  height: 280px;
}

.dashboard-chart--pie {
  height: 240px;
}

.dashboard-section-title {
  margin-top: 18px;
  color: #303133;
  font-size: 16px;
  font-weight: 800;
}

.dashboard-metric-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dashboard-metric-list--compact {
  margin-top: 8px;
}

.dashboard-metric-row,
.dashboard-top-row,
.dashboard-cost-table-head,
.dashboard-cost-table-row {
  display: grid;
  align-items: center;
  gap: 8px;
}

.dashboard-metric-row {
  grid-template-columns: minmax(0, 1fr) auto;
  padding-bottom: 7px;
  border-bottom: 1px dashed #e4e7ed;
  color: #606266;
  font-size: 12px;
}

.dashboard-metric-row:last-child {
  border-bottom: none;
}

.dashboard-metric-row strong {
  max-width: 180px;
  color: #303133;
  overflow-wrap: anywhere;
  text-align: right;
}

.dashboard-cost-table {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dashboard-cost-table-head,
.dashboard-cost-table-row {
  grid-template-columns: 1.3fr repeat(4, minmax(72px, 1fr));
}

.dashboard-cost-table-head {
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 6px;
  color: #909399;
  font-size: 12px;
  font-weight: 700;
}

.dashboard-cost-table-row {
  padding: 8px 10px;
  border-bottom: 1px solid #f0f2f5;
  font-size: 12px;
}

.dashboard-cost-table-row strong {
  color: #303133;
}

.workload-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.workload-metric {
  padding: 14px;
}

.workload-metric span {
  display: block;
  color: #909399;
  font-size: 12px;
}

.workload-metric strong {
  display: block;
  margin-top: 8px;
  color: #303133;
  font-size: 20px;
  overflow-wrap: anywhere;
}

.dashboard-focus-card {
  min-height: 118px;
  padding: 14px;
}

.dashboard-focus-card strong,
.dashboard-focus-card span,
.dashboard-focus-card small {
  display: block;
}

.dashboard-focus-card strong {
  font-size: 18px;
}

.dashboard-focus-card span {
  margin-top: 10px;
  color: #2f80ed;
  font-size: 30px;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.dashboard-focus-card small {
  margin-top: 6px;
  color: #909399;
  line-height: 1.4;
}

.dashboard-top-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dashboard-top-row {
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 8px 0;
  border-bottom: 1px dashed #e4e7ed;
  font-size: 12px;
}

.dashboard-top-row span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-top-row strong {
  color: #303133;
}

.dashboard-empty {
  padding: 18px 12px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  color: #909399;
  font-size: 13px;
  text-align: center;
  background: #fafafa;
}

@media (max-width: 1400px) {
  .runtime-dashboard-shell {
    left: 340px;
  }
}

@media (max-width: 1200px) {
  .dashboard-hero,
  .dashboard-grid--charts,
  .dashboard-grid--detail,
  .dashboard-grid--formula,
  .dashboard-grid--top {
    grid-template-columns: 1fr;
  }

  .dashboard-panel--wide {
    grid-column: span 1;
  }

  .experiment-optimization-cards {
    grid-template-columns: 1fr;
  }

  .experiment-optimization-table-head,
  .experiment-optimization-table-row,
  .experiment-optimization-table--dimension .experiment-optimization-table-head,
  .experiment-optimization-table--dimension .experiment-optimization-table-row,
  .experiment-optimization-table--efficiency .experiment-optimization-table-head,
  .experiment-optimization-table--efficiency .experiment-optimization-table-row {
    grid-template-columns: 1fr;
  }

  .dashboard-kpis,
  .workload-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .runtime-dashboard-shell {
    left: 300px;
  }
}

@media (max-width: 720px) {

  .runtime-dashboard {
    padding: 12px;
  }

  .dashboard-kpis,
  .workload-metric-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-cost-table-head,
  .dashboard-cost-table-row {
    grid-template-columns: 1fr 1fr;
  }
}

</style>
