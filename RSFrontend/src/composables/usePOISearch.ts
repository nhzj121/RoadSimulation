// composables/usePOISearch.ts
import {ref} from "vue";

export function usePOISearch(map: any, AMap: any) {
    const poiData = ref<any[]>([])
    const searchProgress = ref({ total: 0, completed: 0 })

    const searchByKeyword = async (keyword: string, category: string) => {
        // POI搜索逻辑
    }

    const clearResults = () => {
        poiData.value = []
    }

    return {
        poiData,
        searchProgress,
        searchByKeyword,
        clearResults
    }
}