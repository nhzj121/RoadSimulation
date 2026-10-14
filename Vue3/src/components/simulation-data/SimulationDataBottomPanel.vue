<template>
  <section class="data-bottom-shell" :class="{ 'data-bottom-shell--collapsed': collapsed, 'data-bottom-shell--compact': compact }" aria-labelledby="simulation-data-bottom-title">
    <header class="data-bottom-shell__header">
      <div>
        <h3 id="simulation-data-bottom-title">运行观察记录</h3>
        <p v-show="!collapsed">本次页面会话 · 最近50条 · 采样之间的事件可能未被观察到</p>
      </div>
      <nav v-show="!collapsed" class="data-bottom-shell__filters" aria-label="记录分类">
        <button v-for="item in filters" :key="item.value" type="button"
          :aria-pressed="filter === item.value" @click="filter = item.value">{{ item.label }}</button>
      </nav>
    </header>
    <div v-if="collapsed" class="data-bottom-shell__summary" aria-label="监控摘要">
      <span v-for="source in sources" :key="source.key" :title="source.label + '：' + sourceHealthText(state.health[source.key])">
        {{ source.label }}：{{ sourceHealthText(state.health[source.key]) }}
      </span>
    </div>
    <div v-show="!collapsed" class="data-bottom-shell__sources" aria-label="数据源更新状态">
      <div v-for="source in sources" :key="source.key" class="data-bottom-shell__source"
        :class="{ 'data-bottom-shell__source--error': state.health[source.key].currentError }">
        <div><strong>{{ source.label }}</strong><span>{{ sourceHealthText(state.health[source.key]) }}</span></div>
        <small>最近成功接收（页面时间）：{{ formatTime(state.health[source.key].lastSuccessAt) }}</small>
        <small v-if="state.health[source.key].lastFailure" :title="state.health[source.key].lastFailure">
          最近失败：{{ state.health[source.key].lastFailure }}
        </small>
      </div>
    </div>
    <div v-show="!collapsed" class="data-bottom-shell__content">
      <div v-if="!records.length" class="data-bottom-shell__empty" role="status">
        {{ filter === 'all' ? '当前会话尚无可展示的变化记录。' : '该分类暂无记录。' }}
      </div>
      <ol v-else class="data-bottom-shell__records" aria-label="最近观察记录">
        <li v-for="record in records" :key="record.id" :class="'data-bottom-shell__record--' + record.category">
          <div class="data-bottom-shell__record-heading">
            <span class="data-bottom-shell__tag">{{ categoryLabel(record.category) }}</span>
            <strong>{{ record.title }}</strong>
            <span v-if="record.vehicleText" class="data-bottom-shell__vehicle">{{ record.vehicleText }}</span>
            <small v-if="record.eventId">事件 #{{ record.eventId }}</small>
          </div>
          <p>{{ record.detail }}</p>
          <div class="data-bottom-shell__time">
            <span>{{ record.timeLabel }}：{{ formatTime(record.eventTime || record.observedAt) }}</span>
            <span v-if="record.eventTime">页面接收：{{ formatTime(record.observedAt) }}</span>
          </div>
        </li>
      </ol>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { filterSessionRecords, sourceHealthText } from '../../utils/simulationSessionRecords';
import type { DataSource, RecordCategory, RecordFilter, SessionRecordsState } from '../../utils/simulationSessionRecords';

// Phase 5：组件只消费父级会话状态，卸载/隐藏不会改变记录，也不建立网络请求。
const props = withDefaults(defineProps<{ state: SessionRecordsState; collapsed?: boolean; compact?: boolean }>(), { collapsed: false, compact: false });
const filter = ref<RecordFilter>('all');
const filters: Array<{ value: RecordFilter; label: string }> = [
  { value: 'all', label: '全部' }, { value: 'random', label: '随机事件' },
  { value: 'exception', label: '异常' }, { value: 'monitor', label: '监控' }
];
const sources: Array<{ key: DataSource; label: string }> = [
  { key: 'transport', label: '运输监控' }, { key: 'evaluation', label: '评价快照' }
];
const records = computed(() => filterSessionRecords(props.state.records, filter.value));
function categoryLabel(category: RecordCategory): string {
  return { random: '事件', exception: '异常', monitor: '监控' }[category];
}
function formatTime(value: string | null): string {
  if (!value) return '尚未取得';
  // 后端 LocalDateTime 不带时区，直接显示原值；仅页面接收时间（ISO UTC）转换为本地时间。
  if (!value.endsWith('Z')) return value.replace('T', ' ');
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}
</script>

<style scoped>
.data-bottom-shell { height: 100%; min-height: 0; display: flex; flex-direction: column; padding: 12px 16px; box-sizing: border-box; border-top: 1px solid #dcdfe6; color: #303133; background: #fff; overflow: hidden; }
.data-bottom-shell__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; flex-shrink: 0; }
.data-bottom-shell h3 { margin: 0; font-size: 15px; line-height: 22px; }
.data-bottom-shell__header p { margin: 2px 0 0; color: #7b8797; font-size: 10px; }
.data-bottom-shell__filters { display: flex; gap: 4px; flex-wrap: wrap; }
.data-bottom-shell__filters button { padding: 4px 8px; border: 1px solid #dfe5ec; border-radius: 5px; color: #606266; background: #fff; font-size: 11px; cursor: pointer; }
.data-bottom-shell__filters button[aria-pressed="true"] { color: #337ecc; background: #ecf5ff; border-color: #b3d8ff; }
.data-bottom-shell__filters button:focus-visible { outline: 2px solid #409eff; outline-offset: 2px; }
.data-bottom-shell__sources { margin: 8px 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; flex-shrink: 0; }
.data-bottom-shell__source { min-width: 0; padding: 6px 9px; border: 1px solid #e4e7ed; border-radius: 6px; background: #f8fafc; }
.data-bottom-shell__source > div { display: flex; justify-content: space-between; gap: 5px; flex-wrap: wrap; font-size: 11px; }
.data-bottom-shell__source > div span { color: #577188; }
.data-bottom-shell__source small { display: block; color: #7b8797; font-size: 10px; overflow-wrap: anywhere; }
.data-bottom-shell__source small[title] { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.data-bottom-shell__source--error { border-color: #efc5c5; background: #fffafa; }
.data-bottom-shell__source--error > div span { color: #c45656; }
.data-bottom-shell__content { min-height: 0; flex: 1; overflow-y: auto; overscroll-behavior: contain; }
.data-bottom-shell__empty { padding: 16px; border: 1px dashed #dcdfe6; border-radius: 6px; color: #909399; background: #fafafa; font-size: 12px; }
.data-bottom-shell__records { list-style: none; padding: 0; margin: 0; }
.data-bottom-shell__records li { padding: 7px 10px; border-left: 3px solid #337ecc; border-bottom: 1px solid #edf0f5; }
.data-bottom-shell__records .data-bottom-shell__record--exception { border-left-color: #d9902f; }
.data-bottom-shell__records .data-bottom-shell__record--monitor { border-left-color: #718096; }
.data-bottom-shell__record-heading { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; font-size: 11px; }
.data-bottom-shell__record-heading strong { font-weight: 600; }
.data-bottom-shell__tag { color: #577188; background: #f0f4f8; border-radius: 3px; padding: 1px 4px; font-size: 10px; }
.data-bottom-shell__vehicle { color: #337ecc; }
.data-bottom-shell__record-heading small { color: #7b8797; }
.data-bottom-shell__records p { margin: 2px 0; color: #606f7b; font-size: 11px; overflow-wrap: anywhere; }
.data-bottom-shell__time { display: flex; gap: 10px; flex-wrap: wrap; color: #7b8797; font-size: 10px; }
.data-bottom-shell--collapsed { flex-direction: row; align-items: center; gap: 12px; padding: 6px 12px; }
.data-bottom-shell--collapsed h3 { font-size: 12px; }
.data-bottom-shell__summary { min-width: 0; flex: 1; display: flex; gap: 12px; font-size: 10px; color: #718096; }
.data-bottom-shell__summary span { min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.data-bottom-shell--compact .data-bottom-shell__source small[title] { -webkit-line-clamp: 1; }
@media (max-width: 450px) { .data-bottom-shell__sources { grid-template-columns: 1fr; } .data-bottom-shell__summary { flex-direction: column; gap: 0; } }
</style>
