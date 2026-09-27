export const VEHICLE_STATUS_PRESENTATIONS = Object.freeze({
  IDLE: Object.freeze({ text: '空闲', color: '#95a5a6', stopped: false }),
  ORDER_DRIVING: Object.freeze({ text: '前往装货点', color: '#3498db', stopped: false }),
  LOADING: Object.freeze({ text: '装货中', color: '#f39c12', stopped: true }),
  TRANSPORT_DRIVING: Object.freeze({ text: '运输中', color: '#2ecc71', stopped: false }),
  UNLOADING: Object.freeze({ text: '卸货中', color: '#e74c3c', stopped: true }),
  WAITING: Object.freeze({ text: '等待中', color: '#e74c3c', stopped: true }),
  BREAKDOWN: Object.freeze({ text: '故障', color: '#e74c3c', stopped: true }),
  SCRAPPED: Object.freeze({ text: '已报废', color: '#7f1d1d', stopped: true }),
  RESERVED_REPLACEMENT: Object.freeze({ text: '替换车辆准备中', color: '#7c3aed', stopped: true })
})

export function vehicleStatusPresentation(status) {
  return VEHICLE_STATUS_PRESENTATIONS[status] || { text: status || '未知', color: '#ccc', stopped: false }
}

export const isStoppedVehicleStatus = status => vehicleStatusPresentation(status).stopped
