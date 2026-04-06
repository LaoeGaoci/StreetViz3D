'use client';

import React from 'react';
import { Sidebar } from 'primereact/sidebar';
import { SelectionPayload } from '../utils/aframe-selection';

interface Props {
    visible: boolean;
    selected: SelectionPayload | null;
    onHide: () => void;
}

function num(value?: number | null) {
    if (value == null || Number.isNaN(value)) return '-';
    return value.toFixed(2);
}

export default function SelectionInfoPanel({
    visible,
    selected,
    onHide
}: Props) {
    return (
        <Sidebar
            visible={visible}
            position="right"
            onHide={onHide}
            className="selection-info-sidebar"
            header="模型信息"
            modal={false}
            blockScroll={false}
            dismissable={false}
        >
            {!selected ? (
                <div className="selection-info-empty">
                    <div className="selection-info-empty__title">当前未选中模型</div>
                    <div>点击场景中的车辆、树木、建筑或边界模型。</div>
                </div>
            ) : (
                <div className="selection-info-content">
                    <div className="selection-info-section selection-info-section--first">
                        <div className="selection-info-section__title">基础信息</div>
                    </div>
                    
                    <div className="selection-info-row"><strong>kind：</strong>{selected.meta.kind}</div>
                    <div className="selection-info-row"><strong>semanticType：</strong>{selected.meta.semanticType || '-'}</div>
                    <div className="selection-info-row"><strong>displayName：</strong>{selected.meta.displayName || '-'}</div>
                    <div className="selection-info-row"><strong>modelUrl：</strong>{selected.meta.modelUrl || '-'}</div>

                    <div className="selection-info-section">
                        <div className="selection-info-section__title">包围盒尺寸</div>
                    </div>

                    <div className="selection-info-row"><strong>width：</strong>{num(selected.size.x)}</div>
                    <div className="selection-info-row"><strong>height：</strong>{num(selected.size.y)}</div>
                    <div className="selection-info-row"><strong>depth：</strong>{num(selected.size.z)}</div>

                    <div className="selection-info-section">
                        <div className="selection-info-section__title">中心点</div>
                    </div>

                    <div className="selection-info-row"><strong>x：</strong>{num(selected.center.x)}</div>
                    <div className="selection-info-row"><strong>y：</strong>{num(selected.center.y)}</div>
                    <div className="selection-info-row"><strong>z：</strong>{num(selected.center.z)}</div>

                    <div className="selection-info-section">
                        <div className="selection-info-section__title">原始实例尺寸字段</div>
                    </div>

                    <div className="selection-info-row"><strong>meta.width：</strong>{num(selected.meta.width)}</div>
                    <div className="selection-info-row"><strong>meta.height：</strong>{num(selected.meta.height)}</div>
                    <div className="selection-info-row"><strong>meta.depth：</strong>{num(selected.meta.depth)}</div>
                </div>
            )}
        </Sidebar>
    );
}