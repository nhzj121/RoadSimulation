<template>
  <section class="data-right-panel" aria-labelledby="simulation-data-right-title">
    <header class="data-right-panel__header">
      <div>
        <span class="data-right-panel__eyebrow">EVALUATION</span>
        <h3 id="simulation-data-right-title">目标、结构与约束</h3>
      </div>
      <span class="data-right-panel__connection" :class="connectionClass">
        <i></i>{{ connectionText }}
      </span>
    </header>

    <div v-if="feedState.networkError" class="data-right-panel__message" role="alert">
      {{ feedState.networkError }}<template v-if="feedState.snapshot">；继续显示最近一次成功快照。</template>
    </div>

    <div v-if="feedState.initialLoading" class="data-right-panel__state">
      <span class="data-right-panel__loading-mark"></span>
      <strong>正在读取评价快照</strong>
      <small>与左侧面板共享同一次数据更新。</small>
    </div>

    <div v-else-if="!feedState.snapshot && feedState.networkError" class="data-right-panel__state data-right-panel__state--error">
      <strong>实时评价读取失败</strong>
      <small>不会使用车辆或任务接口拼接替代数据。</small>
    </div>

    <div v-else-if="feedState.waitingForSnapshot || !viewModel" class="data-right-panel__state">
      <strong>等待首个评价快照</strong>
      <small>仿真完成一个业务循环后展示目标与构成。</small>
    </div>

    <template v-else>
      <section class="data-right-panel__section" aria-labelledby="objective-title">
        <div class="data-right-panel__section-heading">
          <div>
            <span>当前后端结果</span>
            <h4 id="objective-title">四个优化目标</h4>
          </div>
          <small>原值 · 未归一化</small>
        </div>
        <div class="data-right-panel__objective-grid">
          <article
            v-for="objective in viewModel.objectives"
            :key="objective.metricId"
            class="data-right-panel__objective"
            :class="`data-right-panel__objective--${objective.status.toLowerCase().replace('_', '-')}`"
            :title="objective.reason || objective.statusText"
          >
            <div>
              <span>目标 {{ objective.index }}</span>
              <small>{{ objective.statusText }}</small>
            </div>
            <strong>{{ objective.valueText }}</strong>
            <p>{{ objective.label }}</p>
          </article>
        </div>
      </section>

      <section class="data-right-panel__section" aria-label="车辆执行状态构成">
        <EvaluationDomainChart
          v-if="viewModel.vehicleStateChart"
          class="data-right-panel__domain-chart"
          :model="viewModel.vehicleStateChart"
          :visible="visible"
        />
        <div v-else class="data-right-panel__chart-empty">车辆状态指标字段缺失。</div>
      </section>

      <section
        class="data-right-panel__constraint"
        :class="`data-right-panel__constraint--${viewModel.serviceConstraint.tone}`"
        aria-labelledby="service-constraint-title"
      >
        <div class="data-right-panel__constraint-heading">
          <div>
            <span>服务约束</span>
            <h4 id="service-constraint-title">P95 等待时间</h4>
          </div>
          <strong>{{ viewModel.serviceConstraint.resultText }}</strong>
        </div>
        <div class="data-right-panel__constraint-values">
          <div><span>货物 P95</span><strong>{{ viewModel.serviceConstraint.cargoP95Text }}</strong></div>
          <div><span>任务 P95</span><strong>{{ viewModel.serviceConstraint.taskP95Text }}</strong></div>
          <div><span>阈值</span><strong>{{ viewModel.serviceConstraint.thresholdText }}</strong></div>
        </div>
        <p v-if="viewModel.serviceConstraint.reason">{{ viewModel.serviceConstraint.reason }}</p>
      </section>

      <section v-if="viewModel.errorCodes.length" class="data-right-panel__errors" aria-label="本轮评价错误码">
        <div><span>本轮评价提示</span><strong>{{ viewModel.errorCodes.length }}</strong></div>
        <code v-for="code in viewModel.errorCodes" :key="code">{{ code }}</code>
      </section>

      <footer class="data-right-panel__footer">
        <span>精确明细保留在完整评价页</span>
        <ElButton type="primary" plain size="small" @click="emit('open-full')">完整评价</ElButton>
      </footer>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ElButton } from 'element-plus';
import type { EvaluationSnapshot } from '../../types/evaluation';
import { buildSimulationDataRightViewModel } from '../../utils/simulationDataRightPresentation';
import EvaluationDomainChart from '../EvaluationDomainChart.vue';

interface SimulationDataFeedState {
  snapshot: EvaluationSnapshot | null;
  initialLoading: boolean;
  waitingForSnapshot: boolean;
  networkError: string;
  polling: boolean;
}

const props = withDefaults(defineProps<{ feedState: SimulationDataFeedState; visible?: boolean }>(), { visible: true });
const emit = defineEmits<{ (event: 'open-full'): void }>();

const viewModel = computed(() => props.feedState.snapshot
  ? buildSimulationDataRightViewModel(props.feedState.snapshot)
  : null
);

const connectionText = computed(() => {
  if (props.feedState.networkError) return '连接异常';
  if (props.feedState.initialLoading) return '读取中';
  if (props.feedState.waitingForSnapshot) return '等待快照';
  if (props.feedState.polling) return '更新中';
  return '已同步';
});

const connectionClass = computed(() => ({
  'data-right-panel__connection--error': Boolean(props.feedState.networkError),
  'data-right-panel__connection--waiting': props.feedState.waitingForSnapshot
    && !props.feedState.networkError
}));
</script>

<style scoped>
.data-right-panel {
  /* 高度由父级唯一滚动容器决定，避免与 ElAside 形成嵌套滚动区。 */
  min-height: 0;
  padding: 14px;
  box-sizing: border-box;
  color: #263445;
  background: #f5f7fa;
}

.data-right-panel__header,
.data-right-panel__section-heading,
.data-right-panel__footer,
.data-right-panel__constraint-heading,
.data-right-panel__objective > div,
.data-right-panel__errors > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.data-right-panel__header { margin-bottom: 12px; }
.data-right-panel__header h3 { margin: 2px 0 0; font-size: 18px; }
.data-right-panel__eyebrow { color: #337ecc; font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.data-right-panel__connection { display: inline-flex; align-items: center; gap: 6px; color: #28784a; font-size: 12px; white-space: nowrap; }
.data-right-panel__connection i { width: 7px; height: 7px; border-radius: 50%; background: #31a66a; box-shadow: 0 0 0 4px rgba(49, 166, 106, .12); }
.data-right-panel__connection--waiting { color: #9b6a14; }
.data-right-panel__connection--waiting i { background: #d9a441; box-shadow: 0 0 0 4px rgba(217, 164, 65, .14); }
.data-right-panel__connection--error { color: #c45656; }
.data-right-panel__connection--error i { background: #d85b5b; box-shadow: 0 0 0 4px rgba(216, 91, 91, .14); }

.data-right-panel__message { margin-bottom: 10px; padding: 10px; border: 1px solid #f1c4c4; border-radius: 8px; color: #b94b4b; background: #fff6f6; font-size: 12px; line-height: 1.5; }
.data-right-panel__state { min-height: 220px; padding: 20px; border: 1px dashed #d9e2ec; border-radius: 10px; display: grid; place-content: center; gap: 8px; color: #718096; background: #fff; text-align: center; }
.data-right-panel__state--error { border-color: #f1c4c4; background: #fffafa; }
.data-right-panel__state small { max-width: 250px; line-height: 1.6; }
.data-right-panel__loading-mark { width: 24px; height: 24px; margin: 0 auto 4px; border: 3px solid #d9ecff; border-top-color: #409eff; border-radius: 50%; animation: data-right-spin .8s linear infinite; }
@keyframes data-right-spin { to { transform: rotate(360deg); } }

.data-right-panel__section { margin-top: 12px; padding: 12px; border: 1px solid #dfe7f1; border-radius: 10px; background: #fff; }
.data-right-panel__section:first-of-type { margin-top: 0; }
.data-right-panel__section-heading span { color: #7b8797; font-size: 10px; }
.data-right-panel__section-heading h4 { margin: 2px 0 0; font-size: 14px; }
.data-right-panel__section-heading small { color: #909399; font-size: 10px; }
.data-right-panel__objective-grid { margin-top: 10px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.data-right-panel__objective { min-width: 0; padding: 10px; border: 1px solid #e5eaf0; border-radius: 8px; background: #fafcfe; }
.data-right-panel__objective > div span { color: #337ecc; font-size: 10px; font-weight: 700; }
.data-right-panel__objective > div small { color: #718096; font-size: 9px; }
.data-right-panel__objective > strong { display: block; margin-top: 8px; color: #24394f; font-size: 18px; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.data-right-panel__objective p { min-height: 30px; margin: 5px 0 0; color: #718096; font-size: 10px; line-height: 15px; }
.data-right-panel__objective--invalid { border-color: #efc5c5; background: #fffafa; }
.data-right-panel__objective--invalid > strong { color: #c45656; }
.data-right-panel__objective--not-available > strong,
.data-right-panel__objective--not-supported > strong,
.data-right-panel__objective--not-applicable > strong,
.data-right-panel__objective--missing > strong { color: #909399; }

.data-right-panel__domain-chart { padding: 0; border: 0; }
.data-right-panel__chart-empty { padding: 20px; color: #718096; text-align: center; }

.data-right-panel__constraint { margin-top: 12px; padding: 12px; border: 1px solid #dfe7f1; border-left-width: 4px; border-radius: 10px; background: #fff; }
.data-right-panel__constraint--ok { border-left-color: #31a66a; }
.data-right-panel__constraint--warning { border-left-color: #d9902f; background: #fffaf2; }
.data-right-panel__constraint--unknown { border-left-color: #a3adbb; }
.data-right-panel__constraint-heading span { color: #7b8797; font-size: 10px; }
.data-right-panel__constraint-heading h4 { margin: 2px 0 0; font-size: 14px; }
.data-right-panel__constraint-heading > strong { font-size: 14px; }
.data-right-panel__constraint--ok .data-right-panel__constraint-heading > strong { color: #28784a; }
.data-right-panel__constraint--warning .data-right-panel__constraint-heading > strong { color: #b66b18; }
.data-right-panel__constraint-values { margin-top: 10px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; }
.data-right-panel__constraint-values > div { min-width: 0; padding: 8px; border-radius: 7px; display: grid; gap: 4px; background: rgba(244, 247, 251, .9); }
.data-right-panel__constraint-values span { color: #7b8797; font-size: 9px; }
.data-right-panel__constraint-values strong { overflow-wrap: anywhere; font-size: 11px; font-variant-numeric: tabular-nums; }
.data-right-panel__constraint p { margin: 9px 0 0; color: #718096; font-size: 10px; line-height: 1.5; }

.data-right-panel__errors { margin-top: 12px; padding: 11px; border: 1px solid #f0d7b1; border-radius: 9px; display: grid; gap: 7px; color: #8a5a13; background: #fffaf0; font-size: 11px; }
.data-right-panel__errors code { padding: 5px 7px; border-radius: 5px; overflow-wrap: anywhere; color: #9a512a; background: rgba(217, 144, 47, .08); font-size: 10px; }
.data-right-panel__footer { padding: 12px 2px 2px; color: #909399; font-size: 11px; }

@media (max-width: 1400px) {
  .data-right-panel__objective-grid { grid-template-columns: 1fr; }
  .data-right-panel__objective p { min-height: auto; }
}
</style>
