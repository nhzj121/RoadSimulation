import request from '../utils/request'

export type ProcessingChainStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE'

export interface ProcessingPOIRef {
    id: number
    name?: string
}

export interface ProcessingStage {
    id?: number
    stageOrder: number
    stageName: string
    description?: string
    processingPOI: ProcessingPOIRef
    inputGoods?: { sku?: string }
    inputGoodsSku?: string
    outputGoods?: { sku?: string }
    outputGoodsSku?: string
    inputWeightRatio?: number
    outputWeightRatio?: number
    processingTimeMinutes: number
    minBatchSize?: number
    maxCapacityPerCycle?: number
}

export interface ProcessingChain {
    id?: number
    chainCode: string
    chainName: string
    status: ProcessingChainStatus
    description?: string
    stages?: ProcessingStage[]
}

export interface ProcessingStageInput {
    id?: number
    inputKey: string
    sku: string
    inputShare: number
}

export interface ProcessingGraphStage {
    id?: number
    stageOrder: number
    stageKey?: string
    stageName: string
    processingPoiId: number
    outputGoodsSku?: string
    outputWeightRatio?: number
    inputs?: ProcessingStageInput[]
}

export interface ProcessingGraphEdge {
    fromStageKey?: string
    toStageKey?: string
    toInputKey?: string
}

export interface ProcessingChainGraph {
    id?: number
    chainCode: string
    chainName: string
    stages: ProcessingGraphStage[]
    edges: ProcessingGraphEdge[]
}
function errorMessage(error: any, fallback: string): string {
    return error?.response?.data?.message
        || error?.response?.data?.error
        || error?.message
        || fallback
}

export const processingChainApi = {
    async getChains(): Promise<ProcessingChain[]> {
        try {
            const response = await request.get<ProcessingChain[]>('/api/v1/processing-chains')
            return response.data || []
        } catch (error: any) {
            throw new Error(errorMessage(error, '获取加工链失败'))
        }
    },

    async getChain(id: number): Promise<ProcessingChain> {
        try {
            const response = await request.get<ProcessingChain>(`/api/v1/processing-chains/${id}`)
            return response.data
        } catch (error: any) {
            throw new Error(errorMessage(error, '获取加工链详情失败'))
        }
    },

    async getStages(chainId: number): Promise<ProcessingStage[]> {
        try {
            const response = await request.get<ProcessingStage[]>(
                `/api/v1/processing-chains/${chainId}/stages`
            )
            return response.data || []
        } catch (error: any) {
            throw new Error(errorMessage(error, '获取加工链工序失败'))
        }
    },

    async createGraph(graph: ProcessingChainGraph): Promise<ProcessingChainGraph> {
        try {
            const response = await request.post<ProcessingChainGraph>(
                '/api/v1/processing-chains/graph',
                graph
            )
            return response.data
        } catch (error: any) {
            throw new Error(errorMessage(error, '创建 Y 型加工链失败'))
        }
    },

    async getGraph(chainId: number): Promise<ProcessingChainGraph> {
        try {
            const response = await request.get<ProcessingChainGraph>(
                `/api/v1/processing-chains/${chainId}/graph`
            )
            return response.data
        } catch (error: any) {
            throw new Error(errorMessage(error, '获取加工链图结构失败'))
        }
    },

    async createChain(chain: ProcessingChain): Promise<ProcessingChain> {
        try {
            const response = await request.post<ProcessingChain>('/api/v1/processing-chains', chain)
            return response.data
        } catch (error: any) {
            throw new Error(errorMessage(error, '创建加工链失败'))
        }
    },

    async updateChainStatus(id: number, status: ProcessingChainStatus): Promise<ProcessingChain> {
        try {
            const response = await request.patch<ProcessingChain>(
                `/api/v1/processing-chains/${id}/status`,
                null,
                { params: { status } }
            )
            return response.data
        } catch (error: any) {
            throw new Error(errorMessage(error, '更新加工链状态失败'))
        }
    },

    async deleteChain(id: number): Promise<void> {
        try {
            await request.delete(`/api/v1/processing-chains/${id}`)
        } catch (error: any) {
            throw new Error(errorMessage(error, '删除加工链失败'))
        }
    },

    async createStage(chainId: number, stage: ProcessingStage): Promise<ProcessingStage> {
        try {
            const response = await request.post<ProcessingStage>(
                `/api/v1/processing-chains/${chainId}/stages`,
                stage
            )
            return response.data
        } catch (error: any) {
            throw new Error(errorMessage(error, '创建工序失败'))
        }
    },

    async updateStage(stageId: number, stage: ProcessingStage): Promise<ProcessingStage> {
        try {
            const response = await request.put<ProcessingStage>(
                `/api/v1/processing-chains/stages/${stageId}`,
                stage
            )
            return response.data
        } catch (error: any) {
            throw new Error(errorMessage(error, '更新工序失败'))
        }
    },

    async deleteStage(stageId: number): Promise<void> {
        try {
            await request.delete(`/api/v1/processing-chains/stages/${stageId}`)
        } catch (error: any) {
            throw new Error(errorMessage(error, '删除工序失败'))
        }
    }
}
