// src/api/baseApi.ts
import request from '../utils/request';

// 基础CRUD接口定义
export interface CrudApi<T> {
    getAll(): Promise<T[]>;
    getById(id: number): Promise<T>;
    create(data: Omit<T, 'id'>): Promise<T>;
    update(id: number, data: Partial<T>): Promise<T>;
    delete(id: number): Promise<void>;
}

// 基础API实现
export class BaseApi<T extends { id?: number }> implements CrudApi<T> {
    constructor(protected endpoint: string) {}

    async getAll(): Promise<T[]> {
        return request.get(`/${this.endpoint}`);
    }

    async getById(id: number): Promise<T> {
        return request.get(`/${this.endpoint}/${id}`);
    }

    async create(data: Omit<T, 'id'>): Promise<T> {
        return request.post(`/${this.endpoint}`, data);
    }

    async update(id: number, data: Partial<T>): Promise<T> {
        return request.put(`/${this.endpoint}/${id}`, data);
    }

    async delete(id: number): Promise<void> {
        return request.delete(`/${this.endpoint}/${id}`);
    }

    // 分页查询
    async getByPage(page: number, size: number = 10) {
        return request.get(`/${this.endpoint}/page`, {
            params: { page: page - 1, size } // 后端通常从0开始
        });
    }
}