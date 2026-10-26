<template>
  <span class="mode-navigation-entry" :title="reasonText">
    <el-button type="primary" text :disabled="!!navigationReason" @click="navigate">{{ label }}</el-button>
  </span>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import { navigationReason, navigationMessage } from '../navigation/modeNavigation'
const props = defineProps<{to:string; label:string}>(), router = useRouter()
const reasonText = computed(() => navigationReason.value ? navigationMessage(navigationReason.value) : '')
function navigate() { if (!navigationReason.value) void router.push(props.to) }
</script>
<style scoped>.mode-navigation-entry{display:inline-flex;align-items:center}</style>
