// composables/useFilters.ts
import { reactive } from 'vue'

export function useFilters(initialFilters = []) {
    const filters = reactive(initialFilters)

    const toggleFilter = (key) => {
        const filter = filters.find(f => f.key === key)
        if (filter) {
            filter.checked = !filter.checked
            console.log(`筛选 ${filter.label}: ${filter.checked}`)
        }
    }

    const getActiveFilters = () => {
        return filters.filter(f => f.checked).map(f => f.key)
    }

    const resetFilters = () => {
        filters.forEach(filter => {
            filter.checked = true
        })
    }

    return {
        filters,
        toggleFilter,
        getActiveFilters,
        resetFilters
    }
}