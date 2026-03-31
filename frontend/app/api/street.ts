export type JsonValue = Record<string, any> | any[] | string | number | boolean | null;

export interface ModelAssetDTO {
    modelId: string;
    modelName: string;
    modelType: string;
    modelSubtype: string;
    displayName: string;
    modelUrl: string;
    previewUrl: string;
}

export interface SegmentPreviewDTO {
    segmentId: string;
    sortIndex: number;
    type: string;
    width: number;
    elevation: number;
    slopeOn: boolean;
    slopeValues: JsonValue;
    variantData: JsonValue;
    model: ModelAssetDTO | null;
}

export interface BoundaryPreviewDTO {
    boundaryId: string;
    side: 'left' | 'right';
    type: string;
    floors: number;
    elevation: number;
    variantData: JsonValue;
    model: ModelAssetDTO | null;
}

export interface StreetPreviewResponse {
    streetId: string;
    streetName: string;
    creatorId: string;
    namespacedId: number;
    unit: number;
    schemaVersion: number;
    width: number;
    skybox: string | null;
    weather: string | null;
    location: string | null;
    editCount: number;
    segments: SegmentPreviewDTO[];
    boundaries: {
        left: BoundaryPreviewDTO | null;
        right: BoundaryPreviewDTO | null;
    };
}

/** ===== 新增：Scene API DTO ===== */

export interface SceneVector3DTO {
    x: number;
    y: number;
    z: number;
}

export interface SceneInstanceDTO {
    kind: 'surface' | 'model' | 'support' | 'placeholder' | string;
    semanticType: string;

    modelId?: string | null;
    modelUrl?: string | null;
    displayName?: string | null;

    position: SceneVector3DTO;
    rotation?: SceneVector3DTO | null;
    scale?: SceneVector3DTO | null;

    width?: number | null;
    height?: number | null;
    depth?: number | null;
    color?: string | null;
}

export interface SegmentSurfaceDTO {
    position: SceneVector3DTO;
    width: number;
    height: number;
    depth: number;
    color: string;
    materialKey?: string | null;
}

export interface SegmentSceneDTO {
    segmentId: string;
    sortIndex: number;
    type: string;
    variantData: JsonValue;

    originalWidth: number;
    renderedWidth: number;

    startX: number;
    centerX: number;
    endX: number;

    elevation: number;
    elevationY: number;

    surface: SegmentSurfaceDTO;
    instances: SceneInstanceDTO[];
}

export interface BoundarySceneDTO {
    boundaryId: string;
    side: 'left' | 'right' | string;
    type: string;
    floors: number;
    elevation: number;
    variantData: JsonValue;

    centerX: number;
    supportHeight: number;

    supportSurface: SceneInstanceDTO | null;
    instances: SceneInstanceDTO[];
}

export interface SceneBaseDTO {
    width: number;
    height: number;
    depth: number;
    position: SceneVector3DTO;
    color: string;
}

export interface SceneStyleDTO {
    skyColor: string;
    ambientLightColor: string;
    ambientLightIntensity: number;
    directionalLightColor: string;
    directionalLightIntensity: number;
}

export interface StreetSceneDTO {
    streetId: string;
    streetName: string;

    streetWidth: number;
    roadLength: number;
    widthScale: number;

    base: SceneBaseDTO;
    style: SceneStyleDTO;

    segments: SegmentSceneDTO[];

    leftBoundary: BoundarySceneDTO | null;
    rightBoundary: BoundarySceneDTO | null;
}

export interface StreetSceneResponse {
    scene: StreetSceneDTO;
}

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