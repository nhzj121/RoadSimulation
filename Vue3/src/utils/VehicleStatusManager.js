// src/utils/VehicleStatusManager.js
export class VehicleStatusManager {
    constructor(vehiclesRef, mapRef) {
        this.vehicles = vehiclesRef; // Vue reactive 数组的引用
        this.map = mapRef; // 地图实例
        this.vehicleMarkers = new Map(); // 车辆ID -> 标记映射
        this.assignmentData = new Map(); // 车辆ID -> Assignment数据
        this.statusCallbacks = []; // 状态变化回调
    }

    /**
     * 注册车辆标记到管理器
     */
    registerVehicleMarker(vehicleId, marker, assignmentData = null) {
        this.vehicleMarkers.set(vehicleId, marker);
        if (assignmentData) {
            this.assignmentData.set(vehicleId, assignmentData);
        }

        // 设置初始状态
        const vehicle = this.vehicles.find(v => v.id === vehicleId);
        if (vehicle && vehicle.status) {
            this.updateVehicleIcon(vehicleId, vehicle.status);
        }
    }

    /**
     * 更新车辆状态（核心方法）
     */
    updateVehicleStatus(vehicleId, status, additionalData = {}) {
        console.log(`[VehicleStatusManager] 更新车辆状态: ${vehicleId} -> ${status}`, additionalData);

        // 1. 更新车辆列表中的状态
        const vehicleIndex = this.vehicles.findIndex(v => v.id === vehicleId);
        if (vehicleIndex !== -1) {
            const vehicle = this.vehicles[vehicleIndex];
            const oldStatus = vehicle.status;

            // 保存旧状态用于回调
            vehicle.previousStatus = oldStatus;
            vehicle.status = status;

            // 2. 更新载重信息
            this.updateVehicleLoadInfo(vehicle, status, additionalData);

            // 3. 更新位置信息（如果有提供）
            if (additionalData.position) {
                vehicle.currentLongitude = additionalData.position[0];
                vehicle.currentLatitude = additionalData.position[1];
            }

            // 4. 更新车辆图标
            this.updateVehicleIcon(vehicleId, status);

            // 5. 触发状态变化回调
            this.triggerStatusChange(vehicleId, oldStatus, status, vehicle);

            console.log(`[VehicleStatusManager] 车辆 ${vehicle.licensePlate} 状态已更新: ${oldStatus} -> ${status}`);
            return true;
        } else {
            console.warn(`[VehicleStatusManager] 车辆ID ${vehicleId} 未找到`);
            return false;
        }
    }

    /**
     * 更新车辆载重信息
     */
    updateVehicleLoadInfo(vehicle, status, data) {
        const assignment = data.assignment || this.assignmentData.get(vehicle.id);

        switch (status) {
            case 'ORDER_DRIVING':
                // 前往装货点：空车
                vehicle.currentLoad = 0;
                vehicle.currentVolume = 0;
                vehicle.loadPercentage = 0;
                vehicle.volumePercentage = 0;
                vehicle.actionDescription = `前往装货点: ${assignment?.startPOIName || '未知'}`;
                break;

            case 'LOADING':
                // 装货中：载重逐渐增加
                vehicle.actionDescription = `正在装货...`;
                // 在实际应用中，这里可以模拟装货过程
                break;

            case 'TRANSPORT_DRIVING':
                // 运输中：满载
                if (assignment) {
                    vehicle.currentLoad = assignment.currentLoad || 0;
                    vehicle.currentVolume = assignment.currentVolume || 0;

                    if (vehicle.maxLoadCapacity > 0) {
                        vehicle.loadPercentage = Math.min(100,
                            (vehicle.currentLoad / vehicle.maxLoadCapacity) * 100);
                    }

                    if (vehicle.maxVolumeCapacity > 0) {
                        vehicle.volumePercentage = Math.min(100,
                            (vehicle.currentVolume / vehicle.maxVolumeCapacity) * 100);
                    }
                }
                vehicle.actionDescription = `运输至: ${assignment?.endPOIName || '未知'}`;
                break;

            case 'UNLOADING':
                // 卸货中：载重逐渐减少
                vehicle.actionDescription = `正在卸货...`;
                break;

            case 'WAITING':
            case 'IDLE':
                // 等待/空闲：空车
                vehicle.currentLoad = 0;
                vehicle.currentVolume = 0;
                vehicle.loadPercentage = 0;
                vehicle.volumePercentage = 0;
                vehicle.actionDescription = '等待任务';
                break;

            case 'BREAKDOWN':
                // 故障：保持当前载重
                vehicle.actionDescription = '车辆故障';
                break;
        }
    }

    /**
     * 更新车辆图标
     */
    updateVehicleIcon(vehicleId, status) {
        const marker = this.vehicleMarkers.get(vehicleId);
        if (!marker) {
            console.warn(`[VehicleStatusManager] 车辆ID ${vehicleId} 的标记未找到`);
            return;
        }

        // 获取车辆信息以确定颜色
        const vehicle = this.vehicles.find(v => v.id === vehicleId);
        let color = null;

        // 使用状态映射中的颜色，如果没有则使用车辆默认颜色
        const statusColors = {
            'IDLE': '#95a5a6',
            'ORDER_DRIVING': '#3498db',
            'LOADING': '#f39c12',
            'TRANSPORT_DRIVING': '#2ecc71',
            'UNLOADING': '#e74c3c',
            'WAITING': '#e74c3c',
            'BREAKDOWN': '#e74c3c'
        };

        color = statusColors[status] || (vehicle?.color || '#ff7f50');

        // 创建新图标
        const newIcon = createVehicleIcon(32, status, color);

        // 更新标记
        marker.setContent(newIcon);

        // 更新标记标题
        const newTitle = `${vehicle?.licensePlate || '车辆'} - ${this.getStatusText(status)}`;
        marker.setTitle(newTitle);

        console.log(`[VehicleStatusManager] 车辆ID ${vehicleId} 图标已更新: ${status}`);
    }

    /**
     * 获取状态文本描述
     */
    getStatusText(status) {
        const statusMap = {
            'IDLE': '空闲',
            'ORDER_DRIVING': '前往装货点',
            'LOADING': '装货中',
            'TRANSPORT_DRIVING': '运输中',
            'UNLOADING': '卸货中',
            'WAITING': '等待中',
            'BREAKDOWN': '故障'
        };
        return statusMap[status] || status;
    }

    /**
     * 添加状态变化回调
     */
    onStatusChange(callback) {
        this.statusCallbacks.push(callback);
    }

    /**
     * 触发状态变化回调
     */
    triggerStatusChange(vehicleId, oldStatus, newStatus, vehicle) {
        this.statusCallbacks.forEach(callback => {
            try {
                callback(vehicleId, oldStatus, newStatus, vehicle);
            } catch (error) {
                console.error('状态变化回调执行失败:', error);
            }
        });
    }

    /**
     * 获取车辆当前状态
     */
    getVehicleStatus(vehicleId) {
        const vehicle = this.vehicles.find(v => v.id === vehicleId);
        return vehicle?.status || 'UNKNOWN';
    }

    /**
     * 获取车辆详细信息（包含位置和载重）
     */
    getVehicleInfo(vehicleId) {
        const vehicle = this.vehicles.find(v => v.id === vehicleId);
        if (!vehicle) return null;

        const assignment = this.assignmentData.get(vehicleId);
        return {
            ...vehicle,
            assignment,
            statusText: this.getStatusText(vehicle.status),
            position: vehicle.currentLongitude && vehicle.currentLatitude ?
                [vehicle.currentLongitude, vehicle.currentLatitude] : null
        };
    }

    /**
     * 清理资源
     */
    cleanup() {
        this.vehicleMarkers.clear();
        this.assignmentData.clear();
        this.statusCallbacks = [];
    }
}