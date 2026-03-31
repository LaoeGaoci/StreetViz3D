'use client';

import React from 'react';
import ModelContainer from '../../../layout/ModelContainer';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getUserIdFromEmail } from '../../api/auth';


export default function Page() {
    const router = useRouter();
    const [userId, setUserId] = useState('');

    useEffect(() => {
        const init = async () => {
            try {
                const id = await getUserIdFromEmail();
                setUserId(id);
                console.log('用户ID:', id);
            } catch (error) {
                router.replace('/auth/login');
            }
        };

        init();
    }, [router]);
    return <ModelContainer streetId="a49f3eea-b0e6-40a1-a537-13ad39561543" />;
}