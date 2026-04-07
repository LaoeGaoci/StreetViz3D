'use client';

/* eslint-disable @next/next/no-img-element */
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Password } from 'primereact/password';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { FileUpload, FileUploadHandlerEvent, FileUploadSelectEvent } from 'primereact/fileupload';
import { InputSwitch } from 'primereact/inputswitch';
import { Tag } from 'primereact/tag';
import { Divider } from 'primereact/divider';
import { useRouter } from 'next/navigation';
import AppTopbar from '@/layout/AppTopbar';
import { AppTopbarRef } from '@/types';
import { UserInfo, UserStreetItem } from '@/types/auth/user';
import { UserUploadedModelResponse } from '@/types/auth/userModel';
import {
    getUserInfo,
    updatePassword,
    uploadAvatarFile,
    getUserStreetList,
    getUserUploadedModelList,
    updateUserModelPublicStatus,
    updateUserModelPreview,
    uploadUserModelFiles
} from '@/app/api/auth';

export default function ProfilePage() {
    const toast = useRef<Toast>(null);
    const topbarRef = useRef<AppTopbarRef>(null);
    const router = useRouter();

    const [loading, setLoading] = useState(true);
    const [streetLoading, setStreetLoading] = useState(true);
    const [modelLoading, setModelLoading] = useState(true);

    const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
    const [streetList, setStreetList] = useState<UserStreetItem[]>([]);
    const [userModels, setUserModels] = useState<UserUploadedModelResponse[]>([]);

    const [passwordDialogVisible, setPasswordDialogVisible] = useState(false);
    const [modelDetailVisible, setModelDetailVisible] = useState(false);

    const [savingPassword, setSavingPassword] = useState(false);
    const [savingAvatar, setSavingAvatar] = useState(false);
    const [savingModelPublic, setSavingModelPublic] = useState(false);
    const [savingModelPreview, setSavingModelPreview] = useState(false);
    const [savingModelFiles, setSavingModelFiles] = useState(false);

    const [oldPassword, setOldPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const [selectedModel, setSelectedModel] = useState<UserUploadedModelResponse | null>(null);

    /**
     * 详情弹窗中用于“更新模型文件”的资源包：
     * - 1 个 .gltf
     * - 0 或 1 个 .bin
     * - 0~n 个贴图
     */
    const [replaceResourceFiles, setReplaceResourceFiles] = useState<File[]>([]);

    const parsedReplaceFiles = useMemo(() => {
        let gltfFile: File | null = null;
        let binFile: File | null = null;
        const textureFiles: File[] = [];

        replaceResourceFiles.forEach((file) => {
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

            if (
                name.endsWith('.png') ||
                name.endsWith('.jpg') ||
                name.endsWith('.jpeg') ||
                name.endsWith('.webp')
            ) {
                textureFiles.push(file);
            }
        });

        return {
            gltfFile,
            binFile,
            textureFiles,
            gltfCount: replaceResourceFiles.filter((file) => file.name.toLowerCase().endsWith('.gltf')).length,
            binCount: replaceResourceFiles.filter((file) => file.name.toLowerCase().endsWith('.bin')).length
        };
    }, [replaceResourceFiles]);

    const showToast = (severity: 'success' | 'error' | 'warn' | 'info', summary: string, detail: string) => {
        toast.current?.show({
            severity,
            summary,
            detail,
            life: 3000
        });
    };

    const loadUserInfo = async (userId: string) => {
        try {
            const data = await getUserInfo(userId);
            setUserInfo(data);
        } catch (error: any) {
            showToast('error', '加载失败', error?.message || '获取用户信息失败');
        }
    };

    const loadUserStreets = async (userId: string) => {
        try {
            setStreetLoading(true);
            const data = await getUserStreetList(userId);
            setStreetList(data || []);
        } catch (error: any) {
            showToast('error', '加载失败', error?.message || '获取用户街道列表失败');
            setStreetList([]);
        } finally {
            setStreetLoading(false);
        }
    };

    const loadUserModels = async (userId: string) => {
        try {
            setModelLoading(true);
            const data = await getUserUploadedModelList(userId);
            setUserModels(data || []);
        } catch (error: any) {
            showToast('error', '加载失败', error?.message || '获取用户模型列表失败');
            setUserModels([]);
        } finally {
            setModelLoading(false);
        }
    };

    const loadPageData = async () => {
        const userId = localStorage.getItem('userId');

        if (!userId) {
            showToast('warn', '未登录', '请先登录后再查看个人信息');
            setLoading(false);
            setStreetLoading(false);
            setModelLoading(false);
            return;
        }

        try {
            setLoading(true);
            await Promise.all([
                loadUserInfo(userId),
                loadUserStreets(userId),
                loadUserModels(userId)
            ]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadPageData();
    }, []);

    const handleAvatarUpload = async (event: FileUploadHandlerEvent) => {
        const userId = localStorage.getItem('userId');

        if (!userId) {
            showToast('warn', '未登录', '请先登录');
            return;
        }

        const file = event.files?.[0] as File | undefined;
        if (!file) {
            showToast('warn', '未选择文件', '请选择头像图片');
            return;
        }

        setSavingAvatar(true);

        try {
            const avatarUrl = await uploadAvatarFile(userId, file);

            setUserInfo((prev) => {
                if (!prev) return prev;
                return {
                    ...prev,
                    avatarUrl,
                    updatedAt: new Date().toISOString()
                };
            });

            showToast('success', '更新成功', '头像已更新');
            await loadUserInfo(userId);
        } catch (error: any) {
            showToast('error', '头像更新失败', error?.message || '头像更新失败');
        } finally {
            setSavingAvatar(false);
        }
    };

    const handleUpdatePassword = async () => {
        const userId = localStorage.getItem('userId');

        if (!userId) {
            showToast('warn', '未登录', '请先登录');
            return;
        }

        if (!oldPassword.trim() || !newPassword.trim() || !confirmPassword.trim()) {
            showToast('warn', '参数错误', '请完整填写密码信息');
            return;
        }

        if (newPassword !== confirmPassword) {
            showToast('warn', '两次密码不一致', '请重新确认新密码');
            return;
        }

        setSavingPassword(true);

        try {
            await updatePassword({
                userId,
                oldPassword,
                newPassword
            });

            showToast('success', '修改成功', '密码已更新');

            setOldPassword('');
            setNewPassword('');
            setConfirmPassword('');
            setPasswordDialogVisible(false);

            await loadUserInfo(userId);
        } catch (error: any) {
            showToast('error', '修改失败', error?.message || '修改密码失败');
        } finally {
            setSavingPassword(false);
        }
    };

    const handleViewStreet = (streetId: string) => {
        router.push(`/home?streetId=${encodeURIComponent(streetId)}`);
    };

    const handleOpenModelDetail = (model: UserUploadedModelResponse) => {
        setSelectedModel(model);
        setReplaceResourceFiles([]);
        setModelDetailVisible(true);
    };

    const handleToggleModelPublic = async (checked: boolean) => {
        const userId = localStorage.getItem('userId');
        if (!userId || !selectedModel) {
            showToast('warn', '未登录', '请先登录');
            return;
        }

        setSavingModelPublic(true);
        try {
            const updated = await updateUserModelPublicStatus({
                userId,
                uploadId: selectedModel.uploadId,
                isPublic: checked
            });

            setSelectedModel(updated);
            setUserModels((prev) =>
                prev.map((item) => (item.uploadId === updated.uploadId ? updated : item))
            );

            showToast('success', '修改成功', '模型公开状态已更新');
        } catch (error: any) {
            showToast('error', '修改失败', error?.message || '修改模型公开状态失败');
        } finally {
            setSavingModelPublic(false);
        }
    };

    const handleUpdateModelPreview = async (event: FileUploadHandlerEvent) => {
        const userId = localStorage.getItem('userId');
        const file = event.files?.[0] as File | undefined;

        if (!userId || !selectedModel) {
            showToast('warn', '未登录', '请先登录');
            return;
        }

        if (!file) {
            showToast('warn', '未选择文件', '请选择预览图');
            return;
        }

        setSavingModelPreview(true);
        try {
            const updated = await updateUserModelPreview(userId, selectedModel.uploadId, file);

            setSelectedModel(updated);
            setUserModels((prev) =>
                prev.map((item) => (item.uploadId === updated.uploadId ? updated : item))
            );

            showToast('success', '更新成功', '模型预览图已更新');
        } catch (error: any) {
            showToast('error', '更新失败', error?.message || '更新模型预览图失败');
        } finally {
            setSavingModelPreview(false);
        }
    };

    /**
     * 说明：
     * 当前后端仍没有“替换当前 uploadId 对应模型文件”的独立接口。
     * 因此这里继续采用过渡方案：
     * 使用同样的 metadata 重新上传一条新模型记录。
     */
    const handleReplaceModelFiles = async () => {
        const userId = localStorage.getItem('userId');

        if (!userId || !selectedModel) {
            showToast('warn', '未登录', '请先登录');
            return;
        }

        if (parsedReplaceFiles.gltfCount === 0 || !parsedReplaceFiles.gltfFile) {
            showToast('warn', '参数错误', '必须上传 1 个 .gltf 主文件');
            return;
        }

        if (parsedReplaceFiles.gltfCount > 1) {
            showToast('warn', '参数错误', '.gltf 主文件只能上传 1 个');
            return;
        }

        if (parsedReplaceFiles.binCount > 1) {
            showToast('warn', '参数错误', '.bin 文件最多只能上传 1 个');
            return;
        }

        setSavingModelFiles(true);
        try {
            const updated = await uploadUserModelFiles({
                userId,
                uploadId: selectedModel.uploadId,
                gltfFile: parsedReplaceFiles.gltfFile,
                binFile: parsedReplaceFiles.binFile,
                textureFiles: parsedReplaceFiles.textureFiles
            });

            setSelectedModel(updated);
            setUserModels((prev) =>
                prev.map((item) => (item.uploadId === updated.uploadId ? updated : item))
            );

            showToast('success', '更新成功', '模型文件已覆盖更新');
            setReplaceResourceFiles([]);
        } catch (error: any) {
            showToast('error', '更新失败', error?.message || '更新模型文件失败');
        } finally {
            setSavingModelFiles(false);
        }
    };

    const passwordDialogFooter = (
        <div>
            <Button
                label="取消"
                text
                onClick={() => setPasswordDialogVisible(false)}
                disabled={savingPassword}
            />
            <Button
                label={savingPassword ? '提交中...' : '确认修改'}
                icon="pi pi-check"
                onClick={handleUpdatePassword}
                loading={savingPassword}
            />
        </div>
    );

    const modelDetailFooter = (
        <div>
            <Button
                label="关闭"
                text
                onClick={() => setModelDetailVisible(false)}
                disabled={savingModelPublic || savingModelPreview || savingModelFiles}
            />
        </div>
    );

    return (
        <div className="p-4 md:p-3">
            <Toast ref={toast} position="bottom-right" />
            <AppTopbar ref={topbarRef} />

            <div className="p-4 md:p-6">
                <Dialog
                    visible={passwordDialogVisible}
                    modal
                    header="修改密码"
                    footer={passwordDialogFooter}
                    style={{ width: '32rem' }}
                    onHide={() => {
                        if (savingPassword) return;
                        setPasswordDialogVisible(false);
                    }}
                >
                    <div className="flex flex-column gap-3 mt-2">
                        <div>
                            <label className="block text-900 font-medium mb-2">旧密码</label>
                            <Password
                                value={oldPassword}
                                onChange={(e) => setOldPassword(e.target.value)}
                                placeholder="请输入旧密码"
                                feedback={false}
                                toggleMask
                                className="w-full"
                                inputClassName="w-full"
                            />
                        </div>

                        <div>
                            <label className="block text-900 font-medium mb-2">新密码</label>
                            <Password
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                placeholder="请输入新密码"
                                feedback={false}
                                toggleMask
                                className="w-full"
                                inputClassName="w-full"
                            />
                        </div>

                        <div>
                            <label className="block text-900 font-medium mb-2">确认新密码</label>
                            <Password
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                placeholder="请再次输入新密码"
                                feedback={false}
                                toggleMask
                                className="w-full"
                                inputClassName="w-full"
                            />
                        </div>
                    </div>
                </Dialog>

                <Dialog
                    visible={modelDetailVisible}
                    modal
                    header="模型详情"
                    footer={modelDetailFooter}
                    style={{ width: '48rem', maxWidth: '95vw' }}
                    onHide={() => {
                        if (savingModelPublic || savingModelPreview || savingModelFiles) return;
                        setModelDetailVisible(false);
                    }}
                >
                    {!selectedModel ? (
                        <div>暂无模型信息</div>
                    ) : (
                        <div className="flex flex-column gap-3">
                            {selectedModel.previewUrl ? (
                                <div>
                                    <img
                                        src={selectedModel.previewUrl}
                                        alt={selectedModel.displayName || selectedModel.modelName}
                                        style={{
                                            width: '240px',
                                            maxWidth: '100%',
                                            borderRadius: '8px',
                                            border: '1px solid var(--surface-border)'
                                        }}
                                    />
                                </div>
                            ) : null}

                            <div><strong>模型名称：</strong>{selectedModel.modelName}</div>
                            <div><strong>展示名称：</strong>{selectedModel.displayName || '-'}</div>
                            <div><strong>描述：</strong>{selectedModel.description || '-'}</div>
                            <div><strong>模型大类：</strong>{selectedModel.modelType || '-'}</div>
                            <div><strong>模型子类：</strong>{selectedModel.modelSubtype || '-'}</div>
                            <div><strong>模型格式：</strong>{selectedModel.modelFormat || '-'}</div>
                            <div><strong>模型文件：</strong>{selectedModel.modelUrl || '-'}</div>
                            <div><strong>Bin 文件：</strong>{selectedModel.binUrl || '-'}</div>
                            <div><strong>更新时间：</strong>{selectedModel.updatedAt || '-'}</div>

                            <Divider />

                            <div className="flex align-items-center gap-3">
                                <strong>公开度：</strong>
                                <InputSwitch
                                    checked={!!selectedModel.isPublic}
                                    onChange={(e) => handleToggleModelPublic(!!e.value)}
                                    disabled={savingModelPublic}
                                />
                                <span>{selectedModel.isPublic ? '公开' : '私有'}</span>
                            </div>

                            <Divider />

                            <div className="flex flex-column gap-2">
                                <strong>更新模型预览图片</strong>
                                <FileUpload
                                    mode="basic"
                                    name="previewFile"
                                    accept=".png,.jpg,.jpeg,.webp"
                                    maxFileSize={5_000_000}
                                    customUpload
                                    auto
                                    uploadHandler={handleUpdateModelPreview}
                                    chooseLabel={savingModelPreview ? '上传中...' : '选择新预览图'}
                                    disabled={savingModelPreview}
                                />
                            </div>

                            <Divider />

                            <div className="flex flex-column gap-3">
                                <strong>更新模型文件</strong>

                                <FileUpload
                                    name="replaceResourceFiles"
                                    multiple
                                    customUpload
                                    auto={false}
                                    accept=".gltf,.bin,.png,.jpg,.jpeg,.webp"
                                    emptyTemplate={
                                        <p className="m-0">
                                            拖拽 .gltf / .bin / 贴图文件到这里，或点击选择
                                        </p>
                                    }
                                    onSelect={(e: FileUploadSelectEvent) => {
                                        const newFiles = (e.files || []) as File[];
                                        setReplaceResourceFiles((prev) => {
                                            const merged = [...prev, ...newFiles];
                                            const uniqueMap = new Map<string, File>();
                                            merged.forEach((file) => {
                                                uniqueMap.set(file.name, file);
                                            });
                                            return Array.from(uniqueMap.values());
                                        });
                                    }}
                                />


                                <Button
                                    label={savingModelFiles ? '更新中...' : '覆盖当前模型文件'}
                                    icon="pi pi-upload"
                                    onClick={handleReplaceModelFiles}
                                    loading={savingModelFiles}
                                />
                            </div>
                        </div>
                    )}
                </Dialog>
            </div>

            <div className="flex flex-column gap-4">
                <Card title="个人信息">
                    {loading ? (
                        <div>加载中...</div>
                    ) : !userInfo ? (
                        <div>暂无用户信息</div>
                    ) : (
                        <div className="flex flex-column md:flex-row gap-5 align-items-start">
                            <div className="flex-shrink-0">
                                <img
                                    src={userInfo.avatarUrl}
                                    alt="用户头像"
                                    style={{
                                        width: '150px',
                                        height: '150px',
                                        borderRadius: '50%',
                                        objectFit: 'cover',
                                        border: '1px solid var(--surface-border)'
                                    }}
                                />
                            </div>

                            <div className="flex flex-column" style={{ minHeight: '150px', flex: 1 }}>
                                <div className="flex-1 flex align-items-center">
                                    <div className="flex flex-column gap-3 mt-4">
                                        <div>
                                            <div className="text-900 font-medium mb-2">用户名</div>
                                            <div className="text-700 text-lg">{userInfo.userName}</div>
                                        </div>

                                        <div>
                                            <div className="text-900 font-medium mb-2">邮箱</div>
                                            <div className="text-700">{userInfo.email}</div>
                                        </div>
                                    </div>
                                </div>

                                <div className="flex justify-content-end pt-3">
                                    <div className="flex flex-wrap gap-3 justify-content-end">
                                        <FileUpload
                                            mode="basic"
                                            name="avatar"
                                            accept="image/*"
                                            maxFileSize={1000000}
                                            customUpload
                                            auto
                                            uploadHandler={handleAvatarUpload}
                                            chooseLabel={savingAvatar ? '上传中...' : '修改头像'}
                                            disabled={savingAvatar || loading}
                                        />

                                        <Button
                                            label="修改密码"
                                            icon="pi pi-key"
                                            severity="warning"
                                            onClick={() => setPasswordDialogVisible(true)}
                                            disabled={loading}
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </Card>

                <Card title="我的模型">
                    {modelLoading ? (
                        <div>模型列表加载中...</div>
                    ) : userModels.length === 0 ? (
                        <div>当前用户还没有上传任何模型</div>
                    ) : (
                        <div className="grid">
                            {userModels.map((model) => (
                                <div key={model.uploadId} className="col-12 md:col-6 xl:col-4">
                                    <div
                                        className="surface-border border-1 border-round overflow-hidden h-full"
                                        style={{ background: 'var(--surface-card)' }}
                                    >
                                        {model.previewUrl ? (
                                            <div
                                                style={{
                                                    width: '100%',
                                                    height: '180px',
                                                    overflow: 'hidden',
                                                    borderBottom: '1px solid var(--surface-border)'
                                                }}
                                            >
                                                <img
                                                    src={model.previewUrl}
                                                    alt={model.displayName || model.modelName}
                                                    style={{
                                                        width: '100%',
                                                        height: '100%',
                                                        objectFit: 'cover'
                                                    }}
                                                />
                                            </div>
                                        ) : (
                                            <div
                                                className="flex align-items-center justify-content-center text-500"
                                                style={{
                                                    width: '100%',
                                                    height: '180px',
                                                    borderBottom: '1px solid var(--surface-border)'
                                                }}
                                            >
                                                无预览图
                                            </div>
                                        )}

                                        <div className="p-3 flex flex-column gap-2">
                                            <div className="flex align-items-center justify-content-between gap-2">
                                                <div className="text-900 text-lg font-medium">
                                                    {model.displayName || model.modelName}
                                                </div>
                                                <Tag
                                                    value={model.isPublic ? '公开' : '私有'}
                                                    severity={model.isPublic ? 'success' : 'warning'}
                                                />
                                            </div>

                                            <div className="text-600 text-sm">
                                                类型: {model.modelType || '-'} / {model.modelSubtype || '-'}
                                            </div>

                                            <div className="text-600 text-sm">
                                                最近更新: {model.updatedAt || '-'}
                                            </div>

                                            <div className="pt-2">
                                                <Button
                                                    label="详情"
                                                    text
                                                    icon="pi pi-eye"
                                                    onClick={() => handleOpenModelDetail(model)}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </Card>

                <Card title="用户作品">
                    {streetLoading ? (
                        <div>街道列表加载中...</div>
                    ) : streetList.length === 0 ? (
                        <div>当前用户还没有保存任何街道</div>
                    ) : (
                        <div className="grid">
                            {streetList.map((street) => (
                                <div key={street.streetId} className="col-12 md:col-6 xl:col-4">
                                    <div
                                        className="surface-border border-1 border-round overflow-hidden h-full"
                                        style={{ background: 'var(--surface-card)' }}
                                    >
                                        <div className="p-3 flex flex-column gap-2">
                                            <div className="text-900 text-lg font-medium">
                                                {street.streetName || '未命名街道'}
                                            </div>

                                            <div className="text-600 text-sm">
                                                街道ID: {street.streetId}
                                            </div>

                                            <div className="text-600 text-sm">
                                                宽度: {street.width ?? '-'}
                                            </div>

                                            <div className="text-600 text-sm">
                                                最近更新: {street.updatedAt || '-'}
                                            </div>

                                            <div className="pt-2">
                                                <Button
                                                    label="查看作品"
                                                    text
                                                    icon="pi pi-arrow-right"
                                                    iconPos="right"
                                                    onClick={() => handleViewStreet(street.streetId)}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </Card>
            </div>
        </div>
    );
}