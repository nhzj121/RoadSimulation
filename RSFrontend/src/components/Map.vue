<template>
  <div class="map-wrapper">
    <div id="panel"></div>
    <div style="position: absolute;top: 10px;height: 40px;left: 100px;width: 1500px;z-index: 999; display: flex">
      <el-input v-model="searchText" placeholder="请输入搜索关键字" id="searchInputId"/>


      <el-input v-model="startName" style="margin-right: 10px" placeholder="起始地点"></el-input>
      <el-input id="end" v-model="endName" style="margin-right: 10px" placeholder="目的地点"></el-input>
      <el-select v-model="rule">
        <el-option
            v-for="item in routeRules"
            :key="item.value"
            :label="item.label"
            :value="item.value"
            placeholder="请选择驾车算路策略"
        />
      </el-select>
      <el-button @click="goView" style="margin-left: 10px">搜索</el-button>
    </div>
    <div
        style="height: 400px; width: 600px; position: absolute; top: 100px; z-index: 9999; border: 1px springgreen solid"
        v-if="routeArr.length > 0">
      路线规划策略： {{ policy }}
      <el-row
          v-for="(item, index) in routeArr"
          :key="index"
      >
        {{ item.instruction }}
      </el-row>
    </div>
    <div id="amap"></div>

    <div class="draw-area">
      <div class="select">
        <div style="margin: 10px 0">
          <el-button @click="drawOther">绘制多边形</el-button>
          <el-button @click="drawRect">绘制矩形</el-button>
          <el-button @click="drawCircle">绘制圆形</el-button>
          <el-button @click="stopDrawAndClear()">清除</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, ref} from "vue";
import AMapLoader from "@amap/amap-jsapi-loader";
import logo from "@/assets/vue.svg"
import forklift from "@/assets/forklift.png";


window._AMapSecurityConfig = {
  securityJsCode: "1e509f712466954fa73569505d3b3091",
};
let map: any = null
let placeSearch,
    AMapObj: any,
    geocoder: { getAddress: (arg0: any, arg1: (status: any, result: any) => void) => void; }
let mouseTool: any = null
let circleId = 1
let auto = ref<any>(null)
let autoOptions = {
  input: 'searchInputId',
}
let searchText = ref('')
let startName = ref('')
let endName = ref('')
let rule = ref('')
const routeRules = [
  {
    value: 0,
    label: '速度优先',
  },
  {
    value: 1,
    label: '费用优先',
  },
  {
    value: 2,
    label: '距离优先',
  },
  {
    value: 41,
    label: '躲避拥堵＋少收费',
  },
  {
    value: 32,
    label: '默认',
  },
]

let routeArr = ref([])
let policy = ref('')

const overlayGroup = ref()

const carMarker = ref() // 叉车点标记
const deviceObj = ref<{[k:string]: any} | undefined | null >({})

const getTrackData = () => {
  console.log('获取轨迹数据');
  // 这里可以添加获取轨迹数据的逻辑
  // 例如从 API 获取数据或处理现有的 trackData
}

onMounted(() => {
  initMap();

});

async function initMap() {
  await AMapLoader.load({
    key: '18bf418b01dc2f23be5905204b09e32c',
    version: '2.0',
    plugins:
        ['AMap.ToolBar',
          'AMap.Scale',
          'AMap.Geolocation',
          'AMap.PlaceSearch',
          'AMap.Geocoder',
          'AMap.HawkEye',
          'AMap.MapType',
          'AMap.CircleEditor',
          'AMap.MouseTool',
          'AMap.AutoComplete',
          'AMap.PlaceSearch',
          'AMap.Driving'
        ],
  }).then(AMap => {
        AMapObj = AMap
        map = new AMap.Map('amap', {
          zoom: 15,
          resizeEnable: true,
          center: [104.06585, 30.657361], //中心点坐标
          viewMode: "3D",
        })

    getTrackData()

        // 贴图 
        createMyTexture(map)


        // 图层切换
        const mapType = new AMap.MapType()
        // 鹰眼
        const hawkEye = new AMap.HawkEye()
        // 缩放条
        const toolbar = new AMap.ToolBar({
          position: 'LT'
        })
        // 比例尺
        const scale = new AMap.Scale()
        // 定位
        const geolocation = new AMap.Geolocation({
          enableHighAccuracy: true,//是否使用高精度定位，默认:true
          timeout: 10000,          //超过10秒后停止定位，默认：无穷大
          maximumAge: 0,           //定位结果缓存0毫秒，默认：0
          convert: true,           //自动偏移坐标，偏移后的坐标为高德坐标，默认：true
          showButton: true,        //显示定位按钮，默认：true
          buttonPosition: 'LB',    //定位按钮停靠位置，默认：'LB'，左下角
          buttonOffset: new AMap.Pixel(10, 20),//定位按钮与设置的停靠位置的偏移量，默认：Pixel(10, 20)
          showMarker: true,        //定位成功后在定位到的位置显示点标记，默认：true
          showCircle: true,        //定位成功后用圆圈表示定位精度范围，默认：true
          panToLocation: true,     //定位成功后将定位到的位置作为地图中心点，默认：true
          zoomToAccuracy: true      //定位成功后调整地图视野范围使定位位置及精度范围视野内可见，默认：false
        });
        geocoder = new AMap.Geocoder({
          city: '全国',
        });
        map.addControl(geolocation)  // 定位组件
        map.addControl(toolbar)  // 放大缩小组件
        map.addControl(scale)  // 比例尺
        map.addControl(hawkEye)  // 鹰眼
        map.addControl(mapType)  // 图层切换

        
        poiSearch()

        bindEvent()

        // 点标记
        createMarker()

        // 覆盖物
        mouseTool = new AMap.MouseTool(map); //创建鼠标工具插件实例


        // 区域高亮
        var distProvince = new AMap.DistrictLayer.Province({
          zIndex: 10, //设置图层层级
          zooms: [2, 15], //设置图层显示范围
          // adcode: "620000", //设置行政区 adcode 所有省份的adcode前往utils/adcode.txt查看
          adcode: "340000", // 安徽
          depth: 0, //设置数据显示层级，0：显示国家面，1：显示省级，当国家为中国时设置depth为2的可以显示市一级
        });

        // 设置行政区图层样式
        distProvince.setStyles({
          "stroke-width": 2, //描边线宽
          "province-stroke": "#ff714d", //描边颜色
          fill: "rgba(0,0,0,0.3)",
        });
        map.add(distProvince);



    // 添加插件
    AMap.plugin('AMap.MoveAnimation', function(){

      overlayGroup.value = new AMap.OverlayGroup()
      overlayGroup.value.show()

      var icon = new AMap.Icon({
        size: new AMap.Size(25, 25), // 图标尺寸
        image: forklift, // Icon的图像
        imageOffset: new AMap.Pixel(0, 0), // 图像相对展示区域的偏移量，适于雪碧图等
        imageSize: new AMap.Size(30, 30)
      });

      carMarker.value = new AMap.Marker({
        position: [103.7, 36.5], // 经纬度对象，也可以是经纬度构成的一维数组[116.39, 39.9]
        title: '叉车',
        zIndex: 999,
        icon: icon,
        anchor:'bottom-center',
        label: {
          direction:'bottom',
          offset: new AMap.Pixel(0, 5),  //设置文本标注偏移量
          content: "<div class='info'>我是 marker 的 label 标签</div>", //设置文本标注内容
          style: {
            'transform': 'rotate(0)'
          }
        },
        offset: new AMap.Pixel(-15, -25)
      })
      carMarker.value.on("click", function (e:any) {
        console.log("你点击了Marker", e);
      });
      carMarker.value.hide()
      carMarker.value.addTo(map.value)
      map.value?.add(overlayGroup.value)
    });

      }
  );
}

// 贴图
const createMyTexture = (map: any) => {
  let imageLayer: any = null
  let layers = []
  let bounds = new AMapObj.Bounds(
      [103.691881, 36.487252],  //左下角
      [103.716512, 36.500069]  //右上角
  )
  // 图片地图配置
  imageLayer = new AMapObj.ImageLayer({
    url: 'src/assets/ls-bg.png',
    bounds,
    zoom: 15,//级别
  });

  layers.push(imageLayer)
  // 添加贴图
  map.add(layers);
}

// 搜索
const poiSearch = () => {
  auto.value = new AMapObj.AutoComplete(autoOptions)
  const placeSearch = new AMapObj.PlaceSearch({
    map: map,
    city: '', // 减小搜索范围至某个城市
    pageSize: 5, // 单页显示结果条数
    pageIndex: 1, // 页码
    citylimit: false, // 是否强制限制在设置的城市内搜索
    autoFitView: true,
    panel: "panel", // 结果列表将在此容器中进行展示。出不来时设置样式z-inde: 999
  })
  auto.value.on("select", function (e:any) {
    //针对选中的poi实现自己的功能
    placeSearch.search(e.poi.name);
  });
}

// 鼠标双击事件
const doubleClick = (e:any) => {
  console.log('您在 [ ' + e.lnglat.getLng() + ',' + e.lnglat.getLat() + ' ] 的位置双击了地图！')
  const x = e.lnglat.getLng()
  const y = e.lnglat.getLat()
  showInfo(e, x, y)
}

// 鼠标移动事件
const showInfoMove = () => {
  console.log('您移动了您的鼠标！')
}

// 鼠标点击事件
const signClick = (e:any) => {
  console.log('您在 [ ' + e.lnglat.getLng() + ',' + e.lnglat.getLat() + ' ] 的位置单击了地图！')
  const x = e.lnglat.getLng()
  const y = e.lnglat.getLat()
  showInfo(e, x, y)
}

// 地图事件绑定
const bindEvent = () => {
  console.log("事件绑定成功")
  map.on('click', signClick);
  // map.on('dblclick', doubleClick);
  // map.on('mousemove', showInfoMove);
}

// 信息窗体
const showInfo = (e: any, x: any, y: any,) => {
  geocoder.getAddress(e.lnglat, (status, result) => {
    if (status === 'complete' && result.regeocode) {
      const address = result.regeocode.formattedAddress
      const infoContent = `
        <div style="padding:7px 0px 0px 0px;">
          <div class="global-header">当前位置</div>
          <p class='input-item'>经度: ${x}</p>
          <p class='input-item'>纬度: ${y}</p>
          <p class='input-item'>地址: ${address}</p>
          <img src="${logo}" alt="Vue Logo" width="100px;height=100px"/>
        </div>
      `
      const infoWindow = new AMapObj.InfoWindow({
        content: infoContent
      })
      infoWindow.open(map, new AMapObj.LngLat(x, y))
    } else {
      console.error('获取地址失败')
    }
  })
}

// 创建点标记
const createMarker = () => {
  //  点标记
  const markerContent =
      `<div class="custom-content-marker">
            <img src="//a.amap.com/jsapi_demos/static/demo-center/icons/dir-via-marker.png">
            <div class="close-btn">X</div>
          </div>`
  const position = new AMapObj.LngLat(103.841856, 36.053549); //Marker 经纬度
  const marker = new AMapObj.Marker({
    position: position,
    content: markerContent, //将 html 传给 content
    offset: new AMapObj.Pixel(-13, -30), //以 icon 的 [center bottom] 为原点
  });
  // 添加点标记
  map.add(marker);
  // 未关闭按钮添加点击事件
  const closeBtn = document.querySelector(".close-btn")
  if (closeBtn) {
    closeBtn.addEventListener('click', () => {
      clearMarker(marker); // 移除 marker
    });
  }
}

// 移除点标记
const clearMarker = (marker: any) => {
  console.log("移除点标记")
  map.remove(marker); //清除 marker
}


// 绘制矩形
const drawRect = () => {
  console.log('绘制矩形')
  mouseTool.rectangle({
    strokeColor: 'red',
    strokeOpacity: 0.5,
    strokeWeight: 2,
    fillColor: 'blue',
    fillOpacity: 0.2,
    strokeStyle: 'solid',
  })
}

// 绘制圆形
const drawCircle = () => {
  console.log('绘制圆形')
  mouseTool.circle({
    strokeColor: "#FF33FF",
    strokeOpacity: 1,
    strokeWeight: 6,
    fillColor: '#1791fc',
    fillOpacity: 0.4,
    // strokeStyle: 'solid',
    strokeStyle: 'dashed',
    strokeDasharray: [30, 10],
  })
}

// 绘制多边形
const drawOther = () => {
  mouseTool.polygon({
    strokeColor: "#FF33FF",
    strokeOpacity: 1,
    strokeWeight: 4,
    fillColor: '#1791fc',
    fillOpacity: 0.2,
    strokeStyle: "solid",
  })
}

/**
 * 停止绘制
 * 形参为true时，清除所有绘制的覆盖物
 * 形参为false时，仅仅停止绘制，不会清除覆盖物
 */
const stopDrawAndClear = () => {
  mouseTool.close(true)
}

// 路径规划
const goView = () => {
  // eslint-disable-next-line no-undef
  const driving = new AMapObj.Driving({
    map,
    // 驾车路线规划策略，AMap.DrivingPolicy.LEAST_TIME是最快捷模式
    // eslint-disable-next-line no-undef
    // policy: AMapObj.DrivingPolicy.LEAST_TIME  // 路线最快
    policy: rule.value  // 路线最快
  })
  const points = [
    {keyword: startName.value, city: '兰州'},
    {keyword: endName.value, city: '兰州'}
  ]

  driving.search(points, (status:any, result:any) => {
    // 未出错时，result即是对应的路线规划方案
    console.log('status=', status)
    console.log('result=', result)
    if (status === 'complete') {
      routeArr.value = result.routes[0].steps
      policy.value = result.routes[0].policy
      console.log(routeArr.value)
    }
  })
}


// 动态轨迹数据
const trackData = ref([
    [
      103.69738666739352,
      36.48936555995377
    ],
    [
      103.69738666739352,
      36.48936555995377
    ],
    [
      103.69738666739352,
      36.48936555995377
    ],
    [
      103.69717250586922,
      36.488922488183135
    ],
    [
      103.6972183941377,
      36.488949281741796
    ],
    [
      103.69675952338595,
      36.48894849427901
    ],
    [
      103.69675952338595,
      36.48894849427901
    ],
    [
      103.69675952338595,
      36.48894849427901
    ],
    [
      103.69704250182244,
      36.48909400444933
    ],
    [
      103.69704250182244,
      36.48909400444933
    ],
    [
      103.69704250182244,
      36.48909400444933
    ],
    [
      103.69704250182244,
      36.48909400444933
    ],
    [
      103.69704250182244,
      36.48909400444933
    ],
    [
      103.69704250182244,
      36.48909400444933
    ],
    [
      103.69719548790421,
      36.48965909767394
    ],
    [
      103.69719548790421,
      36.48965909767394
    ],
    [
      103.69719548790421,
      36.48965909767394
    ],
    [
      103.69719548790421,
      36.48965909767394
    ],
    [
      103.69969627064054,
      36.49002588630411
    ],
    [
      103.6996733727784,
      36.49086927902048
    ],
    [
      103.6996733727784,
      36.49086927902048
    ],
    [
      103.6996733727784,
      36.49086927902048
    ]
  ]
)


onUnmounted(() => {
  map?.destroy();
});
</script>


<style scoped lang="less">
.map-wrapper {
  #amap {
    width: 100%;
    height: 100vh;
  }
}

#panel {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 999;
}


.draw-area {
  position: absolute;
  bottom: 10px;
  right: 200px;
  display: flex;
  justify-content: center;

  .select {
    background-color: white;
    padding: 0 10px;
    margin-right: 10px;
    border: 1px solid #ccc;
    border-radius: 5px;
  }

  .action {
    display: flex;
    align-items: center;
  }
}
</style>
