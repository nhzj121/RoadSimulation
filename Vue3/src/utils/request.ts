import axios from 'axios';

const service = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    timeout: 100000,
})

export default service;
