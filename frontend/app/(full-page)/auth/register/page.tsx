/* eslint-disable @next/next/no-img-element */
'use client';

import { useRouter } from 'next/navigation';
import React, { useContext, useRef, useState } from 'react';
import { Button } from 'primereact/button';
import { Password } from 'primereact/password';
import { Toast } from 'primereact/toast';
import { LayoutContext } from '../../../../layout/context/layoutcontext';
import { InputText } from 'primereact/inputtext';
import { classNames } from 'primereact/utils';

const API_BASE_URL = 'http://localhost:8080';

const RegisterPage = () => {
    const [userName, setUserName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);

    const toast = useRef<Toast>(null);

    const { layoutConfig } = useContext(LayoutContext);
    const router = useRouter();

    const containerClassName = classNames(
        'surface-ground flex align-items-center justify-content-center min-h-screen min-w-screen overflow-hidden',
        { 'p-input-filled': layoutConfig.inputStyle === 'filled' }
    );

    const handleRegister = async () => {
        setLoading(true);

        try {
            const registerResponse = await fetch(`${API_BASE_URL}/user/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    userName,
                    email,
                    password
                })
            });

            const registerResult = await registerResponse.json();

            if (!registerResponse.ok || registerResult.code !== 200) {
                throw new Error(registerResult.message || '注册失败');
            }

            const loginResponse = await fetch(`${API_BASE_URL}/user/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    email,
                    password
                })
            });

            const loginResult = await loginResponse.json();

            if (!loginResponse.ok || loginResult.code !== 200) {
                throw new Error(loginResult.message || '自动登录失败，请手动登录');
            }

            const loginData = loginResult.data;

            if (typeof window !== 'undefined') {
                localStorage.setItem('userId', loginData.userId);
                localStorage.setItem('userName', loginData.userName);
                localStorage.setItem('email', loginData.email);
            }

            router.push('/');
        } catch (error: any) {
            toast.current?.show({
                severity: 'error',
                summary: '操作失败',
                detail: error.message || '注册失败',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={containerClassName}>
            <Toast ref={toast} position="top-center" />

            <div className="flex flex-column align-items-center justify-content-center">
                <img
                    src={`/layout/images/logo-${layoutConfig.colorScheme === 'light' ? 'dark' : 'white'}.png`}
                    alt="StreetViz3D 标志"
                    className="mb-5 w-6rem flex-shrink-0"
                />
                <div
                    style={{
                        borderRadius: '56px',
                        padding: '0.3rem',
                        background:
                            'linear-gradient(180deg, var(--primary-color) 10%, rgba(33, 150, 243, 0) 30%)'
                    }}
                >
                    <div
                        className="w-full surface-card py-8 px-5 sm:px-8"
                        style={{ borderRadius: '53px' }}
                    >
                        <div className="text-center mb-5">
                            <div className="text-900 text-3xl font-medium mb-3">
                                StreetViz3D
                            </div>
                        </div>

                        <div>
                            <label className="block text-900 text-xl font-medium mb-2">
                                用户名
                            </label>
                            <InputText
                                value={userName}
                                onChange={(e) => setUserName(e.target.value)}
                                placeholder="请输入用户名"
                                className="w-full md:w-30rem mb-5"
                                style={{ padding: '1rem' }}
                            />

                            <label className="block text-900 text-xl font-medium mb-2">
                                邮箱
                            </label>
                            <InputText
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                placeholder="请输入邮箱地址"
                                className="w-full md:w-30rem mb-5"
                                style={{ padding: '1rem' }}
                            />

                            <label className="block text-900 font-medium text-xl mb-2">
                                密码
                            </label>
                            <Password
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                placeholder="请输入密码"
                                toggleMask
                                feedback={false}
                                className="w-full mb-5"
                                inputClassName="w-full p-3 md:w-30rem"
                            />

                            <div className="flex gap-3">
                                <Button
                                    label={loading ? '注册中...' : '注册'}
                                    className="flex-1 p-3 text-xl"
                                    onClick={handleRegister}
                                    disabled={loading}
                                />
                                <Button
                                    label="返回"
                                    className="flex-1 p-3 text-xl p-button-outlined"
                                    onClick={() => router.push('/auth/login')}
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default RegisterPage;