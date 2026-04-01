/* eslint-disable @next/next/no-img-element */
'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Password } from 'primereact/password';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { FileUpload, FileUploadHandlerEvent } from 'primereact/fileupload';
import { useRouter } from 'next/navigation';
import AppTopbar from '@/layout/AppTopbar';
import { AppTopbarRef } from '@/types';

import {
    getUserInfo,
    updatePassword,
    uploadAvatarFile,
    getUserStreetList,
    UserInfo,
    UserStreetItem
} from '../../../api/auth';

export default function ProfilePage() {
    const toast = useRef<Toast>(null);
    const topbarRef = useRef<AppTopbarRef>(null);
    const router = useRouter();

    const [loading, setLoading] = useState(true);
    const [streetLoading, setStreetLoading] = useState(true);

    const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
    const [streetList, setStreetList] = useState<UserStreetItem[]>([]);

    const [passwordDialogVisible, setPasswordDialogVisible] = useState(false);
    const [savingPassword, setSavingPassword] = useState(false);
    const [savingAvatar, setSavingAvatar] = useState(false);

    const [oldPassword, setOldPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const loadUserInfo = async (userId: string) => {
        try {
            const data = await getUserInfo(userId);
            setUserInfo(data);
        } catch (error: any) {
            toast.current?.show({
                severity: 'error',
                summary: '加载失败',
                detail: error?.message || '获取用户信息失败',
                life: 3000
            });
        }
    };

    const loadUserStreets = async (userId: string) => {
        try {
            setStreetLoading(true);
            const data = await getUserStreetList(userId);
            setStreetList(data || []);
        } catch (error: any) {
            toast.current?.show({
                severity: 'error',
                summary: '加载失败',
                detail: error?.message || '获取用户街道列表失败',
                life: 3000
            });
            setStreetList([]);
        } finally {
            setStreetLoading(false);
        }
    };

    const loadPageData = async () => {
        const userId = localStorage.getItem('userId');

        if (!userId) {
            toast.current?.show({
                severity: 'warn',
                summary: '未登录',
                detail: '请先登录后再查看个人信息',
                life: 3000
            });
            setLoading(false);
            setStreetLoading(false);
            return;
        }

        try {
            setLoading(true);
            await Promise.all([
                loadUserInfo(userId),
                loadUserStreets(userId)
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
            toast.current?.show({
                severity: 'warn',
                summary: '未登录',
                detail: '请先登录',
                life: 3000
            });
            return;
        }

        const file = event.files?.[0] as File | undefined;
        if (!file) {
            toast.current?.show({
                severity: 'warn',
                summary: '未选择文件',
                detail: '请选择头像图片',
                life: 3000
            });
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

            toast.current?.show({
                severity: 'success',
                summary: '更新成功',
                detail: '头像已更新',
                life: 2000
            });

            await loadUserInfo(userId);
        } catch (error: any) {
            toast.current?.show({
                severity: 'error',
                summary: '头像更新失败',
                detail: error?.message || '头像更新失败',
                life: 3000
            });
        } finally {
            setSavingAvatar(false);
        }
    };

    const handleUpdatePassword = async () => {
        const userId = localStorage.getItem('userId');

        if (!userId) {
            toast.current?.show({
                severity: 'warn',
                summary: '未登录',
                detail: '请先登录',
                life: 3000
            });
            return;
        }

        if (!oldPassword.trim() || !newPassword.trim() || !confirmPassword.trim()) {
            toast.current?.show({
                severity: 'warn',
                summary: '参数错误',
                detail: '请完整填写密码信息',
                life: 3000
            });
            return;
        }

        if (newPassword !== confirmPassword) {
            toast.current?.show({
                severity: 'warn',
                summary: '两次密码不一致',
                detail: '请重新确认新密码',
                life: 3000
            });
            return;
        }

        setSavingPassword(true);

        try {
            await updatePassword({
                userId,
                oldPassword,
                newPassword
            });

            toast.current?.show({
                severity: 'success',
                summary: '修改成功',
                detail: '密码已更新',
                life: 2000
            });

            setOldPassword('');
            setNewPassword('');
            setConfirmPassword('');
            setPasswordDialogVisible(false);

            await loadUserInfo(userId);
        } catch (error: any) {
            toast.current?.show({
                severity: 'error',
                summary: '修改失败',
                detail: error?.message || '修改密码失败',
                life: 3000
            });
        } finally {
            setSavingPassword(false);
        }
    };

    // 跳转到街道编辑与显示页面，传递 streetId 参数
    const handleViewStreet = (streetId: string) => {
        router.push(`/home?streetId=${encodeURIComponent(streetId)}`);
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

                            <div
                                className="flex flex-column"
                                style={{ minHeight: '150px', flex: 1 }}
                            >
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
                                        {/* <div
                                            style={{
                                                width: '100%',
                                                height: '180px',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                background: 'var(--surface-100)',
                                                color: 'var(--text-color-secondary)',
                                                fontSize: '1.1rem',
                                                fontWeight: 600
                                            }}
                                        >
                                            Street Preview
                                        </div> */}

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