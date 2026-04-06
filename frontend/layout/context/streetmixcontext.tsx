'use client';

import React, { createContext, useContext, useMemo, useState } from 'react';
import { fetchStreetScene, fetchStreetSceneFromStreetmix, StreetSceneDTO } from '@/app/api/street';

const SCENE_STORAGE_KEY = 'streetviz3d-current-scene';
const SCENE_UPDATED_EVENT = 'streetviz3d:scene-updated';

function syncSceneTree(scene: StreetSceneDTO | null) {
    if (typeof window === 'undefined') {
        return;
    }

    if (!scene) {
        window.sessionStorage.removeItem(SCENE_STORAGE_KEY);
    } else {
        window.sessionStorage.setItem(SCENE_STORAGE_KEY, JSON.stringify(scene));
    }

    window.dispatchEvent(new CustomEvent(SCENE_UPDATED_EVENT));
}

interface StreetmixContextValue {
    streetmixUrl: string;
    setStreetmixUrl: (value: string) => void;

    sceneData: StreetSceneDTO | null;
    setSceneData: (scene: StreetSceneDTO | null) => void;

    loading: boolean;
    setLoading: (loading: boolean) => void;

    defaultStreetId: string;
    setDefaultStreetId: (streetId: string) => void;

    previewStreetmix: () => Promise<{ ok: boolean; message?: string }>;
    resetDefaultStreet: () => Promise<{ ok: boolean; message?: string }>;
}

const StreetmixContext = createContext<StreetmixContextValue | null>(null);

export function StreetmixProvider({ children }: { children: React.ReactNode }) {
    const [streetmixUrl, setStreetmixUrl] = useState('');
    const [sceneData, setSceneData] = useState<StreetSceneDTO | null>(null);
    const [loading, setLoading] = useState(false);
    const [defaultStreetId, setDefaultStreetId] = useState('');

    const previewStreetmix = async () => {
        if (!streetmixUrl.trim()) {
            return {
                ok: false,
                message: '请输入 Streetmix URL'
            };
        }

        try {
            setLoading(true);
            const scene = await fetchStreetSceneFromStreetmix(streetmixUrl.trim());
            setSceneData(scene);
            syncSceneTree(scene);

            return {
                ok: true,
                message: 'Streetmix 预览成功'
            };
        } catch (e: any) {
            return {
                ok: false,
                message: e?.message || 'Streetmix 预览失败'
            };
        } finally {
            setLoading(false);
        }
    };

    const resetDefaultStreet = async () => {
        if (!defaultStreetId) {
            return {
                ok: false,
                message: '默认街道 ID 不存在'
            };
        }

        try {
            setLoading(true);
            const defaultScene = await fetchStreetScene(defaultStreetId);
            setSceneData(defaultScene);
            syncSceneTree(defaultScene);
            setStreetmixUrl('');

            return {
                ok: true,
                message: '已恢复默认街道'
            };
        } catch (e: any) {
            return {
                ok: false,
                message: e?.message || '默认街道加载失败'
            };
        } finally {
            setLoading(false);
        }
    };

    const value = useMemo(
        () => ({
            streetmixUrl,
            setStreetmixUrl,
            sceneData,
            setSceneData,
            loading,
            setLoading,
            defaultStreetId,
            setDefaultStreetId,
            previewStreetmix,
            resetDefaultStreet
        }),
        [streetmixUrl, sceneData, loading, defaultStreetId]
    );

    return <StreetmixContext.Provider value={value}>{children}</StreetmixContext.Provider>;
}

export function useStreetmix() {
    const context = useContext(StreetmixContext);
    if (!context) {
        throw new Error('useStreetmix must be used within StreetmixProvider');
    }
    return context;
}