<template>
  <div
      ref="container"
      class="virtual-scroll-container"
      :style="{ height: containerHeight + 'px' }"
      @scroll="handleScroll"
  >
    <div
        class="virtual-scroll-content"
        :style="{ height: totalHeight + 'px', transform: `translateY(${translateY}px)` }"
    >
      <div
          v-for="item in visibleItems"
          :key="getItemKey(item)"
          :style="{ height: itemHeight + 'px' }"
          class="virtual-scroll-item"
      >
        <slot
            name="item"
            :item="item"
            :index="getItemIndex(item)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';

const props = defineProps({
  items: {
    type: Array,
    required: true,
    default: () => []
  },
  itemHeight: {
    type: Number,
    default: 80
  },
  overscan: {
    type: Number,
    default: 5
  },
  getItemKey: {
    type: Function,
    default: (item, index) => item.id || index
  }
});

const emit = defineEmits(['item-click', 'scroll-end']);

// 容器引用
const container = ref(null);
const scrollTop = ref(0);
const containerHeight = ref(0);

// 计算总高度
const totalHeight = computed(() => props.items.length * props.itemHeight);

// 计算可见范围
const startIndex = computed(() => {
  return Math.max(0, Math.floor(scrollTop.value / props.itemHeight) - props.overscan);
});

const endIndex = computed(() => {
  const visibleCount = Math.ceil(containerHeight.value / props.itemHeight);
  return Math.min(
      props.items.length,
      Math.floor((scrollTop.value + containerHeight.value) / props.itemHeight) + props.overscan
  );
});

// 计算偏移量
const translateY = computed(() => {
  return startIndex.value * props.itemHeight;
});

// 获取可见项
const visibleItems = computed(() => {
  return props.items.slice(startIndex.value, endIndex.value);
});

// 获取项的实际索引
const getItemIndex = (item) => {
  const itemIndex = props.items.indexOf(item);
  return itemIndex;
};

// 处理滚动
const handleScroll = (event) => {
  if (!container.value) return;

  scrollTop.value = container.value.scrollTop;

  // 检查是否滚动到底部
  const { scrollHeight, scrollTop: currentScrollTop, clientHeight } = container.value;
  if (scrollHeight - currentScrollTop - clientHeight < 100) {
    emit('scroll-end');
  }
};

// 处理窗口大小变化
const updateContainerHeight = () => {
  if (container.value) {
    containerHeight.value = container.value.clientHeight;
  }
};

// 滚动到指定项
const scrollToItem = (index) => {
  if (!container.value) return;

  const scrollPosition = index * props.itemHeight;
  container.value.scrollTo({
    top: scrollPosition,
    behavior: 'smooth'
  });
};

// 滚动到底部
const scrollToBottom = () => {
  if (!container.value) return;

  container.value.scrollTo({
    top: container.value.scrollHeight,
    behavior: 'smooth'
  });
};

// 重置滚动位置
const resetScroll = () => {
  if (!container.value) return;

  container.value.scrollTop = 0;
  scrollTop.value = 0;
};

// 生命周期
onMounted(() => {
  updateContainerHeight();
  window.addEventListener('resize', updateContainerHeight);
});

onUnmounted(() => {
  window.removeEventListener('resize', updateContainerHeight);
});

// 监听items变化
watch(() => props.items, () => {
  // 如果添加了新项且当前在底部附近，自动滚动到底部
  if (container.value) {
    const { scrollHeight, scrollTop: currentScrollTop, clientHeight } = container.value;
    const isNearBottom = scrollHeight - currentScrollTop - clientHeight < props.itemHeight * 2;

    if (isNearBottom) {
      // 微调，确保DOM更新完成后再滚动
      setTimeout(() => {
        scrollToBottom();
      }, 50);
    }
  }
}, { deep: true });

// 暴露方法给父组件
defineExpose({
  scrollToItem,
  scrollToBottom,
  resetScroll,
  updateContainerHeight
});
</script>

<style scoped>
.virtual-scroll-container {
  width: 100%;
  overflow-y: auto;
  position: relative;
  -webkit-overflow-scrolling: touch;
}

.virtual-scroll-container::-webkit-scrollbar {
  width: 6px;
}

.virtual-scroll-container::-webkit-scrollbar-track {
  background: #f5f5f5;
  border-radius: 3px;
}

.virtual-scroll-container::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.virtual-scroll-container::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.virtual-scroll-content {
  position: relative;
  width: 100%;
}

.virtual-scroll-item {
  position: absolute;
  width: 100%;
  left: 0;
  top: 0;
  box-sizing: border-box;
}
</style>