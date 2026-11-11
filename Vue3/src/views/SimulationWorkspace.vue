<template>
  <div class="simulation-workspace" :class="{ 'simulation-workspace--sandbox': sandbox }">
    <template v-if="sandbox">
      <SimulationWorkspaceHeader data-view @home="goHome">
        <template #view>
          <span class="mode-label">数据沙箱</span>
          <ElButtonGroup aria-label="沙箱工作区视图">
            <ElButton size="small" :type="sandboxView === 'operation' ? 'primary' : 'default'" :aria-pressed="sandboxView === 'operation'" @click="sandboxView = 'operation'">运行视图</ElButton>
            <ElButton size="small" :type="sandboxView === 'data' ? 'primary' : 'default'" :aria-pressed="sandboxView === 'data'" @click="sandboxView = 'data'">数据视图</ElButton>
          </ElButtonGroup>
        </template>
        <ModeNavigationButton to="/" label="普通仿真" />
        <router-link to="/sandbox">沙箱配置</router-link>
        <router-link to="/sandbox/tasks">任务与进度</router-link>
        <ElButton text type="primary" @click="sandboxView = 'evaluation'">评价</ElButton>
      </SimulationWorkspaceHeader>
      <SandboxMap :view="sandboxView" @open-evaluation="sandboxView = 'evaluation'" @back="sandboxView = 'data'" />
    </template>
    <MapContainer v-else />
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import { ElButton, ElButtonGroup } from 'element-plus'
import { useRouter } from 'vue-router'
import SimulationWorkspaceHeader from '../components/SimulationWorkspaceHeader.vue'
import ModeNavigationButton from '../components/ModeNavigationButton.vue'

defineProps<{ sandbox?: boolean }>()
const router = useRouter()
const sandboxView = ref<'operation' | 'data' | 'evaluation'>('operation')
const MapContainer = defineAsyncComponent(() => import('../components/MapContainer.vue'))
const SandboxMap = defineAsyncComponent(() => import('./SandboxMap.vue'))
function goHome() { void router.push('/') }
</script>

<style scoped>
.simulation-workspace { display: flex; flex-direction: column; min-height: 100vh; }
.mode-label { color: #606266; font-size: 14px; white-space: nowrap; }
</style>
