import request from '../utils/request'

// 后端响应格式
interface BackendResponse<T = any> {
    success: boolean;
    message?: string;
    data?: T;
}

interface StartSimulationOptions {
    useHeuristic?: boolean;
    strategy?: 'ORIGINAL' | 'HEURISTIC';
}

export const simulationController = {
    // 启动仿真
    async startSimulation(options: StartSimulationOptions = {}): Promise<BackendResponse> {
        try{
            const response = await request.post('/api/simulation/start', options);
            return response.data;
        } catch (error : any){
            console.log('启动仿真失败:', error);
            return{
                success: false,
                message: error.response?.data?.message || error.message || '启动仿真失败'
            };
        }
    },

    async getConfig(): Promise<BackendResponse> {
        try {
            const response = await request.get('/api/simulation/config');
            return response.data;
        } catch (error: any) {
            console.log('get simulation config failed:', error);
            return {
                success: false,
                message: error.response?.data?.message || error.message || 'get simulation config failed'
            };
        }
    },

    // 暂停仿真
    async stopSimulation(): Promise<BackendResponse<string>> {
        try{
            const response = await request.post('/api/simulation/stop');
            return response.data;
        } catch (error : any){
            console.log('暂停仿真失败:', error);
            return{
                success: false,
                message: error.response?.data?.message || error.message || '停止仿真失败'
            };
        }
    },

    // 重置仿真
    async resetSimulation(): Promise<BackendResponse<string>> {
        try {
            const response = await request.post('/api/simulation/reset');
            return response.data;
        } catch (error: any) {
            console.error('重置仿真失败:', error);
            return {
                success: false,
                message: error.response?.data?.message || error.message || '重置仿真失败'
            };
        }
    }

}
