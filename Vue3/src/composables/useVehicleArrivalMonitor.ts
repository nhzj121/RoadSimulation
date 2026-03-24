import { ref, computed, watch, onUnmounted } from 'vue';
import { vehicleArrivalApi } from '@/api/vehicleArrivalApi';
import { isVehicleArrived } from '@/utils/geoUtils';

// 车辆到达监控配置
interface MonitoringConfig {
    checkInterval?: number; // 检查间隔（毫秒），默认1000ms
    arrivalThreshold?: number; // 到达阈值（米），默认50米
    preventDuplicateReports?: boolean; // 防止重复上报
    duplicateTimeout?: number; // 重复上报超时（毫秒），默认30000ms
}

// 车辆位置信息
interface VehiclePosition {
    vehicleId: number;
    licensePlate?: string;
    lng: number;
    lat: number;
    [key: string]: any;
}

// POI点信息
interface POI {
    id: number;
    name: string;
    longitude: number;
    latitude: number;
    radius?: number; // POI自定义半径
    [key: string]: any;
}

// 到达记录
interface ArrivalRecord {
    vehicleId: number;
    poiId: number;
    timestamp: number;
}

export function useVehicleArrivalMonitor(config: MonitoringConfig = {}) {
    const {
        checkInterval = 1000,
        arrivalThreshold = 50,
        preventDuplicateReports = true,
        duplicateTimeout = 30000
    } = config;

    // 状态
    const isMonitoring = ref(false);
    const monitoringTimer = ref<NodeJS.Timeout | null>(null);
    const arrivalRecords = ref<ArrivalRecord[]>([]);

    // 清理过期记录
    const cleanupExpiredRecords = () => {
        const now = Date.now();
        arrivalRecords.value = arrivalRecords.value.filter(
            record => now - record.timestamp < duplicateTimeout
        );
    };

    // 检查是否已上报过（防止短时间内重复上报）
    const hasReportedRecently = (vehicleId: number, poiId: number): boolean => {
        if (!preventDuplicateReports) return false;

        cleanupExpiredRecords();
        return arrivalRecords.value.some(
            record => record.vehicleId === vehicleId && record.oiId === poiId
        );
    };

    // 记录到达事件
    const recordArrival = (vehicleId: number, poiId: number) => {
        arrivalRecords.value.push({
            vehicleId,
            poiId,
            timestamp: Date.now()
        });
    };

    // 上报车辆到达事件
    const reportArrival = async (vehicle: VehiclePosition, poi: POI) => {
        if (hasReportedRecently(vehicle.vehicleId, poi.id)) {
            console.log(`车辆 ${vehicle.licensePlate || vehicle.vehicleId} 已在最近到达过 POI ${poi.name}，跳过上报`);
            return;
        }

        try {
            const arrivalData = {
                vehicleId: vehicle.vehicleId,
                poiId: poi.id,
                arrivalTime: new Date().toISOString(),
                actualLongitude: vehicle.lng,
                actualLatitude: vehicle.lat,
                triggerRadius: poi.radius || arrivalThreshold
            };

            console.log(`上报车辆到达事件: 车辆 ${vehicle.vehicleId} 到达 POI ${poi.name}`);

            const result = await vehicleArrivalApi.reportVehicleArrival(arrivalData);

            if (result.success) {
                console.log(`车辆到达事件上报成功: ${result.message}`);
                recordArrival(vehicle.vehicleId, poi.id);
            } else {
                console.warn(`车辆到达事件上报失败: ${result.message}`);
            }
        } catch (error) {
            console.error('上报车辆到达事件时发生错误:', error);
        }
    };

    // 检查车辆是否到达任何POI点
    const checkVehicleArrival = (vehicle: VehiclePosition, poiList: POI[]) => {
        for (const poi of poiList) {
            const threshold = poi.radius || arrivalThreshold;

            if (isVehicleArrived(vehicle, poi, threshold)) {
                reportArrival(vehicle, poi);
                break; // 一次只处理一个到达事件
            }
        }
    };

    // 开始监控
    const startMonitoring = (
        getVehiclePositions: () => VehiclePosition[],
        getPOIList: () => POI[]
    ) => {
        if (isMonitoring.value) {
            console.warn('车辆到达监控已在运行中');
            return;
        }

        isMonitoring.value = true;
        console.log(`开始车辆到达监控，检查间隔: ${checkInterval}ms`);

        monitoringTimer.value = setInterval(() => {
            const vehicles = getVehiclePositions();
            const pois = getPOIList();

            if (vehicles.length === 0 || pois.length === 0) {
                return;
            }

            // 检查每辆车是否到达任何POI
            vehicles.forEach(vehicle => {
                checkVehicleArrival(vehicle, pois);
            });
        }, checkInterval);
    };

    // 停止监控
    const stopMonitoring = () => {
        if (monitoringTimer.value) {
            clearInterval(monitoringTimer.value);
            monitoringTimer.value = null;
        }
        isMonitoring.value = false;
        console.log('车辆到达监控已停止');
    };

    // 组件卸载时自动清理
    onUnmounted(() => {
        stopMonitoring();
    });

    return {
        isMonitoring,
        startMonitoring,
        stopMonitoring
    };
}