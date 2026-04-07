'use client';

import React, { useMemo, useRef, useState } from 'react';
import { Dialog } from 'primereact/dialog';
import { Toast } from 'primereact/toast';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { Checkbox } from 'primereact/checkbox';
import { Button } from 'primereact/button';
import { FileUpload, FileUploadSelectEvent } from 'primereact/fileupload';
import { createUserModelDraft, uploadUserModelFiles } from '@/app/api/auth';

interface Props {
    visible: boolean;
    onHide: () => void;
    onUploaded?: () => void;
}

const IMAGE_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.webp'];

function hasExtension(fileName: string, extensions: string[]) {
    const lower = fileName.toLowerCase();
    return extensions.some((ext) => lower.endsWith(ext));
}

export default function UserModelUploadDialog({ visible, onHide, onUploaded }: Props) {
    const toast = useRef<Toast>(null);

    const [uploading, setUploading] = useState(false);

    const [modelName, setModelName] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [description, setDescription] = useState('');
    const [modelType, setModelType] = useState('');
    const [modelSubtype, setModelSubtype] = useState('');
    const [isPublic, setIsPublic] = useState(false);

    /**
     * 模型资源包：
     * - 1 个 .gltf
     * - 0 或 1 个 .bin
     * - 0~n 个贴图图片
     */
    const [resourceFiles, setResourceFiles] = useState<File[]>([]);

    /**
     * 预览图单独处理
     */
    const [previewFile, setPreviewFile] = useState<File | null>(null);

    const parsedFiles = useMemo(() => {
        let gltfFile: File | null = null;
        let binFile: File | null = null;
        const textureFiles: File[] = [];
        const unsupportedFiles: File[] = [];

        resourceFiles.forEach((file) => {
            const name = file.name.toLowerCase();

            if (name.endsWith('.gltf')) {
                if (!gltfFile) {
                    gltfFile = file;
                }
                return;
            }

            if (name.endsWith('.bin')) {
                if (!binFile) {
                    binFile = file;
                }
                return;
            }

            if (hasExtension(name, IMAGE_EXTENSIONS)) {
                textureFiles.push(file);
                return;
            }

            unsupportedFiles.push(file);
        });

        return {
            gltfFile,
            binFile,
            textureFiles,
            unsupportedFiles
        };
    }, [resourceFiles]);

    const showToast = (
        severity: 'success' | 'error' | 'warn' | 'info',
        summary: string,
        detail: string
    ) => {
        toast.current?.show({
            severity,
            summary,
            detail,
            life: 3000
        });
    };

    const resetForm = () => {
        setModelName('');
        setDisplayName('');
        setDescription('');
        setModelType('');
        setModelSubtype('');
        setIsPublic(false);
        setResourceFiles([]);
        setPreviewFile(null);
    };

    const safeClose = () => {
        if (uploading) {
            return;
        }
        resetForm();
        onHide();
    };

    const validateBeforeUpload = () => {
        const userId = localStorage.getItem('userId');

        if (!userId) {
            showToast('warn', '未登录', '请先登录');
            return null;
        }

        if (!modelName.trim()) {
            showToast('warn', '参数错误', '请填写模型名称');
            return null;
        }

        if (parsedFiles.unsupportedFiles.length > 0) {
            showToast(
                'warn',
                '文件格式错误',
                `存在不支持的文件：${parsedFiles.unsupportedFiles.map((f) => f.name).join('，')}`
            );
            return null;
        }

        const gltfCount = resourceFiles.filter((file) => file.name.toLowerCase().endsWith('.gltf')).length;
        const binCount = resourceFiles.filter((file) => file.name.toLowerCase().endsWith('.bin')).length;

        if (gltfCount === 0 || !parsedFiles.gltfFile) {
            showToast('warn', '参数错误', '必须上传 1 个 .gltf 主文件');
            return null;
        }

        if (gltfCount > 1) {
            showToast('warn', '参数错误', '.gltf 主文件只能上传 1 个');
            return null;
        }

        if (binCount > 1) {
            showToast('warn', '参数错误', '.bin 文件最多只能上传 1 个');
            return null;
        }

        if (previewFile && !hasExtension(previewFile.name, IMAGE_EXTENSIONS)) {
            showToast('warn', '参数错误', '预览图仅支持 png、jpg、jpeg、webp');
            return null;
        }

        return userId;
    };

    const handleUpload = async () => {
        if (uploading) {
            return;
        }

        const userId = validateBeforeUpload();
        if (!userId) {
            return;
        }

        try {
            setUploading(true);

            const draft = await createUserModelDraft({
                userId,
                modelName: modelName.trim(),
                displayName: displayName.trim(),
                description: description.trim(),
                modelType: modelType.trim(),
                modelSubtype: modelSubtype.trim(),
                isPublic
            });

            await uploadUserModelFiles({
                userId,
                uploadId: draft.uploadId,
                gltfFile: parsedFiles.gltfFile!,
                binFile: parsedFiles.binFile,
                textureFiles: parsedFiles.textureFiles,
                previewFile
            });

            showToast('success', '上传成功', '用户模型已上传');
            resetForm();
            onHide();
            onUploaded?.();
        } catch (error: any) {
            showToast('error', '上传失败', error?.message || '模型上传失败');
        } finally {
            setUploading(false);
        }
    };

    const footer = (
        <div>
            <Button
                label="取消"
                text
                onClick={safeClose}
                disabled={uploading}
            />
            <Button
                label={uploading ? '上传中...' : '确认上传'}
                icon="pi pi-upload"
                onClick={handleUpload}
                loading={uploading}
                disabled={uploading}
            />
        </div>
    );

    return (
        <>
            <Toast ref={toast} position="bottom-right" />

            <Dialog
                visible={visible}
                modal
                header="上传用户模型"
                footer={footer}
                style={{ width: '42rem', maxWidth: '95vw' }}
                onHide={safeClose}
            >
                <div className="p-fluid flex flex-column gap-3">
                    <div>
                        <label className="block text-900 font-medium mb-2">模型名称（必填）</label>
                        <InputText
                            value={modelName}
                            onChange={(e) => setModelName(e.target.value)}
                            placeholder="例如：custom_house_01"
                        />
                    </div>

                    <div>
                        <label className="block text-900 font-medium mb-2">展示名称</label>
                        <InputText
                            value={displayName}
                            onChange={(e) => setDisplayName(e.target.value)}
                            placeholder="例如：我的住宅模型"
                        />
                    </div>

                    <div>
                        <label className="block text-900 font-medium mb-2">描述</label>
                        <InputTextarea
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            rows={3}
                            autoResize
                        />
                    </div>

                    <div>
                        <label className="block text-900 font-medium mb-2">模型大类</label>
                        <InputText
                            value={modelType}
                            onChange={(e) => setModelType(e.target.value)}
                            placeholder="例如：Building / Vehicle / Props"
                        />
                    </div>

                    <div>
                        <label className="block text-900 font-medium mb-2">模型子类</label>
                        <InputText
                            value={modelSubtype}
                            onChange={(e) => setModelSubtype(e.target.value)}
                            placeholder="例如：residential / car / bench"
                        />
                    </div>

                    <div className="flex align-items-center gap-2">
                        <Checkbox
                            inputId="user-model-public"
                            checked={isPublic}
                            onChange={(e) => setIsPublic(!!e.checked)}
                        />
                        <label htmlFor="user-model-public">是否公开</label>
                    </div>

                    <div>
                        <label className="block text-900 font-medium mb-2">
                            模型资源包（.gltf / .bin / 贴图）
                        </label>
                        <FileUpload
                            name="resourceFiles"
                            multiple
                            customUpload
                            auto={false}
                            accept=".gltf,.bin,.png,.jpg,.jpeg,.webp"
                            chooseLabel="选择资源文件"
                            uploadOptions={{ style: { display: 'none' } }}
                            cancelOptions={{ style: { display: 'none' } }}
                            emptyTemplate={
                                <p className="m-0">
                                    拖拽 .gltf / .bin / 贴图文件到这里，或点击选择
                                </p>
                            }
                            onSelect={(e: FileUploadSelectEvent) => {
                                const newFiles = (e.files || []) as File[];

                                setResourceFiles((prev) => {
                                    const merged = [...prev, ...newFiles];
                                    const uniqueMap = new Map<string, File>();

                                    merged.forEach((file) => {
                                        uniqueMap.set(file.name, file);
                                    });

                                    return Array.from(uniqueMap.values());
                                });
                            }}
                            onClear={() => {
                                setResourceFiles([]);
                            }}
                        />
                    </div>

                    <div>
                        <label className="block text-900 font-medium mb-2">预览图（可选）</label>
                        <FileUpload
                            mode="basic"
                            name="previewFile"
                            accept=".png,.jpg,.jpeg,.webp"
                            customUpload
                            chooseLabel={previewFile ? previewFile.name : '选择预览图'}
                            onSelect={(e: FileUploadSelectEvent) => {
                                const file = e.files?.[0] as File | undefined;
                                setPreviewFile(file || null);
                            }}
                        />
                    </div>
                </div>
            </Dialog>
        </>
    );
}