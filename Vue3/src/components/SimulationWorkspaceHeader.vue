<template>
  <ElHeader class="header-navbar" :class="{ 'header-navbar--data': dataView }">
    <div class="navbar-content">
      <div class="navbar-left">
        <h2 class="navbar-title" @click="$emit('home')">物流运输仿真系统</h2>
        <slot name="view" />
      </div>
      <div class="navbar-menu"><slot /></div>
    </div>
  </ElHeader>
</template>

<script setup lang="ts">
import { ElHeader } from 'element-plus'
defineProps<{ dataView?: boolean }>()
defineEmits<{ (event: 'home'): void }>()
</script>

<style scoped>
.header-navbar { background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; padding: 0 20px; height: 60px; box-shadow: 0 1px 4px rgba(0,0,0,.08); flex-shrink: 0; }
.navbar-content { display: flex; align-items: center; gap: 10px; padding-left: 20px; width: 100%; }
.navbar-left { display: flex; align-items: center; gap: 16px; flex-shrink: 0; }
.navbar-title { margin: 0; color: #303133; font-size: 20px; font-weight: 600; cursor: pointer; }
.navbar-title:hover { color: #409eff; }
.navbar-menu { display: flex; align-items: center; gap: 10px; min-width: 0; }
.header-navbar--data .navbar-menu { flex: 1; overflow-x: auto; white-space: nowrap; scrollbar-width: thin; }
.header-navbar--data .navbar-menu :slotted(*) { flex-shrink: 0; }
.header-navbar--data .navbar-content { min-width: 0; padding-left: 0; }
@media (max-width: 900px) {
  .header-navbar--data { padding: 0 8px; }
  .header-navbar--data .navbar-left { gap: 8px; }
  .header-navbar--data .navbar-title { max-width: 125px; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
}
@media (max-width: 480px) { .header-navbar--data .navbar-title { max-width: 65px; } }
</style>
