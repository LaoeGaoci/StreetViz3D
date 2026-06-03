export type JsonValue = Record<string, any> | any[] | string | number | boolean | null;
import { StreetSceneDTO, StreetSceneResponse, StreetPreviewResponse} from '@/types/street/street';

const API_BASE_URL = 'http://localhost:8080';
const NGINX_STATIC_BASE_URL = 'http://localhost:65';
const MODEL_BASE_URL = `${NGINX_STATIC_BASE_URL}/model`;

export async function fetchStreetPreview(streetId: string): Promise<StreetPreviewResponse> {
    const response = await fetch(`${API_BASE_URL}/api/streets/${streetId}/preview`, {
        method: 'GET'
    });

    if (!response.ok) {
        throw new Error(`请求失败: ${response.status}`);
    }

    return await response.json();
}

export async function fetchStreetScene(streetId: string): Promise<StreetSceneDTO> {
    const response = await fetch(`${API_BASE_URL}/api/streets/${streetId}/scene`, {
        method: 'GET'
    });

    if (!response.ok) {
        throw new Error(`请求失败: ${response.status}`);
    }

    const result: StreetSceneResponse = await response.json();
    console.log(result.scene);
    if (!result?.scene) {
        throw new Error('场景数据为空');
    }

    return result.scene;
}

export async function fetchStreetSceneFromStreetmix(streetmixUrl: string): Promise<StreetSceneDTO> {
    const response = await fetch(`${API_BASE_URL}/api/streetmix/scene`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ streetmixUrl })
    });

    if (!response.ok) {
        throw new Error(`请求失败: ${response.status}`);
    }
    //console.log(await response.json());
    return await response.json();
}

export function resolveModelUrl(modelUrl?: string | null): string | null {
    if (!modelUrl) return null;

    if (modelUrl.startsWith('http://') || modelUrl.startsWith('https://')) {
        return modelUrl;
    }

    if (modelUrl.startsWith('/')) {
        return `${NGINX_STATIC_BASE_URL}${modelUrl}`;
    }

    return `${MODEL_BASE_URL}/${modelUrl}`;
}


export async function uploadStreetImage(file: File): Promise<StreetPreviewResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/api/street/predict', {
        method: 'POST',
        body: formData,
    });

    if (!response.ok) {
        throw new Error('上传街道图片失败: ' + response.statusText);
    }

    const data = await response.json();
    return data;
}