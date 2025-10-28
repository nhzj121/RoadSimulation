import request from '../utils/request'
import {AxiosResponse} from "axios";

export interface POIFromDB{
    id: string;
    name: string;
    type: string;
    longitude: number;
    latitude: number;
    tel: string;
    address: string;
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

export const poiManagerApi = {
    // 获取所有POI数据 - 对应后端的 /api/pois/all
    async getAll(): Promise<POIFromDB[]> {
        const response = await request.get<BackendResponse<POIFromDB[]>>('/api/pois/all');
        if (response.data.success) {
            return response.data.data || [];
        } else {
            throw new Error(response.data.error || '获取数据失败');
        }
    },

    // 根据类型获取POI - 对应后端的 /api/pois/type/{type}
    async getByType(type: string): Promise<POIFromDB[]> {
        const response = await request.get<BackendResponse<POIFromDB[]>>(`/api/pois/type/${type}`);
        if (response.data.success) {
            return response.data.data || [];
        } else {
            throw new Error(response.data.error || '根据类型获取数据失败');
        }
    },

    // 批量保存POI数据 - 对应后端的 /api/pois/batch-save
    async batchSave(pois: POIFromDB[]): Promise<{ success: boolean; message: string }> {
        const response = await request.post<BackendResponse>('/api/pois/batch-save', pois);
        if (response.data.success) {
            return {
                success: true,
                message: response.data.message || '保存成功'
            };
        } else {
            throw new Error(response.data.error || '保存失败');
        }
    },

    // 保存单个POI - 对应后端的 /api/pois/save
    async save(poi: POIFromDB): Promise<{ success: boolean; message: string; data?: any }> {
        const response = await request.post<BackendResponse>('/api/pois/save', poi);
        return {
            success: response.data.success,
            message: response.data.message || response.data.error || '操作完成',
            data: response.data
        };
    },

    // 删除POI - 对应后端的 /api/pois/{id}
    async delete(id: string): Promise<{ success: boolean; message: string }> {
        const response = await request.delete<BackendResponse>(`/api/pois/${id}`);
        if (response.data.success) {
            return { success: true, message: response.data.message || '删除成功' };
        } else {
            throw new Error(response.data.error || '删除失败');
        }
    },

    // 获取POI类型枚举 - 对应后端的 /api/pois/types
    async getPOITypes(): Promise<string[]> {
        try{
            const response = await request.get<BackendResponse>(`/api/pois/types`);
            if(response.data.success && response.data.data){
                return response.data.data;
            } else{
                throw new Error(response.data.error || '获取POI类型失败');
            }
        } catch(error){
            throw new Error(`获取POI类型失败: ${error instanceof Error ? error.message : '未知错误'}`);
        }
    }
}