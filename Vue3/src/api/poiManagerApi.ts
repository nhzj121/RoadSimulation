import request from '../utils/request'
import {AxiosResponse} from "axios";

export interface POIFromDB{
    id: string;
    name: string;
    type: string;
    longitude: number;
    latitude: number;
    tel: string;
    // 后端可能返回的其他字段
    // ToDo
    createdAt?: string;
    updatedAt?: string;
}

// 后端响应格式
interface BackendResponse<T = any> {
    success: boolean;
    message?: string;
    error?: string;
    data?: T;
}

export const poiApi = {
    // 获取所有POI数据 - 对应后端的 /api/poi/all
    async getAll(): Promise<POIFromDB[]> {
        const response = await request.get<BackendResponse<POIFromDB[]>>('/api/poi/all');
        if (response.data.success) {
            return response.data.data || [];
        } else {
            throw new Error(response.data.error || '获取数据失败');
        }
    },

    // 根据类型获取POI - 对应后端的 /api/poi/type/{type}
    async getByType(type: string): Promise<POIFromDB[]> {
        const response = await request.get<BackendResponse<POIFromDB[]>>(`/api/poi/type/${type}`);
        if (response.data.success) {
            return response.data.data || [];
        } else {
            throw new Error(response.data.error || '根据类型获取数据失败');
        }
    },

    // 批量保存POI数据 - 对应后端的 /api/poi/batch-save
    async batchSave(pois: POIFromDB[]): Promise<{ success: boolean; message: string }> {
        const response = await request.post<BackendResponse>('/api/poi/batch-save', pois);
        if (response.data.success) {
            return {
                success: true,
                message: response.data.message || '保存成功'
            };
        } else {
            throw new Error(response.data.error || '保存失败');
        }
    },

    // 保存单个POI - 对应后端的 /api/poi/save
    async save(poi: POIFromDB): Promise<{ success: boolean; message: string; data?: any }> {
        const response = await request.post<BackendResponse>('/api/poi/save', poi);
        return {
            success: response.data.success,
            message: response.data.message || response.data.error || '操作完成',
            data: response.data
        };
    },

    // 删除POI - 对应后端的 /api/poi/{id}
    async delete(id: string): Promise<{ success: boolean; message: string }> {
        const response = await request.delete<BackendResponse>(`/api/poi/${id}`);
        if (response.data.success) {
            return { success: true, message: response.data.message || '删除成功' };
        } else {
            throw new Error(response.data.error || '删除失败');
        }
    },

    // 获取POI类型枚举 - 可能需要后端支持，这里先模拟
    async getPOITypes(): Promise<string[]> {
        // 如果后端有提供类型枚举的接口，可以调用
        // 暂时返回前端已知的类型
        return ['FACTORY', 'WAREHOUSE', 'GAS_STATION', 'MAINTENANCE', 'REST_AREA', 'TRANSPORT'];
    }
}