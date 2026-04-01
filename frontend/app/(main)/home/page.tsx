'use client';

import React, { useEffect, useState } from 'react';
import ModelContainer from '../../../layout/ModelContainer';
import { useRouter, useSearchParams } from 'next/navigation';
import { getUserIdFromEmail } from '../../api/auth';

export default function Page() {
    const router = useRouter();
    const searchParams = useSearchParams();

    const [userId, setUserId] = useState('');
    const [ready, setReady] = useState(false);

    const streetId: string = searchParams.get('streetId') || 'a49f3eea-b0e6-40a1-a537-13ad39561543';

    useEffect(() => {
        const init = async () => {
            try {
                const id = await getUserIdFromEmail();
                setUserId(id);
                console.log('用户ID:', id);
                setReady(true);
            } catch (error) {
                router.replace('/auth/login');
            }
        };

        init();
    }, [router]);

    if (!ready) {
        return <div style={{ padding: '24px' }}>加载中...</div>;
    }

    return <ModelContainer streetId={streetId} />;
}