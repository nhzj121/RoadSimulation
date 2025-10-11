// 建立前端对象与数据库表字段之间的映射关系，确保数据的类型一致

// POI类型枚举（与后端POI.POIType保持一致）
export enum POIType {
    WAREHOUSE = 'WAREHOUSE',              // 仓库
    DISTRIBUTION_CENTER = 'DISTRIBUTION_CENTER',    // 配送中心
    FACTORY = 'FACTORY',                // 工厂
    GAS_STATION = 'GAS_STATION',            // 加油站
    MAINTENANCE_CENTER = 'MAINTENANCE_CENTER',     // 维修中心
    REST_AREA = 'REST_AREA'               // 休息区
}

// POI实体类型（对应后端POI实体）
export interface POI{
    id?: number;
    name: string;
    longitude: number;
    latitude: number;
    poiType: POIType;
    vehiclesAtLocation?: any[];
}

export const POITypeOptions = [
    { label: '仓库', value: POIType.WAREHOUSE },
    { label: '配送中心', value: POIType.DISTRIBUTION_CENTER },
    { label: '工厂', value: POIType.FACTORY },
    { label: '加油站', value: POIType.GAS_STATION },
    { label: '维修中心', value: POIType.MAINTENANCE_CENTER },
    { label: '休息区', value: POIType.REST_AREA }
]

// POI图标映射配置
export const POIIconMap: Record<POIType, string> = {
    [POIType.WAREHOUSE]: '/src/assets/icons/warehouse.png',
    [POIType.DISTRIBUTION_CENTER]: '/src/assets/icons/distribution-center.png',
    [POIType.FACTORY]: '/src/assets/icons/factory.png',
    [POIType.GAS_STATION]: '/src/assets/icons/gas-station.png',
    [POIType.MAINTENANCE_CENTER]: '/src/assets/icons/maintenance-center.png',
    [POIType.REST_AREA]: '/src/assets/icons/rest-area.png'
};

// POI类型颜色映射（可选，用于UI区分）
export const POIColorMap: Record<POIType, string> = {
    [POIType.WAREHOUSE]: '#ff6b6b',
    [POIType.DISTRIBUTION_CENTER]: '#4ecdc4',
    [POIType.FACTORY]: '#45b7d1',
    [POIType.GAS_STATION]: '#96ceb4',
    [POIType.MAINTENANCE_CENTER]: '#feca57',
    [POIType.REST_AREA]: '#ff9ff3'
};

// POI表单类型（用于创建和更新）
export interface POIForm {
    name: string;
    longitude: number;
    latitude: number;
    poiType: POIType;
}

// 客户实体类型（对应后端Customer实体）
export interface Customer {
    id?: number;
    code: string;
    name: string;
    contactPerson?: string;
    contactPhone?: string;
    address?: string;
    createdAt?: string;
    updatedAt?: string;
    shipments?: any[]; // 可以根据需要进一步定义Shipment类型
}

// 客户表单类型（用于创建和更新）
export interface CustomerForm {
    code: string;
    name: string;
    contactPerson?: string;
    contactPhone?: string;
    address?: string;
}

// 分页响应类型
export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
    first: boolean;
    last: boolean;
    empty: boolean;
}

// API响应包装类型
export interface ApiResponse<T = any> {
    code: number;
    message: string;
    data: T;
    timestamp: string;
}

// 错误响应类型
export interface ApiError {
    status: number;
    message: string;
    path?: string;
    timestamp?: string;
}