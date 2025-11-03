// composables/usePOIManagement.ts
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { poiManagerApi } from '../api/poiManagerApi'

export function usePOIManagement(mapContext) {
    // POI 数据状态
    const poiData = reactive({
        factory: [],
        warehouse: [],
        gasStation: [],
        maintenance: [],
        restArea: [],
        transport: []
    })
    // 详细的POI分类配置
    const poiCategories = ref([
        {
            name: 'factory',
            label: '工厂',
            types: ['170300'],
            keywords: ['工厂'],//, '工业园', '加工厂'
            visible: true
        },
        {
            name: 'warehouse',
            label: '仓库',
            types: ['070501'],
            keywords: ['仓库'],//, '物流园', '仓储'
            visible: true
        },
        {
            name: 'gasStation',
            label: '加油站',
            types: ['010100'],
            keywords: ['加油站'],//, '中国石油', '中国石化'
            visible: true
        },
        {
            name: 'maintenance',
            label: '维修中心',
            types: ['035000'],
            keywords: ['货车维修'],
            visible: true
        },
        {
            name: 'restArea',
            label: '休息区',
            types: ['180300'],
            keywords: ['休息区'],//'服务区',
            visible: true
        },
        {
            name: 'transport',
            label: '运输中心',
            types: ['070500', '150107', '150210'],
            keywords: ['配送中心'],//, '物流'
            visible: true
        }
    ]);

    // 搜索状态
    const searchProgress = ref({
        total: 0,
        completed: 0,
        currentCategory: '',
        currentKeyword: ''
    })

    const isSearching = ref(false)
    const loadingData = ref(false)
    const loadProgress = ref(0)
    const showTypeMappingWarning = ref(false)

    // 类型映射
    const typeMapping = {
        'factory': 'FACTORY',
        'warehouse': 'WAREHOUSE',
        'gasStation': 'GAS_STATION',
        'maintenance': 'MAINTENANCE_CENTER',
        'restArea': 'REST_AREA',
        'transport': 'DISTRIBUTION_CENTER'
    }

    const reverseTypeMapping = {
        'FACTORY': 'factory',
        'WAREHOUSE': 'warehouse',
        'GAS_STATION': 'gasStation',
        'MAINTENANCE_CENTER': 'maintenance',
        'REST_AREA': 'restArea',
        'DISTRIBUTION_CENTER': 'transport'
    }

    // 计算属性
    const totalPOICount = computed(() => {
        return Object.values(poiData).reduce((sum, pois) => sum + pois.length, 0)
    })

    const getCategoryCount = (categoryName) => {
        return poiData[categoryName]?.length || 0
    }

    // 数据操作方法
    const classifyPOIData = (pois) => {
        console.group('🔍 POI数据分类过程')

        // 清空现有数据
        Object.keys(poiData).forEach(key => {
            poiData[key] = []
        })

        let classifiedCount = 0
        let unclassifiedCount = 0

        pois.forEach(poi => {
            const categoryKey = poi.category
            if (categoryKey && poiData[categoryKey] !== undefined) {
                poiData[categoryKey].push(poi)
                classifiedCount++
            } else {
                unclassifiedCount++
            }
        })

        console.log(`总计: 已分类 ${classifiedCount} 个, 未分类 ${unclassifiedCount} 个`)
        console.groupEnd()
    }

    const convertDBDataToFrontend = (dbData) => {
        console.group('🔄 数据转换过程')
        const convertedPOIs = dbData.map((item) => {
            let frontendCategory = 'unknown'
            const normalizedType = item.type.toUpperCase().trim()

            if (reverseTypeMapping[normalizedType]) {
                frontendCategory = reverseTypeMapping[normalizedType]
            }

            return {
                id: item.id.toString(),
                name: item.name,
                type: item.type,
                location: { lng: item.longitude, lat: item.latitude },
                address: item.address,
                tel: item.tel || '',
                category: frontendCategory
            }
        })

        console.log(`转换完成: ${convertedPOIs.length} 条记录`)
        console.groupEnd()

        return convertedPOIs
    }

    const convertFrontendDataToDB = (frontendData) => {
        return frontendData.map(poi => {
            const backendType = typeMapping[poi.category] || 'UNKNOWN'
            return {
                id: poi.id,
                name: poi.name,
                type: backendType,
                longitude: poi.location.lng,
                latitude: poi.location.lat,
                address: poi.address,
                tel: poi.tel
            }
        })
    }

    // API 操作方法
    const loadDataFromBackend = async () => {
        if (!mapContext?.value) {
            ElMessage.warning('地图未初始化')
            return
        }

        loadingData.value = true
        loadProgress.value = 0
        const startTime = Date.now()
        showTypeMappingWarning.value = false

        try {
            ElMessage.info('开始从数据库加载POI数据...')

            const progressInterval = setInterval(() => {
                if (loadProgress.value < 90) {
                    loadProgress.value += 10
                }
            }, 200)

            const poisFromDB = await poiManagerApi.getAll()
            clearInterval(progressInterval)
            loadProgress.value = 100

            if (poisFromDB && poisFromDB.length > 0) {
                const convertedPOIs = convertDBDataToFrontend(poisFromDB)
                classifyPOIData(convertedPOIs)
                ElMessage.success(`成功加载 ${convertedPOIs.length} 个POI数据`)
            } else {
                ElMessage.info('数据库中没有POI数据')
            }
        } catch (error) {
            console.error('加载POI数据失败:', error)
            ElMessage.error('加载POI数据失败，请检查网络连接')
        } finally {
            loadingData.value = false
            loadProgress.value = 0
        }
    }

    const saveToBackend = async () => {
        try {
            const allPOIs = Object.values(poiData).flat()
            if (allPOIs.length === 0) {
                ElMessage.warning('没有数据可保存')
                return
            }

            const poisToSave = convertFrontendDataToDB(allPOIs)

            await ElMessageBox.confirm(
                `确定要保存 ${allPOIs.length} 个POI数据到数据库吗？`,
                '确认保存',
                {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }
            )

            ElMessage.info('正在保存数据...')
            const result = await poiManagerApi.batchSave(poisToSave)

            if (result.success) {
                ElMessage.success(result.message || 'POI数据保存成功')
            } else {
                ElMessage.error(`保存失败: ${result.message}`)
            }
        } catch (error) {
            console.error('保存POI数据时发生错误:', error)
            ElMessage.error('保存POI数据时发生错误: ' + (error as Error).message)
        }
    }

    // 搜索方法
    const smartBatchPOISearch = async () => {
        if (!mapContext?.value) {
            ElMessage.warning('地图未初始化')
            return
        }

        isSearching.value = true
        const allPOIs = []

        // 计算总任务数
        const totalTasks = poiCategories.value.reduce((sum, category) => sum + category.keywords.length, 0)
        let completedTasks = 0

        searchProgress.value = {
            total: totalTasks,
            completed: completedTasks,
            currentCategory: '',
            currentKeyword: ''
        }

        try {
            // 搜索逻辑实现...
            // 这里可以进一步拆分搜索逻辑到单独的 Composable
            console.log('开始POI搜索...')

            // 模拟搜索过程
            await new Promise(resolve => setTimeout(resolve, 2000))

            ElMessage.success('POI搜索完成')
        } catch (error) {
            console.error('POI搜索失败:', error)
            ElMessage.error('POI搜索失败')
        } finally {
            isSearching.value = false
            searchProgress.value.currentCategory = ''
            searchProgress.value.currentKeyword = ''
        }
    }

    // 分类可见性控制
    const onCategoryVisibilityChange = (category) => {
        console.log(`切换 ${category.label} 可见性:`, category.visible)
    }

    const showAllCategories = () => {
        poiCategories.value.forEach(cat => cat.visible = true)
        ElMessage.success('已显示所有分类')
    }

    const hideAllCategories = () => {
        poiCategories.value.forEach(cat => cat.visible = false)
        ElMessage.info('已隐藏所有分类')
    }

    const clearAllData = () => {
        Object.keys(poiData).forEach(key => {
            poiData[key] = []
        })
        ElMessage.info('已清空所有数据')
    }

    const exportPOIData = () => {
        const allPOIs = Object.values(poiData).flat()
        const dataStr = JSON.stringify(allPOIs, null, 2)
        const dataBlob = new Blob([dataStr], { type: 'application/json' })

        const link = document.createElement('a')
        link.href = URL.createObjectURL(dataBlob)
        link.download = `poi_data_${new Date().getTime()}.json`
        link.click()

        ElMessage.success('数据导出成功')
    }

    return {
        // 状态
        poiData,
        poiCategories,
        searchProgress,
        isSearching,
        loadingData,
        loadProgress,
        showTypeMappingWarning,

        // 计算属性
        totalPOICount,

        // 方法
        getCategoryCount,
        classifyPOIData,
        loadDataFromBackend,
        saveToBackend,
        smartBatchPOISearch,
        onCategoryVisibilityChange,
        showAllCategories,
        hideAllCategories,
        clearAllData,
        exportPOIData
    }
}
