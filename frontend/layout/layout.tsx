'use client';

import React, { useContext, useRef, useState } from 'react';
import { classNames } from 'primereact/utils';
import { LayoutContext } from './context/layoutcontext';
import { PrimeReactContext } from 'primereact/api';

import AppSidebar from './AppSidebar';
import AppTopbar from './AppTopbar';
import AppConfig from './AppConfig';
import AddModelPanel from './components/AddModelPanel';
import { Button } from 'primereact/button';
import { StreetmixProvider } from './context/streetmixcontext';

import { ChildContainerProps, AppTopbarRef } from '@/types';

const Layout = ({ children }: ChildContainerProps) => {
    const { layoutConfig, layoutState } = useContext(LayoutContext);
    const { setRipple } = useContext(PrimeReactContext);

    const topbarRef = useRef<AppTopbarRef>(null);
    const sidebarRef = useRef<HTMLDivElement>(null);
    const [showAddModel, setShowAddModel] = useState(false);

    const containerClass = classNames('layout-wrapper', {
        'layout-overlay': layoutConfig.menuMode === 'overlay',
        'layout-static': layoutConfig.menuMode === 'static',
        'layout-static-inactive':
            layoutState.staticMenuDesktopInactive && layoutConfig.menuMode === 'static',
        'layout-overlay-active': layoutState.overlayMenuActive,
        'layout-mobile-active': layoutState.staticMenuMobileActive
    });

    return (
        <StreetmixProvider>
            <div className={containerClass}>
                {/* TopBar */}
                <AppTopbar ref={topbarRef} />

                {/* Sidebar */}
                <div className="layout-sideTool">
                    <div ref={sidebarRef} className="layout-sidebar">
                        <AppSidebar />
                    </div>
                    <Button
                        label="Add Model"
                        icon="pi pi-plus"
                        className="p-button-primary"
                        onClick={() => setShowAddModel(true)}
                    />
                </div>

                {/* Main */}
                <div className="layout-main-container">
                    {children}
                </div>

                <AddModelPanel
                    visible={showAddModel}
                    onHide={() => setShowAddModel(false)}
                />

                {/* Settings */}
                <AppConfig />
            </div>
        </StreetmixProvider>
    );
};

export default Layout;