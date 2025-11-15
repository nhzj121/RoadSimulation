// src/composables/usePOIManager.ts
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { poiManagerApi, POIFromDB } from '../api/poiManagerApi'

// 类型定义
export interface POI {
    id: string;
    name: string;
    poiType: string;
    location: { lng: number; lat: number };
    address: string;
    tel: string;
    category: string;
}

export interface POICategory {
    name: string;
    label: string;
    visible: boolean;
}

export interface UsePOIManagerOptions {
    map: any;
    AMap: any;
    onPOIClicked?: (poi: POI) => void;
    onPOILoaded?: (pois: POI[]) => void;
    onError?: (error: Error) => void;
}

// 类型映射配置
const typeMapping = {
    'factory': 'FACTORY',
    'warehouse': 'WAREHOUSE',
    'gasStation': 'GAS_STATION',
    'maintenance': 'MAINTENANCE_CENTER',
    'restArea': 'REST_AREA',
    'transport': 'DISTRIBUTION_CENTER'
} as const;

const reverseTypeMapping = {
    'FACTORY': 'factory',
    'WAREHOUSE': 'warehouse',
    'GAS_STATION': 'gasStation',
    'MAINTENANCE_CENTER': 'maintenance',
    'REST_AREA': 'restArea',
    'DISTRIBUTION_CENTER': 'transport'
} as const;

// 图标配置 - 使用相对路径
const poiIcons = {
    '工厂': {
        url: '/icons/factory.png',
        size: [22, 22] as [number, number],
        anchor: 'bottom-center' as const,
        color: '#FF6B6B'
    },
    '仓库': {
        url: '/icons/warehouse.png',
        size: [22, 22] as [number, number],
        anchor: 'bottom-center' as const,
        color: '#4ECDC4'
    },
    '加油站': {
        url: '/icons/gas-station.png',
        size: [22, 22] as [number, number],
        anchor: 'bottom-center' as const,
        color: '#FFD166'
    },
    '维修中心': {
        url: '/icons/maintenance-center.png',
        size: [22, 22] as [number, number],
        anchor: 'bottom-center' as const,
        color: '#06D6A0'
    },
    '休息区': {
        url: '/icons/rest-area.png',
        size: [22, 22] as [number, number],
        anchor: 'bottom-center' as const,
        color: '#8f11b2'
    },
    '运输中心': {
        url: '/icons/distribution-center.png',
        size: [22, 22] as [number, number],
        anchor: 'bottom-center' as const,
        color: '#073B4C'
    }
};

export function usePOIManager(options: UsePOIManagerOptions) {
    const { map, AMap, onPOIClicked, onPOILoaded, onError } = options

    // 响应式状态
    const poiMarkers = ref<any[]>([])
    const poiData = ref<Record<string, POI[]>>({
        factory: [],
        warehouse: [],
        gasStation: [],
        maintenance: [],
        restArea: [],
        transport: []
    })

    const isLoading = ref(false)
    const isLoaded = ref(false)

    // POI 分类配置
    const poiCategories = ref<POICategory[]>([
        { name: 'factory', label: '工厂', visible: true },
        { name: 'warehouse', label: '仓库', visible: true },
        { name: 'gasStation', label: '加油站', visible: true },
        { name: 'maintenance', label: '维修中心', visible: true },
        { name: 'restArea', label: '休息区', visible: true },
        { name: 'transport', label: '运输中心', visible: true }
    ])

    // 计算属性
    const totalPOICount = computed(() => {
        return Object.values(poiData.value).reduce((sum, pois) => sum + pois.length, 0)
    })

    const visiblePOICount = computed(() => {
        return poiCategories.value
            .filter(cat => cat.visible)
            .reduce((sum, cat) => sum + getCategoryCount(cat.name), 0)
    })

    // 核心方法：从后端加载可展示的 POI 数据
    const loadPOIsAbleToShow = async (): Promise<POI[]> => {
        if (!map || !AMap) {
            const error = new Error('地图未初始化')
            onError?.(error)
            throw error
        }

        try {
            isLoading.value = true
            console.log('开始从后端加载可展示的 POI 数据...')

            // 调用后端 API 获取可展示的 POI 数据
            const poisFromDB = await poiManagerApi.getPOIAbleToShow()

            if (!poisFromDB || poisFromDB.length === 0) {
                console.log('没有可展示的 POI 数据')
                return []
            }

            // 转换数据格式
            const convertedPOIs = convertDBDataToFrontend(poisFromDB)

            // 分类存储数据
            classifyPOIData(convertedPOIs)

            // 更新地图显示
            updateMapDisplay()

            // 标记为已加载
            isLoaded.value = true

            // 触发回调
            onPOILoaded?.(convertedPOIs)

            console.log(`成功加载 ${convertedPOIs.length} 个 POI 数据`)
            ElMessage.success(`成功加载 ${convertedPOIs.length} 个 POI 点`)

            return convertedPOIs

        } catch (error) {
            console.error('加载 POI 数据失败:', error)
            const err = error instanceof Error ? error : new Error('加载 POI 数据失败')
            onError?.(err)
            ElMessage.error('加载 POI 数据失败: ' + err.message)
            throw err
        } finally {
            isLoading.value = false
        }
    }

    // 清除所有标记
    const clearMarkers = (): void => {
        if (!map) return

        poiMarkers.value.forEach(marker => {
            map.remove(marker)
        })
        poiMarkers.value = []
        console.log('已清除所有 POI 标记')
    }

    // 清除所有数据
    const clearAllData = (): void => {
        Object.keys(poiData.value).forEach(key => {
            poiData.value[key as keyof typeof poiData.value] = []
        })
        clearMarkers()
        isLoaded.value = false
        console.log('已清空所有 POI 数据')
    }

    // 更新分类可见性
    const updateCategoryVisibility = (categoryName: string, visible: boolean): void => {
        const category = poiCategories.value.find(cat => cat.name === categoryName)
        if (category) {
            category.visible = visible
            updateMapDisplay()
        }
    }

    // 显示所有分类
    const showAllCategories = (): void => {
        poiCategories.value.forEach(cat => cat.visible = true)
        updateMapDisplay()
    }

    // 隐藏所有分类
    const hideAllCategories = (): void => {
        poiCategories.value.forEach(cat => cat.visible = false)
        updateMapDisplay()
    }

    // 获取分类数量
    const getCategoryCount = (categoryName: string): number => {
        return poiData.value[categoryName as keyof typeof poiData.value]?.length || 0
    }

    // 内部方法：转换数据库数据为前端格式
    const convertDBDataToFrontend = (dbData: POIFromDB[]): POI[] => {
        if (!dbData || dbData.length === 0) {
            return []
        }

        return dbData.map((item, index) => {
            if (!item) {
                console.warn(`数据项 ${index} 为空，跳过`)
                return null
            }

            // 标准化类型处理
            let frontendCategory = 'unknown'
            const itemType = (item.poiType || '').toString().trim()
            const normalizedType = itemType.toUpperCase()

            // 类型映射
            if (normalizedType && reverseTypeMapping[normalizedType as keyof typeof reverseTypeMapping]) {
                frontendCategory = reverseTypeMapping[normalizedType as keyof typeof reverseTypeMapping]
            } else if (normalizedType === 'GASSTATION') {
                frontendCategory = 'gasStation'
            } else if (normalizedType === 'RESTAREA') {
                frontendCategory = 'restArea'
            }

            return {
                id: (item.id || `unknown-${Date.now()}-${index}`).toString(),
                name: item.name || '未知名称',
                poiType: itemType,
                location: {
                    lng: Number(item.longitude) || 0,
                    lat: Number(item.latitude) || 0
                },
                address: item.address || '未知地址',
                tel: item.tel || '',
                category: frontendCategory
            }
        }).filter(poi => poi !== null) as POI[]
    }

    // 内部方法：分类存储 POI 数据
    const classifyPOIData = (pois: POI[]): void => {
        // 清空现有数据
        Object.keys(poiData.value).forEach(key => {
            poiData.value[key as keyof typeof poiData.value] = []
        })

        // 分类存储
        pois.forEach(poi => {
            const categoryKey = poi.category
            if (categoryKey && poiData.value[categoryKey as keyof typeof poiData.value] !== undefined) {
                poiData.value[categoryKey as keyof typeof poiData.value].push(poi)
            } else {
                console.warn(`无法分类的 POI: ${poi.name} (分类: ${categoryKey})`)
            }
        })

        console.log('POI 数据分类完成:', Object.keys(poiData.value).map(key => ({
            category: key,
            count: poiData.value[key as keyof typeof poiData.value].length
        })))
    }

    // 内部方法：更新地图显示
    const updateMapDisplay = (): void => {
        if (!map || !AMap) {
            console.error('地图未就绪')
            return
        }

        clearMarkers()
        addIndividualMarkers()
    }

    // 内部方法：添加单独标记
    const addIndividualMarkers = (): void => {
        if (!map || !AMap) return

        let totalMarkers = 0

        poiCategories.value.forEach(category => {
            if (!category.visible) return

            const pois = poiData.value[category.name as keyof typeof poiData.value]
            const iconConfig = poiIcons[category.label as keyof typeof poiIcons]

            if (!iconConfig) {
                console.warn(`未找到分类 ${category.label} 的图标配置`)
                return
            }

            pois.forEach(poi => {
                try {
                    const icon = new AMap.Icon({
                        image: iconConfig.url,
                        size: new AMap.Size(iconConfig.size[0], iconConfig.size[1]),
                        imageSize: new AMap.Size(iconConfig.size[0], iconConfig.size[1]),
                        anchor: iconConfig.anchor
                    })

                    const marker = new AMap.Marker({
                        position: [poi.location.lng, poi.location.lat],
                        title: `${poi.name} - ${category.label}`,
                        icon: icon,
                        offset: new AMap.Pixel(-iconConfig.size[0] / 2, -iconConfig.size[1]),
                        extData: poi
                    })

                    marker.on('click', () => {
                        console.log(`点击标记: ${poi.name}`)
                        onPOIClicked?.(poi)
                        showPOIInfoWindow(poi, marker.getPosition())
                    })

                    map.add(marker)
                    poiMarkers.value.push(marker)
                    totalMarkers++

                } catch (error) {
                    console.error(`创建标记失败: ${poi.name}`, error)
                }
            })
        })

        console.log(`标记创建完成: 共 ${totalMarkers} 个标记`)
    }

    // 内部方法：显示 POI 信息窗口
    const showPOIInfoWindow = (poi: POI, position: any): void => {
        if (!map || !AMap) return

        const infoContent = `
      <div class="poi-info-window" style="color: #000000;">
        <div class="poi-header">
          <h4 style="color: #000000; margin: 0 0 8px 0;">${poi.name}</h4>
          <span class="poi-category" style="color: #ffffff; background-color: #409eff; padding: 2px 8px; border-radius: 4px; font-size: 12px;">${poi.category}</span>
        </div>
        <div class="poi-details" style="color: #000000; margin-top: 10px;">
          <p style="color: #000000; margin: 4px 0;"><strong style="color: #000000;">地址:</strong> ${poi.address}</p>
          ${poi.tel ? `<p style="color: #000000; margin: 4px 0;"><strong style="color: #000000;">电话:</strong> ${poi.tel}</p>` : ''}
          <p style="color: #000000; margin: 4px 0;"><strong style="color: #000000;">坐标:</strong> ${poi.location.lng.toFixed(6)}, ${poi.location.lat.toFixed(6)}</p>
        </div>
      </div>
    `

        const infoWindow = new AMap.InfoWindow({
            content: infoContent,
            offset: new AMap.Pixel(0, -30)
        })

        infoWindow.open(map, position)
    }

    // 组件卸载时清理
    onUnmounted(() => {
        clearMarkers()
    })

    // 返回公共 API
    return {
        // 状态
        poiMarkers,
        poiData,
        poiCategories,
        isLoading,
        isLoaded,

        // 计算属性
        totalPOICount,
        visiblePOICount,

        // 方法
        loadPOIsAbleToShow,
        clearMarkers,
        clearAllData,
        updateCategoryVisibility,
        showAllCategories,
        hideAllCategories,
        getCategoryCount

    }
}

export type UsePOIManagerReturn = ReturnType<typeof usePOIManager>