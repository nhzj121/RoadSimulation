<template>
  <div class="pressure-test-container">
    <!-- 顶部控制栏 -->
    <el-header class="header-navbar">
      <div class="navbar-content left-aligned">
        <h2 class="navbar-title" @click="goBack">物流运输仿真系统</h2>
        <div class="navbar-menu">
          <ElButton text @click="goToPOIManager">POI管理</ElButton>
          <ElButton text>压力测试</ElButton>
        </div>
      </div>
    </el-header>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {ElButton, ElMessage, ElMessageBox} from 'element-plus'

const router = useRouter()
// 路由跳转
const goBack = () => router.push('/')
const goToPOIManager = () => router.push('/poi-manager')



</script>

<style scoped>
.pressure-test-container {
  padding: 20px;
  height: 100vh;
  overflow-y: auto;
  background-color: #f5f7fa;
}

/* 导航栏样式 */
.header-navbar {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 60px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.navbar-content {
  display: flex;
  align-items: center;
  width: 100%;
}

.navbar-content.left-aligned {
  justify-content: flex-start;
  gap: 40px;
}

.navbar-title {
  margin: 0;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.navbar-menu {
  display: flex;
  gap: 10px;
}
</style>