// 运单进度相关API
import request from '../utils/request';

/**
 * 获取活跃运单列表
 * @returns {Promise<Array>}
 */
export const getActiveShipments = async () => {
    try {
        const response = await request.get('/api/shipments/active');
        return response.data;
    } catch (error) {
        console.error('获取活跃运单列表失败:', error);
        throw error;
    }
};

/**
 * 获取运单进度详情
 * @param {number} shipmentId - 运单ID
 * @returns {Promise<Object>}
 */
export const getShipmentProgressDetail = async (shipmentId) => {
    try {
        const response = await request.get(`/api/shipments/${shipmentId}/progress`);
        return response.data;
    } catch (error) {
        console.error(`获取运单${shipmentId}进度详情失败:`, error);
        throw error;
    }
};

/**
 * 批量获取运单进度
 * @param {Array<number>} shipmentIds - 运单ID数组
 * @returns {Promise<Array>}
 */
export const getBatchShipmentProgress = async (shipmentIds) => {
    try {
        const response = await request.post('/api/shipments/batch-progress', shipmentIds);
        return response.data;
    } catch (error) {
        console.error('批量获取运单进度失败:', error);
        throw error;
    }
};

/**
 * 更新运单进度（由车辆到达事件触发）
 * @param {number} shipmentId - 运单ID
 * @returns {Promise<void>}
 */
export const updateShipmentProgress = async (shipmentId) => {
    try {
        await request.patch(`/api/shipments/${shipmentId}/update-progress`);
        console.log(`已更新运单${shipmentId}的进度`);
    } catch (error) {
        console.error(`更新运单${shipmentId}进度失败:`, error);
        throw error;
    }
};

/**
 * 获取运单进度摘要（用于仪表板）
 * @returns {Promise<Object>}
 */
export const getOverallProgressSummary = async () => {
    try {
        const response = await request.get('/api/shipments/progress-summary');
        return response.data;
    } catch (error) {
        console.error('获取运单进度摘要失败:', error);
        throw error;
    }
};

/**
 * 运单项状态映射
 */
export const shipmentItemStatusMap = {
    NOT_ASSIGNED: { text: '未分配', color: '#bfbfbf' },
    ASSIGNED: { text: '已分配', color: '#1890ff' },
    LOADED: { text: '已装货', color: '#fa8c16' },
    IN_TRANSIT: { text: '运输中', color: '#52c41a' },
    DELIVERED: { text: '已送达', color: '#722ed1' },
};

/**
 * 运单状态映射
 */
export const shipmentStatusMap = {
    CREATED: { text: '已创建', color: '#1890ff' },
    PLANNED: { text: '已规划', color: '#722ed1' },
    PICKED_UP: { text: '已提货', color: '#52c41a' },
    IN_TRANSIT: { text: '运输中', color: '#fa8c16' },
    DELIVERED: { text: '已送达', color: '#13c2c2' },
    CANCELLED: { text: '已取消', color: '#f5222d' },
};

/**
 * 计算运单进度颜色
 * @param {number} progressPercentage - 进度百分比
 * @returns {string}
 */
export const getProgressColor = (progressPercentage) => {
    if (progressPercentage >= 100) {
        return '#52c41a'; // 完成 - 绿色
    } else if (progressPercentage >= 70) {
        return '#1890ff'; // 高进度 - 蓝色
    } else if (progressPercentage >= 30) {
        return '#faad14'; // 中等进度 - 橙色
    } else {
        return '#f5222d'; // 低进度 - 红色
    }
};

/**
 * 格式化时间显示
 * @param {string|Date} dateTime - 时间
 * @returns {string}
 */
export const formatDateTime = (dateTime) => {
    if (!dateTime) return '-';

    const date = new Date(dateTime);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });
};

/**
 * 简化运单信息，用于列表显示
 * @param {Object} shipment - 运单对象
 * @returns {Object}
 */
export const simplifyShipmentForList = (shipment) => {
    return {
        id: shipment.shipmentId,
        refNo: shipment.refNo,
        origin: shipment.originPOIName,
        destination: shipment.destPOIName,
        status: shipment.status,
        statusText: shipment.statusText,
        totalItems: shipment.totalItems,
        completedItems: shipment.completedItems,
        progressPercentage: shipment.progressPercentage,
        progressColor: getProgressColor(shipment.progressPercentage),
        updatedAt: shipment.updatedAt,
    };
};