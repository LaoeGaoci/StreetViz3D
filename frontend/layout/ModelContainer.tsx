'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button } from 'primereact/button';
import {SceneInstanceDTO, StreetSceneDTO } from '@/types/street/street';
import { resolveModelUrl } from '@/app/api/street';
import StreetSceneLights from './components/StreetSceneLights';
import SelectionInfoPanel from './components/SelectionInfoPanel';
import {
    createSelectionController,
    InteractionMode,
    SelectionPayload,
    SelectableMeta
} from './utils/aframe-selection';
import 'aframe';

const SELECT_NODE_EVENT = 'streetviz3d:select-node';

if (
    typeof window !== 'undefined' &&
    (window as any).AFRAME &&
    !(window as any).AFRAME.components['fly-controls-y']
) {
    const AFRAME = (window as any).AFRAME;

    AFRAME.registerComponent('fly-controls-y', {
        schema: {
            speed: { type: 'number', default: 6 },
            upKey: { type: 'string', default: 'Space' },
            downKey: { type: 'string', default: 'ShiftLeft' },
            downKey2: { type: 'string', default: 'ShiftRight' },
            enabled: { type: 'boolean', default: true }
        },

        init() {
            this.keys = new Set<string>();

            this.onKeyDown = (e: KeyboardEvent) => {
                this.keys.add(e.code);
            };

            this.onKeyUp = (e: KeyboardEvent) => {
                this.keys.delete(e.code);
            };

            window.addEventListener('keydown', this.onKeyDown);
            window.addEventListener('keyup', this.onKeyUp);
        },

        tick(_time: number, delta: number) {
            if (!this.data.enabled) return;

            const el = this.el;
            const pos = el.getAttribute('position');
            if (!pos) return;

            const step = (this.data.speed * delta) / 1000;
            let nextY = pos.y;

            if (this.keys.has(this.data.upKey)) {
                nextY += step;
            }

            if (this.keys.has(this.data.downKey) || this.keys.has(this.data.downKey2)) {
                nextY -= step;
            }

            el.setAttribute('position', {
                x: pos.x,
                y: nextY,
                z: pos.z
            });
        },

        remove() {
            window.removeEventListener('keydown', this.onKeyDown);
            window.removeEventListener('keyup', this.onKeyUp);
        }
    });
}

interface ModelContainerProps {
    sceneData: StreetSceneDTO;
    className?: string;
    style?: React.CSSProperties;
}

interface GltfEntityProps {
    url: string;
    position: string;
    rotation?: string;
    scale?: string;
    meta: SelectableMeta;
    onModelReady?: () => void;
}

interface SelectionControllerHandle {
    clearSelection?: () => void;
    refreshSelected?: () => void;
    selectBySelectionId?: (selectionId: string) => SelectionPayload | null;
    focusSelected?: (options?: { distanceMultiplier?: number }) => void;
    setInteractionMode?: (mode: InteractionMode) => void;
    getInteractionMode?: () => InteractionMode;
    destroy?: () => void;
}

function vectorToString(
    vector?: { x: number; y: number; z: number } | null,
    fallback = '0 0 0'
): string {
    if (!vector) return fallback;
    return `${vector.x} ${vector.y} ${vector.z}`;
}

function serializeMeta(meta: SelectableMeta): string {
    return JSON.stringify(meta);
}

function SelectableGltfEntity({
    url,
    position,
    rotation = '0 0 0',
    scale = '1 1 1',
    meta,
    onModelReady
}: GltfEntityProps) {
    const entityRef = useRef<any>(null);

    useEffect(() => {
        const el = entityRef.current;
        if (!el) return;

        const onError = (event: any) => {
            console.error('模型加载失败：', url, event);
        };

        const applyShadowToMesh = () => {
            const mesh = el.getObject3D('mesh');
            if (!mesh) return;

            mesh.traverse((node: any) => {
                if (node?.isMesh) {
                    node.castShadow = true;
                    node.receiveShadow = true;

                    if (node.material) {
                        if (Array.isArray(node.material)) {
                            node.material.forEach((mat: any) => {
                                if (mat) mat.needsUpdate = true;
                            });
                        } else {
                            node.material.needsUpdate = true;
                        }
                    }
                }
            });
        };

        const onModelLoaded = () => {
            applyShadowToMesh();
            onModelReady?.();
        };

        el.addEventListener('model-error', onError);
        el.addEventListener('model-loaded', onModelLoaded);

        applyShadowToMesh();

        return () => {
            el.removeEventListener('model-error', onError);
            el.removeEventListener('model-loaded', onModelLoaded);
        };
    }, [url, onModelReady]);

    return (
        <a-entity
            ref={entityRef}
            gltf-model={url}
            position={position}
            rotation={rotation}
            scale={scale}
            shadow="cast: true; receive: true"
            data-selectable="true"
            data-selection-meta={serializeMeta(meta)}
        />
    );
}

function SelectablePlaceholderEntity({
    instance,
    meta
}: {
    instance: SceneInstanceDTO;
    meta: SelectableMeta;
}) {
    const width = instance.width ?? 1;
    const height = instance.height ?? 1;
    const depth = instance.depth ?? 1;
    const color = instance.color || '#9ca3af';

    return (
        <a-box
            position={vectorToString(instance.position)}
            rotation={vectorToString(instance.rotation)}
            scale={vectorToString(instance.scale, '1 1 1')}
            width={width}
            height={height}
            depth={depth}
            color={color}
            opacity="0.95"
            shadow="cast: true; receive: true"
            data-selectable="true"
            data-selection-meta={serializeMeta(meta)}
        />
    );
}

function SceneInstanceRenderer({
    instance,
    instanceKey,
    meta,
    onModelReady
}: {
    instance: SceneInstanceDTO;
    instanceKey: string;
    meta: SelectableMeta;
    onModelReady?: () => void;
}) {
    const modelUrl = resolveModelUrl(instance.modelUrl);

    if (modelUrl) {
        return (
            <SelectableGltfEntity
                key={instanceKey}
                url={modelUrl}
                position={vectorToString(instance.position)}
                rotation={vectorToString(instance.rotation)}
                scale={vectorToString(instance.scale, '1 1 1')}
                meta={meta}
                onModelReady={onModelReady}
            />
        );
    }

    return (
        <SelectablePlaceholderEntity
            key={instanceKey}
            instance={instance}
            meta={meta}
        />
    );
}

export default function ModelContainer({
    sceneData,
    className,
    style
}: ModelContainerProps) {
    const [aframeReady, setAframeReady] = useState(false);
    const [selected, setSelected] = useState<SelectionPayload | null>(null);
    const [visibleRight, setVisibleRight] = useState(false);
    const [interactionMode, setInteractionMode] = useState<InteractionMode>('view');

    const sceneRef = useRef<any>(null);
    const controllerRef = useRef<SelectionControllerHandle | null>(null);

    useEffect(() => {
        let mounted = true;

        async function loadAFrame() {
            try {
                if (mounted) {
                    setAframeReady(true);
                }
            } catch (e) {
                console.error('A-Frame 初始化失败', e);
            }
        }

        loadAFrame();

        return () => {
            mounted = false;
        };
    }, []);

    useEffect(() => {
        const sceneEl = sceneRef.current;
        if (!sceneEl || !aframeReady) return;

        let destroyed = false;

        const mountController = () => {
            if (destroyed || controllerRef.current) return;

            controllerRef.current = createSelectionController({
                sceneEl,
                onSelect: (payload: SelectionPayload | null) => {
                    setSelected(payload);
                    setVisibleRight(!!payload);
                },
                onHover: () => { }
            });

            controllerRef.current?.setInteractionMode?.('view');
        };

        if (sceneEl.hasLoaded) {
            mountController();
        } else {
            sceneEl.addEventListener('loaded', mountController, { once: true });
        }

        return () => {
            destroyed = true;
            if (controllerRef.current) {
                controllerRef.current.destroy?.();
                controllerRef.current = null;
            }
        };
    }, [aframeReady, sceneData]);

    useEffect(() => {
        const handleExternalSelect = (event: Event) => {
            const customEvent = event as CustomEvent<{ selectionId?: string }>;
            const selectionId = customEvent.detail?.selectionId;

            if (!selectionId || !controllerRef.current) {
                return;
            }

            const payload = controllerRef.current.selectBySelectionId?.(selectionId);

            if (!payload) {
                console.warn('未找到对应的场景节点：', selectionId);
                return;
            }

            setSelected(payload);
            setVisibleRight(true);

            window.requestAnimationFrame(() => {
                controllerRef.current?.focusSelected?.();
            });
        };

        window.addEventListener(SELECT_NODE_EVENT, handleExternalSelect);

        return () => {
            window.removeEventListener(SELECT_NODE_EVENT, handleExternalSelect);
        };
    }, []);

    const handleModeChange = (mode: InteractionMode) => {
        setInteractionMode(mode);
        controllerRef.current?.setInteractionMode?.(mode);
    };

    const allBoundaryInstances = useMemo(() => {
        const scene = sceneData;
        const result: Array<{ key: string; instance: SceneInstanceDTO; meta: SelectableMeta }> = [];

        if (scene.leftBoundary?.supportSurface) {
            result.push({
                key: `left-support-${scene.leftBoundary.boundaryId}`,
                instance: scene.leftBoundary.supportSurface,
                meta: {
                    selectionId: `left-support-${scene.leftBoundary.boundaryId}`,
                    kind: 'boundary-instance',
                    semanticType: scene.leftBoundary.type,
                    displayName: `${scene.leftBoundary.type}-support`,
                    modelId: scene.leftBoundary.supportSurface.modelId ?? null,
                    modelUrl: scene.leftBoundary.supportSurface.modelUrl ?? null,
                    width: scene.leftBoundary.supportSurface.width ?? null,
                    height: scene.leftBoundary.supportSurface.height ?? null,
                    depth: scene.leftBoundary.supportSurface.depth ?? null
                }
            });
        }

        scene.leftBoundary?.instances?.forEach((instance, index) => {
            result.push({
                key: `left-instance-${scene.leftBoundary?.boundaryId}-${index}`,
                instance,
                meta: {
                    selectionId: `left-instance-${scene.leftBoundary?.boundaryId}-${index}`,
                    kind: 'boundary-instance',
                    semanticType: scene.leftBoundary?.type,
                    displayName: instance.displayName ?? `${scene.leftBoundary?.type}-${index}`,
                    modelId: instance.modelId ?? null,
                    modelUrl: instance.modelUrl ?? null,
                    width: instance.width ?? null,
                    height: instance.height ?? null,
                    depth: instance.depth ?? null
                }
            });
        });

        if (scene.rightBoundary?.supportSurface) {
            result.push({
                key: `right-support-${scene.rightBoundary.boundaryId}`,
                instance: scene.rightBoundary.supportSurface,
                meta: {
                    selectionId: `right-support-${scene.rightBoundary.boundaryId}`,
                    kind: 'boundary-instance',
                    semanticType: scene.rightBoundary.type,
                    displayName: `${scene.rightBoundary.type}-support`,
                    modelId: scene.rightBoundary.supportSurface.modelId ?? null,
                    modelUrl: scene.rightBoundary.supportSurface.modelUrl ?? null,
                    width: scene.rightBoundary.supportSurface.width ?? null,
                    height: scene.rightBoundary.supportSurface.height ?? null,
                    depth: scene.rightBoundary.supportSurface.depth ?? null
                }
            });
        }

        scene.rightBoundary?.instances?.forEach((instance, index) => {
            result.push({
                key: `right-instance-${scene.rightBoundary?.boundaryId}-${index}`,
                instance,
                meta: {
                    selectionId: `right-instance-${scene.rightBoundary?.boundaryId}-${index}`,
                    kind: 'boundary-instance',
                    semanticType: scene.rightBoundary?.type,
                    displayName: instance.displayName ?? `${scene.rightBoundary?.type}-${index}`,
                    modelId: instance.modelId ?? null,
                    modelUrl: instance.modelUrl ?? null,
                    width: instance.width ?? null,
                    height: instance.height ?? null,
                    depth: instance.depth ?? null
                }
            });
        });

        return result;
    }, [sceneData]);

    const handleModelReady = () => {
        controllerRef.current?.refreshSelected?.();
    };

    if (!aframeReady) {
        return (
            <div className={className} style={fallbackStyle('#111827', '#e5e7eb', style)}>
                正在初始化 3D 引擎...
            </div>
        );
    }

    if (!sceneData) {
        return (
            <div className={className} style={fallbackStyle('#111827', '#f87171', style, true)}>
                未获取到场景数据
            </div>
        );
    }

    const scene = sceneData;
    const styleInfo = scene.style;
    const base = scene.base;

    return (
        <div
            className={className}
            style={{
                width: '100%',
                height: '100%',
                minHeight: 520,
                position: 'relative',
                overflow: 'hidden',
                background: '#000',
                ...style
            }}
        >
            <SelectionInfoPanel
                visible={visibleRight}
                selected={selected}
                onHide={() => setVisibleRight(false)}
            />

            <div className="model-container__mode-toolbar">
                <ModeButton
                    active={interactionMode === 'view'}
                    title="正常视角移动"
                    icon="pi pi-eye"
                    onClick={() => handleModeChange('view')}
                />
                <ModeButton
                    active={interactionMode === 'move'}
                    title="模型移动"
                    icon="pi pi-arrows-alt"
                    onClick={() => handleModeChange('move')}
                />
                <ModeButton
                    active={interactionMode === 'rotate'}
                    title="模型旋转"
                    icon="pi pi-refresh"
                    onClick={() => handleModeChange('rotate')}
                />
            </div>

            <div className="model-container__scene-info">
                <div className="model-container__scene-title">{scene.streetName}</div>
                <div>street width: {scene.streetWidth} m</div>
                <div>road length: {scene.roadLength} m</div>
                <div>segments: {scene.segments.length}</div>
                <div>sky: {styleInfo?.skyColor || '#e7eff6'}</div>
                <div className="model-container__scene-mode">
                    mode:{' '}
                    {interactionMode === 'view'
                        ? 'view'
                        : interactionMode === 'move'
                            ? 'move'
                            : 'rotate'}
                </div>
            </div>

            <a-scene
                ref={sceneRef}
                embedded
                renderer="colorManagement: true; physicallyCorrectLights: true; antialias: true; shadowMapEnabled: true; shadowMapType: pcfsoft"
                shadow="type: pcfsoft"
                style={{
                    width: '100%',
                    height: '100%',
                    position: 'absolute',
                    inset: 0
                }}
            >
                <a-sky color={styleInfo?.skyColor || '#e4ecf6'} />

                <a-entity position="0 14 22" fly-controls-y="speed: 8; enabled: true">
                    <a-camera
                        wasd-controls-enabled="true"
                        look-controls-enabled="true"
                        near="0.1"
                        far="2000"
                    />
                </a-entity>

                <StreetSceneLights styleInfo={styleInfo} />

                <a-box
                    position={vectorToString(base.position)}
                    width={base.width + 110}
                    height={base.height}
                    depth={base.depth}
                    color={base.color || '#c8b89a'}
                    opacity="1"
                    shadow="cast: false; receive: true"
                />

                {scene.segments.map((segment) => (
                    <a-box
                        key={`surface-${segment.segmentId}`}
                        position={vectorToString(segment.surface.position)}
                        width={segment.surface.width}
                        height={segment.surface.height}
                        depth={segment.surface.depth}
                        color={segment.surface.color}
                        opacity="0.96"
                        shadow="cast: false; receive: true"
                        data-selectable="true"
                        data-selection-meta={serializeMeta({
                            selectionId: `surface-${segment.segmentId}`,
                            kind: 'segment-surface',
                            semanticType: segment.type,
                            displayName: `${segment.type}-surface`
                        })}
                    />
                ))}

                {scene.segments.flatMap((segment) =>
                    (segment.instances || []).map((instance, index) => {
                        const key = `instance-${segment.segmentId}-${index}`;
                        const meta: SelectableMeta = {
                            selectionId: key,
                            kind: 'segment-instance',
                            semanticType: instance.semanticType || segment.type,
                            displayName: instance.displayName ?? `${segment.type}-${index}`,
                            modelId: instance.modelId ?? null,
                            modelUrl: instance.modelUrl ?? null,
                            width: instance.width ?? null,
                            height: instance.height ?? null,
                            depth: instance.depth ?? null
                        };

                        return (
                            <SceneInstanceRenderer
                                key={key}
                                instanceKey={key}
                                instance={instance}
                                meta={meta}
                                onModelReady={handleModelReady}
                            />
                        );
                    })
                )}

                {allBoundaryInstances.map(({ key, instance, meta }) => (
                    <SceneInstanceRenderer
                        key={key}
                        instanceKey={key}
                        instance={instance}
                        meta={meta}
                        onModelReady={handleModelReady}
                    />
                ))}
            </a-scene>
        </div>
    );
}

function ModeButton({
    active,
    title,
    icon,
    onClick
}: {
    active: boolean;
    title: string;
    icon: string;
    onClick: () => void;
}) {
    return (
        <Button
            type="button"
            tooltip={title}
            tooltipOptions={{ position: 'bottom' }}
            onClick={onClick}
            icon={icon}
            text
            rounded
            aria-label={title}
            className="model-container__mode-button"
        />
    );
}

function fallbackStyle(
    background: string,
    color: string,
    style?: React.CSSProperties,
    isError = false
): React.CSSProperties {
    return {
        width: '100%',
        height: '100%',
        minHeight: 320,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background,
        color,
        borderRadius: 16,
        textAlign: isError ? 'center' : undefined,
        ...style
    };
}