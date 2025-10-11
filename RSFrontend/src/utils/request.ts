import axios from 'axios';
import { ElMessage } from 'element-plus';

// 创建axios实例
const service = axios.create({
    baseURL: 'http://localhost:8080',
    timeout: 10000,
});

// // 请求拦截器
// service.interceptors.request.use(
//     (config) => {
//         // 添加token等认证信息
//         const token = localStorage.getItem('token');
//         if (token) {
//             config.headers.Authorization = `Bearer ${token}`;
//         }
//         return config;
//     },
//     (error) => {
//         return Promise.reject(error);
//     }
// );
//
// // 响应拦截器
// service.interceptors.response.use(
//     (response) => {
//         const res = response.data;
//         if (res.code === 200) {
//             return res.data;
//         } else {
//             ElMessage.error(res.message || '请求失败');
//             return Promise.reject(new Error(res.message || 'Error'));
//         }
//     },
//     (error) => {
//         ElMessage.error('网络错误或服务器异常');
//         return Promise.reject(error);
//     }
// );

export default service;