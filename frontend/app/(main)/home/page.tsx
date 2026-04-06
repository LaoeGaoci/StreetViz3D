'use client';

import React, { useEffect, useState } from 'react';
import ModelContainer from '@/layout/ModelContainer';
import { useRouter, useSearchParams } from 'next/navigation';
import { getUserIdFromEmail } from '@/app/api/auth';
import { fetchStreetScene } from '@/app/api/street';
import { useStreetmix } from '@/layout/context/streetmixcontext';

export default function Page() {
    const router = useRouter();
    const searchParams = useSearchParams();

    const [ready, setReady] = useState(false);

    const {
        sceneData,
        setSceneData,
        loading,
        setLoading,
        setDefaultStreetId
    } = useStreetmix();

    const defaultStreetId =
        searchParams.get('streetId') || 'a49f3eea-b0e6-40a1-a537-13ad39561543';

    useEffect(() => {
        const init = async () => {
            try {
                await getUserIdFromEmail();
                setReady(true);
                setDefaultStreetId(defaultStreetId);

                setLoading(true);
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
    }, [router, defaultStreetId, setDefaultStreetId, setLoading, setSceneData]);

    if (!ready || (loading && !sceneData)) {
        return <div>加载中...</div>;
    }

    return (
        <div
            style={{
                height: '100%'
            }}
        >
            <div style={{ height: '100%', minHeight: 520 }}>
                {sceneData ? (
                    <ModelContainer sceneData={sceneData} />
                ) : (
                    <div>暂无场景数据</div>
                )}
            </div>
        </div>
    );
}