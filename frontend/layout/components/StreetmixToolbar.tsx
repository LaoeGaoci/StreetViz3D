'use client';

import React, { useRef } from 'react';
import { InputText } from 'primereact/inputtext';
import { Button } from 'primereact/button';
import { Toast } from 'primereact/toast';
import { useStreetmix } from '../context/streetmixcontext';

export default function StreetmixTopbarToolbar() {
    const toastRef = useRef<Toast>(null);

    const {
        streetmixUrl,
        setStreetmixUrl,
        loading,
        previewStreetmix,
        resetDefaultStreet
    } = useStreetmix();

    const handlePreview = async () => {
        const result = await previewStreetmix();

        toastRef.current?.show({
            severity: result.ok ? 'success' : 'error',
            summary: result.ok ? '成功' : '错误',
            detail: result.message,
            life: 2500
        });
    };

    const handleReset = async () => {
        const result = await resetDefaultStreet();

        toastRef.current?.show({
            severity: result.ok ? 'success' : 'error',
            summary: result.ok ? '成功' : '错误',
            detail: result.message,
            life: 2500
        });
    };

    return (
        <>
            <Toast ref={toastRef} position="top-center" />

            <div className="streetmix-topbar-toolbar">
                <InputText
                    value={streetmixUrl}
                    onChange={(e) => setStreetmixUrl(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                            handlePreview();
                        }
                    }}
                    placeholder="请输入 Streetmix URL"
                    disabled={loading}
                    className="streetmix-topbar-toolbar__input"
                />

                <Button
                    label="确定"
                    onClick={handlePreview}
                    loading={loading}
                    className="streetmix-topbar-toolbar__confirm"
                />

                <Button
                    label="默认街道"
                    onClick={handleReset}
                    disabled={loading}
                    severity="secondary"
                    outlined
                    className="streetmix-topbar-toolbar__reset"
                />
            </div>
        </>
    );
}