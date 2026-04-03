'use client';

import React, { useEffect, useState } from 'react';
import ModelContainer from '../../../layout/ModelContainer';
import { useRouter, useSearchParams } from 'next/navigation';
import { getUserIdFromEmail } from '../../api/auth';
import { fetchStreetScene, fetchStreetSceneFromStreetmix, StreetSceneDTO } from '../../api/street';

export default function Page() {
    const router = useRouter();
    const searchParams = useSearchParams();

    const [ready, setReady] = useState(false);
    const [sceneData, setSceneData] = useState<StreetSceneDTO | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [streetmixUrl, setStreetmixUrl] = useState('');

    const defaultStreetId =
        searchParams.get('streetId') || 'a49f3eea-b0e6-40a1-a537-13ad39561543';

    useEffect(() => {
        const init = async () => {
            try {
                await getUserIdFromEmail();
                setReady(true);

                const defaultScene = await fetchStreetScene(defaultStreetId);
                setSceneData(defaultScene);
            } catch (e: any) {
                router.replace('/auth/login');
                return;
            } finally {
                setLoading(false);
            }
        };

        init();
    }, [router, defaultStreetId]);

    const handlePreviewStreetmix = async () => {
        if (!streetmixUrl.trim()) {
            setError('请输入 Streetmix URL');
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const scene = await fetchStreetSceneFromStreetmix(streetmixUrl.trim());
            //console.log('从 Streetmix 获取的场景数据:', scene);
            setSceneData(scene);
        } catch (e: any) {
            setError(e?.message || 'Streetmix 预览失败');
        } finally {
            setLoading(false);
        }
    };

    const handleResetDefaultStreet = async () => {
        try {
            setLoading(true);
            setError(null);

            const defaultScene = await fetchStreetScene(defaultStreetId);
            setSceneData(defaultScene);
            setStreetmixUrl('');
        } catch (e: any) {
            setError(e?.message || '默认街道加载失败');
        } finally {
            setLoading(false);
        }
    };

    if (!ready || loading && !sceneData) {
        return <div style={{ padding: '24px' }}>加载中...</div>;
    }

    return (
        <div
            style={{
                padding: '24px',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                gap: '16px'
            }}
        >
            <div style={{ display: 'flex', gap: '12px' }}>
                <input
                    type="text"
                    value={streetmixUrl}
                    onChange={(e) => setStreetmixUrl(e.target.value)}
                    placeholder="请输入 Streetmix URL"
                    style={{
                        flex: 1,
                        height: '42px',
                        padding: '0 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '10px',
                        outline: 'none'
                    }}
                />
                <button
                    onClick={handlePreviewStreetmix}
                    style={{
                        height: '42px',
                        padding: '0 18px',
                        border: 'none',
                        borderRadius: '10px',
                        background: '#2563eb',
                        color: '#fff',
                        cursor: 'pointer'
                    }}
                >
                    确定
                </button>
                <button
                    onClick={handleResetDefaultStreet}
                    style={{
                        height: '42px',
                        padding: '0 18px',
                        border: '1px solid #d1d5db',
                        borderRadius: '10px',
                        background: '#fff',
                        cursor: 'pointer'
                    }}
                >
                    默认街道
                </button>
            </div>

            {error && (
                <div style={{ color: '#dc2626', fontSize: '14px' }}>
                    {error}
                </div>
            )}

            <div style={{ flex: 1, minHeight: 520 }}>
                {sceneData ? (
                    <ModelContainer sceneData={sceneData} />
                ) : (
                    <div style={{ padding: '24px' }}>暂无场景数据</div>
                )}
            </div>
        </div>
    );
}