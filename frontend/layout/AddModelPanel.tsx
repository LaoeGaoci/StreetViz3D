'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { Sidebar } from 'primereact/sidebar';
import { TabMenu } from 'primereact/tabmenu';
import { ScrollPanel } from 'primereact/scrollpanel';
import { Button } from 'primereact/button';
import { Card } from 'primereact/card';

interface Props {
    visible: boolean;
    onHide: () => void;
}

interface ModelItem {
    modelId: string;
    modelName: string;
    displayName: string;
    modelPreview: string;
}

interface CategoryItem {
    label: string;
    type: string;
}

const categories: CategoryItem[] = [
    { label: '🚦Streets & Intersections', type: 'Scene' },
    { label: '🌳Plants', type: 'Plant' },
    { label: '🪑Fixtures', type: 'Props' },
    { label: '🚗Vehicles', type: 'Vehicle' },
    { label: '🏢Buildings', type: 'Building' }
];

// 你的后端地址
const API_BASE_URL = 'http://localhost:8080';

export default function AddModelPanel({ visible, onHide }: Props) {
    const [activeIndex, setActiveIndex] = useState(0);
    const [models, setModels] = useState<ModelItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>('');

    const items = useMemo(
        () => categories.map((c) => ({ label: c.label })),
        []
    );

    const currentCategory = categories[activeIndex];
    const currentType = currentCategory.type;

    useEffect(() => {
        if (!visible) return;

        const fetchModels = async () => {
            setLoading(true);
            setError('');

            try {
                const response = await fetch(
                    `${API_BASE_URL}/model/type?type=${encodeURIComponent(currentType)}`,
                    {
                        method: 'GET',
                        headers: {
                            'Content-Type': 'application/json'
                        }
                    }
                );

                if (!response.ok) {
                    throw new Error(`请求失败，状态码：${response.status}`);
                }

                const data: ModelItem[] = await response.json();
                console.log('获取模型列表成功：', data);
                setModels(data);
            } catch (err) {
                console.error('获取模型列表失败：', err);
                setError('模型数据加载失败');
                setModels([]);
            } finally {
                setLoading(false);
            }
        };

        fetchModels();
    }, [visible, currentType]);

    return (
        <Sidebar
            visible={visible}
            position="bottom"
            onHide={onHide}
            className="streetviz-addmodel"
            showCloseIcon={false}
        >
            <div className="panel-header">
                <TabMenu
                    model={items}
                    activeIndex={activeIndex}
                    onTabChange={(e) => setActiveIndex(e.index)}
                />

                <div className="panel-close">
                    <Button
                        icon="pi pi-times"
                        className="p-button-primary"
                        onClick={onHide}
                    />
                </div>
            </div>

            <ScrollPanel className="model-scroll-panel">
                <div className="model-grid">
                    {loading && (
                        <div className="model-status">
                            正在加载模型...
                        </div>
                    )}

                    {!loading && error && (
                        <div className="model-status error">
                            {error}
                        </div>
                    )}

                    {!loading && !error && models.length === 0 && (
                        <div className="model-status">
                            当前分类下暂无模型
                        </div>
                    )}

                    {!loading &&
                        !error &&
                        models.map((m) => (
                            <Card
                                key={m.modelId}
                                className="model-card"
                                header={
                                    <div className="model-image-wrapper">
                                        <img
                                            src={m.modelPreview}
                                            alt={m.displayName}
                                            className="model-image"
                                        />
                                    </div>
                                }
                            >
                                <div className="model-title">
                                    {m.displayName}
                                </div>
                            </Card>
                        ))}
                </div>
            </ScrollPanel>
        </Sidebar>
    );
}