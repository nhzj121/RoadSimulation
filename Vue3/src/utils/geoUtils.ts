/**
 * 计算两个经纬度坐标点之间的距离（Haversine公式）
 * @param lon1 点1经度
 * @param lat1 点1纬度
 * @param lon2 点2经度
 * @param lat2 点2纬度
 * @returns 距离（米）
 */
export function calculateDistance(lon1: number, lat1: number, lon2: number, lat2: number): number {
    const R = 6371000; // 地球半径（米）
    const toRad = (degree: number) => degree * Math.PI / 180;

    const φ1 = toRad(lat1);
    const φ2 = toRad(lat2);
    const Δφ = toRad(lat2 - lat1);
    const Δλ = toRad(lon2 - lon1);

    const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
        Math.cos(φ1) * Math.cos(φ2) *
        Math.sin(Δλ/2) * Math.sin(Δλ/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

    return R * c; // 返回距离（米）
}

/**
 * 判断车辆是否到达POI点
 * @param vehiclePos 车辆位置 {lng: number, lat: number}
 * @param poi POI点位置 {longitude: number, latitude: number}
 * @param threshold 到达阈值（米），默认50米
 * @returns 是否到达
 */
export function isVehicleArrived(
    vehiclePos: {lng: number, lat: number},
    poi: {longitude: number, latitude: number},
    threshold: number = 50
): boolean {
    const distance = calculateDistance(
        vehiclePos.lng,
        vehiclePos.lat,
        poi.longitude,
        poi.latitude
    );
    return distance <= threshold;
}