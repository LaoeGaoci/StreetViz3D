export const API_BASE_URL = 'http://localhost:8080';

export interface UserInfo {
    userId: string;
    userName: string;
    avatarUrl: string;
    email: string;
    createdAt: string;
    updatedAt: string;
}

interface ApiResponse<T> {
    code: number;
    message?: string;
    data: T;
}

export interface UpdateAvatarRequest {
    userId: string;
    avatarUrl: string;
}

export interface UpdatePasswordRequest {
    userId: string;
    oldPassword: string;
    newPassword: string;
}
export interface UserStreetItem {
    streetId: string;
    streetName: string;
    width: number;
    createdAt: string;
    updatedAt: string;
}
/**
 * 用户街道列表项
 * 对应后端：
 * GET /api/streets/user/{userId}
 */
export interface UserStreetItem {
    streetId: string;
    streetName: string;
    width: number;
    createdAt: string;
    updatedAt: string;
}


export async function getUserInfo(userId: string): Promise<UserInfo> {
    const response = await fetch(`${API_BASE_URL}/user/info?userId=${encodeURIComponent(userId)}`, {
        method: 'GET'
    });

    const result: ApiResponse<UserInfo> = await response.json();

    if (!response.ok || result.code !== 200) {
        throw new Error(result.message || '获取用户信息失败');
    }

    return result.data;
}

export async function updatePassword(request: UpdatePasswordRequest): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/user/password`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });

    const result: ApiResponse<null> = await response.json();

    if (!response.ok || result.code !== 200) {
        throw new Error(result.message || '修改密码失败');
    }
}

/**
 * 上传用户头像文件
 *
 * 功能说明：
 * 1. 前端将 userId 与头像文件一起提交给后端；
 * 2. 后端负责将文件保存到 Nginx 静态目录；
 * 3. 后端负责将相对路径 avatar/{userId}.{ext} 存入数据库；
 * 4. 接口最终返回完整可访问的头像 URL。
 *
 * @param userId 当前用户 ID
 * @param file 用户选择的头像文件
 * @returns 上传成功后的完整头像访问地址，例如：
 * http://localhost:65/auth/avatar/{userId}.png
 * @throws 当接口响应失败或业务状态码不是 200 时抛出异常
 */
export async function uploadAvatarFile(userId: string, file: File): Promise<string> {
    const formData = new FormData();
    formData.append('userId', userId);
    formData.append('file', file);

    const response = await fetch(`${API_BASE_URL}/user/avatar`, {
        method: 'POST',
        body: formData
    });

    const result: ApiResponse<string> = await response.json();

    if (!response.ok || result.code !== 200) {
        throw new Error(result.message || '上传头像失败');
    }

    return result.data;
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

    const result = await response.json();

    if (!response.ok || result.code !== 200) {
        throw new Error(result.message || '获取用户ID失败');
    }

    const userId = result.data.userId;
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

    const result: ApiResponse<UserStreetItem[]> = await response.json();

    if (!response.ok || result.code !== 200) {
        throw new Error(result.message || '获取用户街道列表失败');
    }

    return result.data;
}