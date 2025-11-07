// composables/useSimulation.ts
import { ref, computed } from 'vue'

export function useSimulation() {
    const speedFactor = ref(1)
    const isSimulationRunning = ref(false)

    const setSpeed = (val) => {
        speedFactor.value = val
    }

    const decSpeed = () => {
        speedFactor.value = Math.max(0.5, speedFactor.value - 0.5)
    }

    const incSpeed = () => {
        speedFactor.value = Math.min(5, speedFactor.value + 0.5)
    }

    const startSimulation = () => {
        isSimulationRunning.value = true
        console.log("开始仿真")
    }

    const resetSimulation = () => {
        isSimulationRunning.value = false
        speedFactor.value = 1
        console.log("重置仿真")
    }

    const simulationStatus = computed(() => {
        return isSimulationRunning.value ? 'running' : 'stopped'
    })

    return {
        speedFactor,
        isSimulationRunning,
        simulationStatus,
        setSpeed,
        decSpeed,
        incSpeed,
        startSimulation,
        resetSimulation
    }
}