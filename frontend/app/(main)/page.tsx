'use client';

import React from 'react';
import ModelContainer from '../../layout/ModelContainer';
import { redirect } from 'next/navigation';

export default function Page() {
    redirect('/auth/login');
}