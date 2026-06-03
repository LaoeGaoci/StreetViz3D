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
export interface SceneInstanceDTO {
    modelUrl?: string;
    modelId?: string;
    position: { x: number; y: number; z: number };
    rotation: { x: number; y: number; z: number };
    scale?: { x: number; y: number; z: number };
    width?: number;
    height?: number;
    depth?: number;
    displayName?: string;
    semanticType?: string;
    color?: string;
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
