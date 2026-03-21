/* eslint-disable @next/next/no-img-element */
'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Password } from 'primereact/password';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { FileUpload, FileUploadHandlerEvent } from 'primereact/fileupload';
import AppTopbar from '@/layout/AppTopbar';
import { AppTopbarRef } from '@/types';

import {
    getUserInfo,
    updatePassword,
    uploadAvatarFile,
    UserInfo
} from '../../../api/auth';

interface MockWorkItem {
    id: string;
    title: string;
    cover: string;
    updatedAt: string;
    description: string;
}

const mockWorks: MockWorkItem[] = [
    {
        id: '1',
        title: 'Downtown Street Demo',
        cover: 'https://placehold.co/600x360?text=StreetViz3D+Work+1',
        updatedAt: '2026-03-21 12:30',
        description: '一个包含车道、绿化带和建筑布置的街道场景示例。'
    },
    {
        id: '2',
        title: 'Bike Lane Street',
        cover: 'https://placehold.co/600x360?text=StreetViz3D+Work+2',
        updatedAt: '2026-03-20 19:10',
        description: '带有自行车道与人行道结构的街道作品。'
    },
    {
        id: '3',
        title: 'Urban Avenue',
        cover: 'https://placehold.co/600x360?text=StreetViz3D+Work+3',
        updatedAt: '2026-03-18 09:45',
        description: '城市主干道可视化建模的阶段性成果。'
    },
    {
        id: '4',
        title: 'Intersection Test',
        cover: 'https://placehold.co/600x360?text=StreetViz3D+Work+4',
        updatedAt: '2026-03-15 14:20',
        description: '交叉路口与路侧设施组合测试。'
    }
];

export default function ProfilePage() {
    const toast = useRef<Toast>(null);
    const topbarRef = useRef<AppTopbarRef>(null);

    const [loading, setLoading] = useState(true);
    const [userInfo, setUserInfo] = useState<UserInfo | null>(null);

    const [passwordDialogVisible, setPasswordDialogVisible] = useState(false);
    const [savingPassword, setSavingPassword] = useState(false);
    const [savingAvatar, setSavingAvatar] = useState(false);

    const [oldPassword, setOldPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const loadUserInfo = async () => {
        const userId = localStorage.getItem('userId');

        if (!userId) {
            toast.current?.show({
                severity: 'warn',
                summary: '未登录',
                detail: '请先登录后再查看个人信息',
                life: 3000
            });
            setLoading(false);
            return;
        }

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
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadUserInfo();
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
            // 上传接口已经完成：
            // 1. 保存文件
            // 2. 更新数据库 avatar_url
            // 3. 返回完整头像 URL
            const avatarUrl = await uploadAvatarFile(userId, file);

            // 直接更新本地页面数据，避免必须整页重查一次才能看到新头像
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

            // 如需确保数据和后端完全一致，也可以保留这句重新拉取
            await loadUserInfo();
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

            await loadUserInfo();
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
                                    <div className="flex flex-column gap-3">
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
                    <div className="grid">
                        {mockWorks.map((work) => (
                            <div key={work.id} className="col-12 md:col-6 xl:col-4">
                                <div
                                    className="surface-border border-1 border-round overflow-hidden h-full"
                                    style={{ background: 'var(--surface-card)' }}
                                >
                                    <img
                                        src={work.cover}
                                        alt={work.title}
                                        style={{
                                            width: '100%',
                                            height: '180px',
                                            objectFit: 'cover',
                                            display: 'block'
                                        }}
                                    />
                                    <div className="p-3 flex flex-column gap-2">
                                        <div className="text-900 text-lg font-medium">
                                            {work.title}
                                        </div>
                                        <div className="text-600 text-sm">
                                            最近更新：{work.updatedAt}
                                        </div>
                                        <div className="text-700 line-height-3">
                                            {work.description}
                                        </div>
                                        <div className="pt-2">
                                            <Button
                                                label="查看作品"
                                                text
                                                icon="pi pi-arrow-right"
                                                iconPos="right"
                                            />
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </Card>
            </div>
        </div>
    );
}