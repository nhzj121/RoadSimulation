// composables/useVehicles.ts
import { ref, reactive, computed } from 'vue'

export function useVehicles() {
    const statusMap = {
        running: { text: '运输中', color: '#2ecc71' },
        loading: { text: '装卸货', color: '#f39c12' },
        maintenance: { text: '保养中', color: '#e74c3c' },
        stopped: { text: '停靠中', color: '#95a5a6' },
    }

    const vehicles = reactive([])

    const addVehicle = (vehicle) => {
        vehicles.push({
            id: vehicle.id,
            location: vehicle.location,
            status: vehicle.status || 'stopped',
            ...vehicle
        })
    }

    const updateVehicleStatus = (vehicleId, status) => {
        const vehicle = vehicles.find(v => v.id === vehicleId)
        if (vehicle && statusMap[status]) {
            vehicle.status = status
        }
    }

    const removeVehicle = (vehicleId) => {
        const index = vehicles.findIndex(v => v.id === vehicleId)
        if (index !== -1) {
            vehicles.splice(index, 1)
        }
    }

    const runningVehicleCount = computed(() => {
        return vehicles.filter(v => v.status === 'running').length
    })

    const getVehicleStatus = (status) => {
        return statusMap[status] || { text: '未知', color: '#95a5a6' }
    }

    return {
        vehicles,
        statusMap,
        runningVehicleCount,
        addVehicle,
        updateVehicleStatus,
        removeVehicle,
        getVehicleStatus
    }
}