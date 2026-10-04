<script setup>
import { useRoute } from 'vue-router'
import { onMounted, onBeforeUnmount } from 'vue'
import SandboxNavigationBar from './components/sandbox/SandboxNavigationBar.vue'
import { refreshModeNavigation } from './navigation/modeNavigation'

const route = useRoute()
let timer, disposed = false
async function pollNavigation() {
  await refreshModeNavigation()
  if (!disposed) timer = setTimeout(pollNavigation, 5000)
}
onMounted(pollNavigation)
onBeforeUnmount(() => { disposed = true; clearTimeout(timer) })
</script>

<template>
  <div :class="{ 'sandbox-shell': route.path === '/sandbox' || route.path.startsWith('/sandbox/') }">
    <SandboxNavigationBar v-if="route.path === '/sandbox' || route.path.startsWith('/sandbox/')" />
    <router-view />
  </div>

</template>
