'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
    resolveModelUrl,
    SceneInstanceDTO,
    StreetSceneDTO
} from '@/app/api/street';
import StreetSceneLights from './StreetSceneLights';
import 'aframe';

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
            downKey2: { type: 'string', default: 'ShiftRight' }
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
}

function vectorToString(
    vector?: { x: number; y: number; z: number } | null,
    fallback = '0 0 0'
): string {
    if (!vector) return fallback;
    return `${vector.x} ${vector.y} ${vector.z}`;
}

function GltfEntity({
    url,
    position,
    rotation = '0 0 0',
    scale = '1 1 1'
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
        };

        el.addEventListener('model-error', onError);
        el.addEventListener('model-loaded', onModelLoaded);

        applyShadowToMesh();

        return () => {
            el.removeEventListener('model-error', onError);
            el.removeEventListener('model-loaded', onModelLoaded);
        };
    }, [url]);

    return (
        <a-entity
            ref={entityRef}
            gltf-model={url}
            position={position}
            rotation={rotation}
            scale={scale}
            shadow="cast: true; receive: true"
        />
    );
}

function PlaceholderEntity({ instance }: { instance: SceneInstanceDTO }) {
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
        />
    );
}

function SceneInstanceRenderer({
    instance,
    instanceKey
}: {
    instance: SceneInstanceDTO;
    instanceKey: string;
}) {
    const modelUrl = resolveModelUrl(instance.modelUrl);
    if (modelUrl) {
        return (
            <GltfEntity
                key={instanceKey}
                url={modelUrl}
                position={vectorToString(instance.position)}
                rotation={vectorToString(instance.rotation)}
                scale={vectorToString(instance.scale, '1 1 1')}
            />
        );
    }

    return <PlaceholderEntity key={instanceKey} instance={instance} />;
}

export default function ModelContainer({
    sceneData,
    className,
    style
}: ModelContainerProps) {
    const [aframeReady, setAframeReady] = useState(false);

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

    const allBoundaryInstances = useMemo(() => {
        const scene = sceneData;
        const result: Array<{ key: string; instance: SceneInstanceDTO }> = [];

        if (scene.leftBoundary?.supportSurface) {
            result.push({
                key: `left-support-${scene.leftBoundary.boundaryId}`,
                instance: scene.leftBoundary.supportSurface
            });
        }

        scene.leftBoundary?.instances?.forEach((instance, index) => {
            result.push({
                key: `left-instance-${scene.leftBoundary?.boundaryId}-${index}`,
                instance
            });
        });

        if (scene.rightBoundary?.supportSurface) {
            result.push({
                key: `right-support-${scene.rightBoundary.boundaryId}`,
                instance: scene.rightBoundary.supportSurface
            });
        }

        scene.rightBoundary?.instances?.forEach((instance, index) => {
            result.push({
                key: `right-instance-${scene.rightBoundary?.boundaryId}-${index}`,
                instance
            });
        });

        return result;
    }, [sceneData]);

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
                borderRadius: 18,
                background: '#000',
                ...style
            }}
        >
            <div
                style={{
                    position: 'absolute',
                    top: 12,
                    right: 12,
                    zIndex: 20,
                    background: 'rgba(17,24,39,0.72)',
                    backdropFilter: 'blur(8px)',
                    color: '#f9fafb',
                    padding: '10px 14px',
                    borderRadius: 12,
                    fontSize: 13,
                    lineHeight: 1.5,
                    boxShadow: '0 6px 20px rgba(0,0,0,0.24)',
                    maxWidth: 360
                }}
            >
                <div style={{ fontWeight: 700 }}>{scene.streetName}</div>
                <div>street width: {scene.streetWidth} m</div>
                <div>road length: {scene.roadLength} m</div>
                <div>segments: {scene.segments.length}</div>
                <div>sky: {styleInfo?.skyColor || '#e7eff6'}</div>
            </div>

            <a-scene
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

                <a-entity position="0 14 22" fly-controls-y="speed: 8">
                    <a-camera
                        wasd-controls-enabled="true"
                        look-controls-enabled="true"
                        near="0.1"
                        far="2000"
                    />
                </a-entity>

                <StreetSceneLights styleInfo={styleInfo} />

                {/* base */}
                <a-box
                    position={vectorToString(base.position)}
                    width={base.width + 110}
                    height={base.height}
                    depth={base.depth}
                    color={base.color || '#c8b89a'}
                    opacity="1"
                    shadow="cast: false; receive: true"
                />

                {/* segment surfaces */}
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
                    />
                ))}

                {/* segment instances */}
                {scene.segments.flatMap((segment) =>
                    (segment.instances || []).map((instance, index) => (
                        <SceneInstanceRenderer
                            key={`instance-${segment.segmentId}-${index}`}
                            instanceKey={`instance-${segment.segmentId}-${index}`}
                            instance={instance}
                        />
                    ))
                )}

                {/* boundary supports + boundary models */}
                {allBoundaryInstances.map(({ key, instance }) => (
                    <SceneInstanceRenderer
                        key={key}
                        instanceKey={key}
                        instance={instance}
                    />
                ))}
            </a-scene>
        </div>
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
        padding: isError ? 24 : undefined,
        textAlign: isError ? 'center' : undefined,
        ...style
    };
}