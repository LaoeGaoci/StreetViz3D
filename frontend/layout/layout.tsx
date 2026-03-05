'use client';

import React, { useContext, useRef } from 'react';
import { classNames } from 'primereact/utils';
import { LayoutContext } from './context/layoutcontext';
import { PrimeReactContext } from 'primereact/api';

import AppSidebar from './AppSidebar';
import AppTopbar from './AppTopbar';
import AppConfig from './AppConfig';

import { ChildContainerProps, AppTopbarRef } from '@/types';

const Layout = ({ children }: ChildContainerProps) => {
    const { layoutConfig, layoutState } = useContext(LayoutContext);
    const { setRipple } = useContext(PrimeReactContext);

    const topbarRef = useRef<AppTopbarRef>(null);
    const sidebarRef = useRef<HTMLDivElement>(null);

    const containerClass = classNames('layout-wrapper', {
        'layout-overlay': layoutConfig.menuMode === 'overlay',
        'layout-static': layoutConfig.menuMode === 'static',
        'layout-static-inactive': layoutState.staticMenuDesktopInactive && layoutConfig.menuMode === 'static',
        'layout-overlay-active': layoutState.overlayMenuActive,
        'layout-mobile-active': layoutState.staticMenuMobileActive
    });

    return (
        <div className={containerClass}>

            {/* TopBar */}
            <AppTopbar ref={topbarRef} />

            {/* Sidebar */}
            <div ref={sidebarRef} className="layout-sidebar">
                <AppSidebar />
            </div>

            {/* Main */}
            <div className="layout-main-container">
                {children}
            </div>

            {/* Settings */}
            <AppConfig />

        </div>
    );
};

export default Layout;