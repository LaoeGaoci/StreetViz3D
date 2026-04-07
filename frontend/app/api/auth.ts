import {
    UpdateUserModelPublicRequest,
    UploadUserModelRequest,
    UserUploadedModelResponse
} from '@/types/auth/userModel';
import {
    UpdatePasswordRequest,
    UserInfo,
    UserStreetItem
} from '@/types/auth/user';

export const API_BASE_URL = 'http://localhost:8080';

/**
 * 后端统一响应结构
 */
interface ApiResponse<T> {
    code: number;
    message?: string;
    data: T;
}

/**
 * 创建模型草稿响应
 */
export interface CreateUserModelDraftResponse {
    uploadId: string;
}

/**
 * 统一解析接口响应
 */
async function parseApiResponse<T>(response: Response, defaultMessage: string): Promise<T> {
    let result: ApiResponse<T>;

    try {
        result = await response.json();
    } catch {
        throw new Error(defaultMessage);
    }

    if (!response.ok || result.code !== 200) {
        throw new Error(result.message || defaultMessage);
    }

    return result.data;
}

/* =========================
 * 用户基础信息
 * ========================= */

export async function getUserInfo(userId: string): Promise<UserInfo> {
    const response = await fetch(
        `${API_BASE_URL}/user/info?userId=${encodeURIComponent(userId)}`,
        {
            method: 'GET'
        }
    );

    return parseApiResponse<UserInfo>(response, '获取用户信息失败');
}

export async function updatePassword(request: UpdatePasswordRequest): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/user/password`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });

    await parseApiResponse<null>(response, '修改密码失败');
}

export async function uploadAvatarFile(userId: string, file: File): Promise<string> {
    const formData = new FormData();
    formData.append('userId', userId);
    formData.append('file', file);

    const response = await fetch(`${API_BASE_URL}/user/avatar`, {
        method: 'POST',
        body: formData
    });

    return parseApiResponse<string>(response, '上传头像失败');
}

export async function getUserIdFromEmail(): Promise<string> {
    const email = localStorage.getItem('email');

    if (!email) {
        throw new Error('邮箱不存在，请重新登录');
    }

    const response = await fetch(
        `${API_BASE_URL}/user/id?email=${encodeURIComponent(email)}`,
        {
            method: 'GET'
        }
    );

    const data = await parseApiResponse<{ userId: string }>(response, '获取用户ID失败');
    const userId = data.userId;

    localStorage.setItem('userId', userId);
    return userId;
}

export async function getUserStreetList(userId: string): Promise<UserStreetItem[]> {
    const response = await fetch(
        `${API_BASE_URL}/user/streetList?userId=${encodeURIComponent(userId)}`,
        {
            method: 'GET'
        }
    );

    return parseApiResponse<UserStreetItem[]>(response, '获取用户街道列表失败');
}

/* =========================
 * 用户模型
 * ========================= */

/**
 * 第一步：创建模型草稿
 * POST /user/model
 */
export async function createUserModelDraft(
    request: UploadUserModelRequest
): Promise<CreateUserModelDraftResponse> {
    const response = await fetch(`${API_BASE_URL}/user/model`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });

    return parseApiResponse<CreateUserModelDraftResponse>(response, '创建模型草稿失败');
}

/**
 * 第二步：上传模型文件
 * POST /user/model/files
 *
 * 注意：
 * 1. 不要手动设置 Content-Type
 * 2. 让浏览器自动为 FormData 补充 multipart boundary
 */
export async function uploadUserModelFiles(params: {
    userId: string;
    uploadId: string;
    gltfFile: File;
    binFile?: File | null;
    textureFiles?: File[];
    previewFile?: File | null;
}): Promise<UserUploadedModelResponse> {
    const formData = new FormData();
    formData.append('userId', params.userId);
    formData.append('uploadId', params.uploadId);
    formData.append('gltfFile', params.gltfFile);

    if (params.binFile) {
        formData.append('binFile', params.binFile);
    }

    if (params.textureFiles?.length) {
        params.textureFiles.forEach((file) => {
            formData.append('textureFiles', file);
        });
    }

    if (params.previewFile) {
        formData.append('previewFile', params.previewFile);
    }

    const response = await fetch(`${API_BASE_URL}/user/model/files`, {
        method: 'POST',
        body: formData
    });

    return parseApiResponse<UserUploadedModelResponse>(response, '上传模型文件失败');
}

export async function deleteUserModel(userId: string, uploadId: string): Promise<void> {
    const response = await fetch(
        `${API_BASE_URL}/user/model?userId=${encodeURIComponent(userId)}&uploadId=${encodeURIComponent(uploadId)}`,
        {
            method: 'DELETE'
        }
    );

    await parseApiResponse<null>(response, '删除用户模型失败');
}

export async function getUserUploadedModelList(userId: string): Promise<UserUploadedModelResponse[]> {
    const response = await fetch(
        `${API_BASE_URL}/user/model/list?userId=${encodeURIComponent(userId)}`,
        {
            method: 'GET'
        }
    );

    return parseApiResponse<UserUploadedModelResponse[]>(response, '获取用户上传模型列表失败');
}

export async function updateUserModelPublicStatus(
    request: UpdateUserModelPublicRequest
): Promise<UserUploadedModelResponse> {
    const response = await fetch(`${API_BASE_URL}/user/model/public`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });

    return parseApiResponse<UserUploadedModelResponse>(response, '修改用户模型公开状态失败');
}

export async function updateUserModelPreview(
    userId: string,
    uploadId: string,
    previewFile: File
): Promise<UserUploadedModelResponse> {
    const formData = new FormData();
    formData.append('userId', userId);
    formData.append('uploadId', uploadId);
    formData.append('previewFile', previewFile);

    const response = await fetch(`${API_BASE_URL}/user/model/preview`, {
        method: 'PUT',
        body: formData
    });

    return parseApiResponse<UserUploadedModelResponse>(response, '修改用户模型预览图失败');
}