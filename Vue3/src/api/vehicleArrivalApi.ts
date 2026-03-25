import request from '../utils/request'

// 车辆到达事件请求接口
interface VehicleArrivalRequest {
    vehicleId: number;
    poiId: number;
    arrivalTime: string; // ISO 8601格式
    actualLongitude: number;
    actualLatitude: number;
    triggerRadius: number;
}

// 后端响应格式（复用项目中已有的定义）
interface BackendResponse<T = any> {
    success: boolean;
    message?: string;
    data?: T;
}

export const vehicleArrivalApi = {
    /**
     * 上报车辆到达POI点事件
     * @param arrivalData 到达事件数据
     * @returns 后端响应
     */
    async reportVehicleArrival(arrivalData: VehicleArrivalRequest): Promise<BackendResponse<any>> {
        try {
            const response = await request.post('/api/vehicles/arrival', arrivalData);
            return response.data;
        } catch (error: any) {
            console.error('上报车辆到达事件失败:', error);
            return {
                success: false,
                message: error.response?.data?.message || error.message || '上报车辆到达事件失败'
            };
        }
    }
};