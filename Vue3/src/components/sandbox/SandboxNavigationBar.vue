<template>
  <nav class="sandbox-navbar" aria-label="数据沙箱顶部导航">
    <ModeNavigationButton to="/" label="物流运输仿真系统" class="system-title" />
    <span class="module-label">数据沙箱</span>
    <div class="sandbox-navbar-links">
      <router-link to="/sandbox">沙箱配置</router-link>
      <router-link to="/sandbox/tasks">任务与进度</router-link>
    </div>
    <span v-if="navigationReason" class="navigation-reason" role="status">{{ navigationMessage(navigationReason) }}</span>
  </nav>
</template>
<script setup lang="ts">
import ModeNavigationButton from '../ModeNavigationButton.vue'
import { navigationReason, navigationMessage } from '../../navigation/modeNavigation'
</script>
<style scoped>
.sandbox-navbar { min-height: 60px; padding: 10px 32px; display: flex; align-items: center; gap: 24px; flex-wrap: wrap; background: #fff; border-bottom: 1px solid #e4e7ed; }
.system-title :deep(.el-button) { font-size: 20px; font-weight: 600; color: #303133; padding: 0; }
.system-title :deep(.el-button:not(:disabled):hover) { color: #409eff; }
.system-title :deep(.el-button:disabled) { color: #a8abb2; }
.module-label { padding-left: 24px; border-left: 1px solid #dcdfe6; color: #606266; font-size: 14px; }
.sandbox-navbar-links { display: flex; align-self: stretch; align-items: center; gap: 8px; }
.sandbox-navbar-links a { display: inline-flex; align-items: center; min-height: 36px; padding: 6px 16px; border-radius: 6px; font-size: 14px; text-decoration: none; }
.sandbox-navbar-links a:hover { background: #f7f8fa; }
.sandbox-navbar-links a.router-link-exact-active { background: #ecf5ff; color: #337ecc; font-weight: 600; }
.navigation-reason { margin-left: auto; font-size: 12px; color: #606266; max-width: 540px; }
@media (max-width: 900px) { .sandbox-navbar { padding: 12px 20px; gap: 16px; } .navigation-reason { width: 100%; margin-left: 0; } }
</style>
