<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { processingChainApi } from '@/api/processingChainApi'
import type { ProcessingChain, ProcessingChainGraph, ProcessingGraphStage, ProcessingStage } from '@/api/processingChainApi'
import { poiManagerApi } from '@/api/poiManagerApi'
import type { POIFromDB } from '@/api/poiManagerApi'

const chains = ref<ProcessingChain[]>([])
const stages = ref<ProcessingStage[]>([])
const poiOptions = ref<POIFromDB[]>([])
const selectedChainId = ref<number | null>(null)
const loadingChains = ref(false)
const loadingStages = ref(false)
const saving = ref(false)
const graphVisible = ref(false)
const graphLoading = ref(false)
const graph = ref<ProcessingChainGraph | null>(null)

const chainDialogVisible = ref(false)
const stageDialogVisible = ref(false)
const editingStageId = ref<number | null>(null)

const chainForm = reactive({
    chainCode: '',
    chainName: '',
    description: ''
})

const stageForm = reactive({
    stageOrder: 1,
    stageName: '',
    processingPoiId: undefined as number | undefined,
    inputGoodsSku: '',
    outputGoodsSku: '',
    processingTimeMinutes: 60,
    outputWeightRatio: 1,
    minBatchSize: undefined as number | undefined,
    maxCapacityPerCycle: undefined as number | undefined
})

const selectedChain = computed(() =>
    chains.value.find(chain => chain.id === selectedChainId.value) || null
)

const nextStageOrder = computed(() => {
    if (stages.value.length === 0) return 1
    return Math.max(...stages.value.map(stage => stage.stageOrder)) + 1
})

const stageFlow = computed(() => {
    if (stages.value.length === 0) return '暂无工序'
    return stages.value
        .map(stage => `${resolveInputSku(stage)} → ${stage.stageName} → ${resolveOutputSku(stage)}`)
        .join(' → ')
})

const skuConsistent = computed(() => {
    const ordered = [...stages.value].sort((a, b) => a.stageOrder - b.stageOrder)
    for (let i = 0; i < ordered.length - 1; i++) {
        if (resolveOutputSku(ordered[i]) !== resolveInputSku(ordered[i + 1])) {
            return false
        }
    }
    return true
})

function resolveInputSku(stage: ProcessingStage): string {
    return stage.inputGoods?.sku || stage.inputGoodsSku || '-'
}

function resolveOutputSku(stage: ProcessingStage): string {
    return stage.outputGoods?.sku || stage.outputGoodsSku || '-'
}

async function loadChains() {
    loadingChains.value = true
    try {
        chains.value = await processingChainApi.getChains()
        if (!selectedChainId.value && chains.value.length > 0) {
            await selectChain(chains.value[0].id!)
        }
    } catch (error: any) {
        ElMessage.error(error.message || '获取加工链失败')
    } finally {
        loadingChains.value = false
    }
}

async function openGraph() {
    if (!selectedChainId.value) return
    graphLoading.value = true
    graphVisible.value = true
    try {
        graph.value = await processingChainApi.getGraph(selectedChainId.value)
    } catch (error: any) {
        ElMessage.error(error.message || '获取加工链图结构失败')
    } finally {
        graphLoading.value = false
    }
}

async function loadPois() {
    try {
        poiOptions.value = await poiManagerApi.getAll()
    } catch (error: any) {
        ElMessage.warning(error.message || '获取 POI 列表失败，请先维护 POI')
    }
}

function selectChainByRow(row: ProcessingChain) {
    void selectChain(row.id!)
}

function formatGraphInputs(stage: ProcessingGraphStage): string {
    return (stage.inputs || [])
        .map(input => `${input.inputKey}:${input.sku}(${input.inputShare})`)
        .join('；')
}

function formatGraphEdges(value: ProcessingChainGraph): string {
    return value.edges
        .map(edge => `${edge.fromStageKey} → ${edge.toStageKey}.${edge.toInputKey}`)
        .join('；')
}

async function selectChain(chainId: number) {
    selectedChainId.value = chainId
    await loadStages()
}

async function loadStages() {
    if (!selectedChainId.value) return
    loadingStages.value = true
    try {
        stages.value = await processingChainApi.getStages(selectedChainId.value)
    } catch (error: any) {
        ElMessage.error(error.message || '获取工序失败')
    } finally {
        loadingStages.value = false
    }
}

function openCreateChain() {
    chainForm.chainCode = ''
    chainForm.chainName = ''
    chainForm.description = ''
    chainDialogVisible.value = true
}

async function submitChain() {
    if (!chainForm.chainCode.trim() || !chainForm.chainName.trim()) {
        ElMessage.warning('请填写加工链编码和名称')
        return
    }
    saving.value = true
    try {
        await processingChainApi.createChain({
            chainCode: chainForm.chainCode.trim(),
            chainName: chainForm.chainName.trim(),
            description: chainForm.description.trim(),
            status: 'ACTIVE'
        })
        chainDialogVisible.value = false
        ElMessage.success('加工链创建成功')
        await loadChains()
    } catch (error: any) {
        ElMessage.error(error.message || '创建加工链失败')
    } finally {
        saving.value = false
    }
}

function openCreateStage() {
    editingStageId.value = null
    stageForm.stageOrder = nextStageOrder.value
    stageForm.stageName = ''
    stageForm.processingPoiId = undefined
    stageForm.inputGoodsSku = ''
    stageForm.outputGoodsSku = ''
    stageForm.processingTimeMinutes = 60
    stageForm.outputWeightRatio = 1
    stageForm.minBatchSize = undefined
    stageForm.maxCapacityPerCycle = undefined
    stageDialogVisible.value = true
}

function openEditStage(stage: ProcessingStage) {
    editingStageId.value = stage.id || null
    stageForm.stageOrder = stage.stageOrder
    stageForm.stageName = stage.stageName
    stageForm.processingPoiId = stage.processingPOI?.id
    stageForm.inputGoodsSku = stage.inputGoods?.sku || stage.inputGoodsSku || ''
    stageForm.outputGoodsSku = stage.outputGoods?.sku || stage.outputGoodsSku || ''
    stageForm.processingTimeMinutes = stage.processingTimeMinutes
    stageForm.outputWeightRatio = stage.outputWeightRatio ?? 1
    stageForm.minBatchSize = stage.minBatchSize
    stageForm.maxCapacityPerCycle = stage.maxCapacityPerCycle
    stageDialogVisible.value = true
}

function buildStagePayload(): ProcessingStage {
    const selectedPoi = poiOptions.value.find(poi => Number(poi.id) === stageForm.processingPoiId)
    return {
        id: editingStageId.value || undefined,
        stageOrder: stageForm.stageOrder,
        stageName: stageForm.stageName.trim(),
        processingPOI: selectedPoi as any,
        inputGoodsSku: stageForm.inputGoodsSku.trim(),
        outputGoodsSku: stageForm.outputGoodsSku.trim(),
        processingTimeMinutes: stageForm.processingTimeMinutes,
        outputWeightRatio: stageForm.outputWeightRatio,
        minBatchSize: stageForm.minBatchSize,
        maxCapacityPerCycle: stageForm.maxCapacityPerCycle
    }
}

async function submitStage() {
    if (!selectedChainId.value) return
    if (!stageForm.stageName.trim()) {
        ElMessage.warning('请填写工序名称')
        return
    }
    if (!stageForm.processingPoiId) {
        ElMessage.warning('请选择加工 POI')
        return
    }
    if (!stageForm.inputGoodsSku.trim() || !stageForm.outputGoodsSku.trim()) {
        ElMessage.warning('请填写输入和输出 SKU')
        return
    }
    if (stageForm.processingTimeMinutes <= 0) {
        ElMessage.warning('加工时间必须大于 0')
        return
    }
    if (stageForm.outputWeightRatio <= 0) {
        ElMessage.warning('产出率必须大于 0')
        return
    }

    const payload = buildStagePayload()
    const candidateStages = editingStageId.value
        ? stages.value.map(stage => (stage.id === editingStageId.value ? payload : stage))
        : [...stages.value, payload]
    const orderedStages = [...candidateStages].sort((a, b) => a.stageOrder - b.stageOrder)
    for (let i = 0; i < orderedStages.length - 1; i++) {
        if (resolveOutputSku(orderedStages[i]) !== resolveInputSku(orderedStages[i + 1])) {
            ElMessage.error(
                `工序 SKU 不一致：第 ${orderedStages[i].stageOrder} 道输出 ${resolveOutputSku(orderedStages[i])}，第 ${orderedStages[i + 1].stageOrder} 道输入 ${resolveInputSku(orderedStages[i + 1])}`
            )
            return
        }
    }

    saving.value = true
    try {
        if (editingStageId.value) {
            await processingChainApi.updateStage(editingStageId.value, payload)
        } else {
            await processingChainApi.createStage(selectedChainId.value, payload)
        }
        stageDialogVisible.value = false
        ElMessage.success('工序保存成功')
        await loadStages()
        await loadChains()
    } catch (error: any) {
        ElMessage.error(error.message || '工序保存失败')
    } finally {
        saving.value = false
    }
}

async function removeStage(stage: ProcessingStage) {
    if (!stage.id) return
    try {
        await ElMessageBox.confirm(`确定删除工序「${stage.stageName}」吗？`, '提示', {
            type: 'warning'
        })
        await processingChainApi.deleteStage(stage.id)
        ElMessage.success('工序删除成功')
        await loadStages()
        await loadChains()
    } catch (error: any) {
        if (error !== 'cancel' && error?.message !== 'cancel') {
            ElMessage.error(error.message || '工序删除失败')
        }
    }
}

async function changeChainStatus(chain: ProcessingChain | null) {
    if (!chain?.id) return
    if (!chain.id) return
    const nextStatus = chain.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    try {
        await processingChainApi.updateChainStatus(chain.id, nextStatus)
        ElMessage.success('加工链状态更新成功')
        await loadChains()
    } catch (error: any) {
        ElMessage.error(error.message || '加工链状态更新失败')
    }
}

async function removeChain(chain: ProcessingChain | null) {
    if (!chain?.id) return
    if (!chain.id) return
    try {
        await ElMessageBox.confirm(`确定删除加工链「${chain.chainName}」吗？`, '提示', {
            type: 'warning'
        })
        await processingChainApi.deleteChain(chain.id)
        if (selectedChainId.value === chain.id) {
            selectedChainId.value = null
            stages.value = []
        }
        ElMessage.success('加工链删除成功')
        await loadChains()
    } catch (error: any) {
        if (error !== 'cancel' && error?.message !== 'cancel') {
            ElMessage.error(error.message || '加工链删除失败')
        }
    }
}

onMounted(async () => {
    await Promise.all([loadChains(), loadPois()])
})
</script>

<template>
    <el-container class="processing-chain-page">
        <el-header class="page-header">
            <div class="header-content">
                <h2>加工链管理</h2>
                <div class="header-actions">
                    <el-button @click="openGraph">查看图结构</el-button>
                    <el-button type="primary" @click="openCreateChain">新建加工链</el-button>
                    <el-button
                        type="primary"
                        plain
                        :disabled="!selectedChainId"
                        @click="openCreateStage"
                    >
                        新增工序
                    </el-button>
                </div>
            </div>
        </el-header>

        <el-container>
            <el-aside width="340px" class="chain-aside">
                <el-card shadow="never">
                    <template #header>
                        <span>加工链列表</span>
                    </template>
                    <el-table
                        :data="chains"
                        v-loading="loadingChains"
                        highlight-current-row
                        height="520"
                        @row-click="selectChainByRow"
                    >
                        <el-table-column prop="chainCode" label="编码" width="110" />
                        <el-table-column prop="chainName" label="名称" min-width="130" />
                        <el-table-column label="状态" width="90">
                            <template #default="{ row }">
                                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                                    {{ row.status }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="工序" width="65">
                            <template #default="{ row }">
                                {{ row.stages?.length || 0 }}
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>
            </el-aside>

            <el-main class="stage-main">
                <el-empty v-if="!selectedChain" description="请选择或创建加工链" />
                <template v-else>
                    <el-card shadow="never" class="summary-card">
                        <template #header>
                            <div class="card-header">
                                <span>{{ selectedChain.chainName }}</span>
                                <div>
                                    <el-tag :type="skuConsistent ? 'success' : 'danger'">
                                        {{ skuConsistent ? 'SKU 一致' : 'SKU 不一致' }}
                                    </el-tag>
                                    <el-button
                                        size="small"
                                        @click="changeChainStatus(selectedChain)"
                                    >
                                        {{ selectedChain.status === 'ACTIVE' ? '停用' : '启用' }}
                                    </el-button>
                                    <el-button
                                        size="small"
                                        type="danger"
                                        @click="removeChain(selectedChain)"
                                    >
                                        删除
                                    </el-button>
                                </div>
                            </div>
                        </template>
                        <div class="chain-flow">{{ stageFlow }}</div>
                    </el-card>

                    <el-card shadow="never">
                        <template #header>
                            <span>工序列表</span>
                        </template>
                        <el-table :data="stages" v-loading="loadingStages">
                            <el-table-column prop="stageOrder" label="顺序" width="70" />
                            <el-table-column prop="stageName" label="工序" min-width="130" />
                            <el-table-column label="加工 POI" min-width="140">
                                <template #default="{ row }">
                                    {{ row.processingPOI?.name || row.processingPOI?.id }}
                                </template>
                            </el-table-column>
                            <el-table-column label="输入 SKU" min-width="110">
                                <template #default="{ row }">{{ resolveInputSku(row) }}</template>
                            </el-table-column>
                            <el-table-column label="输出 SKU" min-width="110">
                                <template #default="{ row }">{{ resolveOutputSku(row) }}</template>
                            </el-table-column>
                            <el-table-column prop="processingTimeMinutes" label="时长(分钟)" width="100" />
                            <el-table-column prop="outputWeightRatio" label="产出率" width="85" />
                            <el-table-column label="操作" width="130" fixed="right">
                                <template #default="{ row }">
                                    <el-button size="small" @click="openEditStage(row)">编辑</el-button>
                                    <el-button size="small" type="danger" @click="removeStage(row)">
                                        删除
                                    </el-button>
                                </template>
                            </el-table-column>
                        </el-table>
                    </el-card>
                </template>
            </el-main>
        </el-container>

        <el-dialog v-model="chainDialogVisible" title="新建加工链" width="520px">
            <el-form label-width="90px">
                <el-form-item label="编码" required>
                    <el-input v-model="chainForm.chainCode" placeholder="例如 STEEL_CHAIN" />
                </el-form-item>
                <el-form-item label="名称" required>
                    <el-input v-model="chainForm.chainName" placeholder="例如钢材加工链" />
                </el-form-item>
                <el-form-item label="描述">
                    <el-input
                        v-model="chainForm.description"
                        type="textarea"
                        :rows="3"
                        placeholder="加工链说明"
                    />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="chainDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitChain">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog
            v-model="stageDialogVisible"
            :title="editingStageId ? '编辑工序' : '新增工序'"
            width="620px"
        >
            <el-form label-width="110px">
                <el-form-item label="工序顺序" required>
                    <el-input-number v-model="stageForm.stageOrder" :min="1" />
                </el-form-item>
                <el-form-item label="工序名称" required>
                    <el-input v-model="stageForm.stageName" placeholder="例如炼钢" />
                </el-form-item>
                <el-form-item label="加工 POI" required>
                    <el-select
                        v-model="stageForm.processingPoiId"
                        filterable
                        placeholder="选择加工 POI"
                        style="width: 100%"
                    >
                        <el-option
                            v-for="poi in poiOptions"
                            :key="poi.id"
                            :label="poi.name"
                            :value="Number(poi.id)"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="输入 SKU" required>
                    <el-input v-model="stageForm.inputGoodsSku" placeholder="例如 RAW_STEEL" />
                </el-form-item>
                <el-form-item label="输出 SKU" required>
                    <el-input v-model="stageForm.outputGoodsSku" placeholder="例如 STEEL_PLATE" />
                </el-form-item>
                <el-form-item label="加工时长(分)" required>
                    <el-input-number v-model="stageForm.processingTimeMinutes" :min="1" />
                </el-form-item>
                <el-form-item label="产出率" required>
                    <el-input-number
                        v-model="stageForm.outputWeightRatio"
                        :min="0.01"
                        :step="0.05"
                    />
                </el-form-item>
                <el-form-item label="最小批量">
                    <el-input-number v-model="stageForm.minBatchSize" :min="0" />
                </el-form-item>
                <el-form-item label="单次产能">
                    <el-input-number v-model="stageForm.maxCapacityPerCycle" :min="0" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="stageDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitStage">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="graphVisible" title="加工链图结构" width="720px">
            <div v-loading="graphLoading">
                <el-descriptions v-if="graph" :column="1" border>
                    <el-descriptions-item label="加工链">{{ graph.chainName }}（{{ graph.chainCode }}）</el-descriptions-item>
                </el-descriptions>
                <el-table v-if="graph" :data="graph.stages" border>
                    <el-table-column prop="stageOrder" label="顺序" width="70" />
                    <el-table-column prop="stageKey" label="标识" width="100" />
                    <el-table-column prop="stageName" label="工序" min-width="120" />
                    <el-table-column prop="outputGoodsSku" label="输出 SKU" min-width="110" />
                    <el-table-column label="输入" min-width="180">
                        <template #default="{ row }">
                            {{ formatGraphInputs(row) }}
                        </template>
                    </el-table-column>
                </el-table>
                <el-divider v-if="graph">物料流</el-divider>
                <div v-if="graph" class="chain-flow">
                    {{ graph.edges.map(edge => `${edge.fromStageKey} → ${edge.toStageKey}.${edge.toInputKey}`).join('；') }}
                </div>
            </div>
        </el-dialog>    </el-container>
</template>

<style scoped>
.processing-chain-page {
    height: 100vh;
    background: #f5f7fa;
}

.page-header {
    background: #fff;
    border-bottom: 1px solid #e6e6e6;
}

.header-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 100%;
}

.header-content h2 {
    margin: 0;
    font-size: 20px;
    color: #303133;
}

.header-actions {
    display: flex;
    gap: 8px;
}

.chain-aside {
    padding: 12px;
    border-right: 1px solid #e6e6e6;
    background: #fff;
}

.stage-main {
    padding: 12px;
}

.summary-card {
    margin-bottom: 12px;
}

.card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.chain-flow {
    font-family: Consolas, Monaco, monospace;
    color: #303133;
    line-height: 1.8;
    word-break: break-all;
}
</style>
