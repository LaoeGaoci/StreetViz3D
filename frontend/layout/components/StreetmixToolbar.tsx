'use client';

import React, { useRef, useState } from 'react';
import { InputText } from 'primereact/inputtext';
import { Button } from 'primereact/button';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { FileUpload } from 'primereact/fileupload';
import { useStreetmix } from '../context/streetmixcontext';


export default function StreetmixTopbarToolbar() {
    const toastRef = useRef<Toast>(null);
    const [visible, setVisible] = useState(false);

    const {
        streetmixUrl,
        setStreetmixUrl,
        loading,
        previewStreetmix,
        resetDefaultStreet,
        setSceneData
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
                        if (e.key === 'Enter') handlePreview();
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

                {/* 摄像头按钮 */}
                <Button
                    icon="pi pi-camera"
                    onClick={() => setVisible(true)}
                    className="streetmix-topbar-toolbar__camera"
                    tooltip="上传街道图片"
                    tooltipOptions={{ position: 'bottom' }}
                />
            </div>

            {/* 弹窗 */}
            <Dialog
                header="上传街道图片"
                visible={visible}
                modal
                style={{ width: '50vw' }}
                onHide={() => setVisible(false)}
            >
                <FileUpload
                    name="file"
                    accept="image/*"
                    maxFileSize={10 * 1024 * 1024}
                    customUpload
                    uploadHandler={async (event) => {
                        const file = event.files?.[0];
                        if (!file) return;

                        try {
                            const formData = new FormData();
                            formData.append('file', file);

                            const response = await fetch('http://localhost:8080/api/street/predict', {
                                method: 'POST',
                                body: formData
                            });

                            if (!response.ok) throw new Error(response.statusText);

                            const data = await response.json();
                            console.log('预测结果:', data.scene);
                            setSceneData(data.scene);
                            toastRef.current?.show({
                                severity: 'success',
                                summary: '上传成功',
                                life: 2500
                            });
                            setVisible(false);
                        } catch (err) {
                            toastRef.current?.show({
                                severity: 'error',
                                summary: '上传失败',
                                detail: String(err),
                                life: 3000
                            });
                        }
                    }}
                />
            </Dialog>
        </>
    );
}