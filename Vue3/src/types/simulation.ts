// types/simulation.ts
export interface POIPairDTO {
    pairId: string;
    startPOIId: number;
    startPOIName: string;
    startLng: number;
    startLat: number;
    startPOIType: string;

    endPOIId: number;
    endPOIName: string;
    endLng: number;
    endLat: number;
    endPOIType: string;

    goodsName: string;
    quantity: number;
    shipmentRefNo: string;

    // 扩展的运输任务信息
    assignmentId?: number;
    assignmentStatus?: string;
    assignmentCurrentActionIndex?: number;
    assignmentCreatedTime?: string;
    assignmentStartTime?: string;
    assignmentEndTime?: string;

    shipmentId?: number;
    shipmentTotalWeight?: number;
    shipmentTotalVolume?: number;
    shipmentStatus?: string;

    vehicleId?: number;
    vehicleLicensePlate?: string;
    vehicleBrand?: string;
    vehicleModelType?: string;
    vehicleCurrentLoad?: number;
    vehicleMaxLoadCapacity?: number;
    vehicleStatus?: string;
    vehicleLongitude?: number;
    vehicleLatitude?: number;

    shipmentItemId?: number;
    shipmentItemName?: string;
    shipmentItemQuantity?: number;
    shipmentItemSku?: string;
    shipmentItemTotalWeight?: number;
    shipmentItemTotalVolume?: number;
}

export interface VehicleInfo {
    id: number;
    licensePlate: string;
    brand?: string;
    modelType?: string;
    currentStatus: string;
    currentLoad: number;
    maxLoadCapacity: number;
    currentLongitude?: number;
    currentLatitude?: number;

    // 当前任务信息
    currentAssignment?: {
        id: number;
        status: string;
        route?: {
            startPOIName: string;
            endPOIName: string;
        };
        shipment?: {
            refNo: string;
            goodsName: string;
        };
    };
}