<template>
  <section class="selector">
    <h3>{{ title }}</h3>
    <el-radio-group v-model="selection.mode" :disabled="disabled">
      <el-radio label="ALL_ELIGIBLE">全部资格数据（再筛选）</el-radio>
      <el-radio label="EXPLICIT_IDS">指定ID（再筛选）</el-radio>
    </el-radio-group>
    <p class="hint">筛选条件按AND组合，最后排除ID；不会自动补回加工链所需数据。</p>
    <el-input :model-value="selection.includeIds.join(', ')" :disabled="disabled || selection.mode !== 'EXPLICIT_IDS'"
      placeholder="包含ID，逗号分隔" @change="setIds('includeIds', $event)" />
    <el-input :model-value="selection.excludeIds.join(', ')" :disabled="disabled" placeholder="排除ID，逗号分隔" @change="setIds('excludeIds', $event)" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="items" size="small" v-loading="loading" max-height="290">
      <el-table-column label="包含/排除" width="100">
        <template #default="{row}">
          <el-checkbox :model-value="selected(row.id)" :disabled="disabled" @change="pick(row.id, Boolean($event))" />
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="基线记录"><template #default="{row}">{{ row.name || row.licensePlate || row.chainName }}</template></el-table-column>
      <el-table-column label="属性"><template #default="{row}">{{ row.poiType || row.category || row.status || `${row.maxLoadCapacityTonnes}吨 / ${row.cargoVolumeCubicMeters}m³ / ${row.vehicleType}` }}</template></el-table-column>
    </el-table>
    <p class="hint">{{ selection.mode === 'EXPLICIT_IDS' ? '勾选=包含ID' : '勾选=未被显式排除（尚未计算筛选结果）' }}。最终集合以编译预览为准。</p>
    <el-pagination :current-page="page" :page-size="50" :total="total" layout="prev, pager, next, total" :disabled="disabled || loading" @current-change="changePage" />
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { sandboxApi } from '../../api/sandbox'
import { parseIds, toggleId } from '../../sandbox/authoring'
import type { SandboxSelection, SandboxCatalogItem } from '../../types/sandbox'
const props = defineProps<{selection: SandboxSelection; type: string; title: string; disabled: boolean}>()
const emit = defineEmits<{(event: 'invalid', value: boolean): void}>()
const items = ref<SandboxCatalogItem[]>([]), total = ref(0), page = ref(1), loading = ref(false), error = ref('')
const invalidFields = new Set<string>()
function selected(id: number) { return props.selection.mode === 'EXPLICIT_IDS' ? props.selection.includeIds.includes(id) : !props.selection.excludeIds.includes(id) }
function pick(id: number, value: boolean) { toggleId(props.selection, props.selection.mode === 'EXPLICIT_IDS' ? 'includeIds' : 'excludeIds', id, props.selection.mode === 'EXPLICIT_IDS' ? value : !value) }
function setIds(field: 'includeIds' | 'excludeIds', text: string) {
  try { props.selection[field] = parseIds(text); invalidFields.delete(field); error.value = invalidFields.size ? '还有未修正的非法ID字段' : '' }
  catch (failure) { error.value = (failure as Error).message; invalidFields.add(field) }
  emit('invalid', invalidFields.size > 0)
}
async function changePage(value: number) {
  loading.value = true; error.value = ''
  try { const result = await sandboxApi.catalog(props.type, (value - 1) * 50); items.value = result.items; total.value = result.total; page.value = value }
  catch { error.value = '基线目录加载失败，请重试'; items.value = [] }
  finally { loading.value = false }
}
onMounted(() => changePage(1))
</script>
<style scoped>
.selector { border: 1px solid #dcdfe6; padding: 16px; margin: 12px 0; border-radius: 8px; }
.hint { color: #606266; font-size: 13px; }
.el-input { margin-bottom: 8px; }
</style>
