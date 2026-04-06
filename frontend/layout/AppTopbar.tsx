/* eslint-disable @next/next/no-img-element */

import Link from 'next/link';
import { classNames } from 'primereact/utils';
import React, { forwardRef, useContext, useImperativeHandle, useRef } from 'react';
import { AppTopbarRef } from '@/types';
import { LayoutContext } from './context/layoutcontext';
import { usePathname } from 'next/navigation';
import StreetmixTopbarToolbar from './components/StreetmixToolbar';

const AppTopbar = forwardRef<AppTopbarRef>((props, ref) => {
    const { layoutConfig, layoutState, onMenuToggle, showProfileSidebar } = useContext(LayoutContext);
    const menubuttonRef = useRef(null);
    const topbarmenuRef = useRef(null);
    const topbarmenubuttonRef = useRef(null);

    const pathname = usePathname();
    const hideEllipsis = pathname === '/auth/profile';

    const showStreetmixToolbar = pathname === '/home';

    useImperativeHandle(ref, () => ({
        menubutton: menubuttonRef.current,
        topbarmenu: topbarmenuRef.current,
        topbarmenubutton: topbarmenubuttonRef.current
    }));

    return (
        <div className="layout-topbar">

            {!hideEllipsis && (
                <button
                    ref={menubuttonRef}
                    type="button"
                    className="p-link layout-menu-button layout-topbar-button"
                    onClick={onMenuToggle}
                >
                    <i className="pi pi-bars" />
                </button>
            )}
            
            <Link href="/home" className="layout-topbar-logo">
                <img
                    src={`/layout/images/logo-${layoutConfig.colorScheme !== 'light' ? 'white' : 'dark'}.png`}
                    width="47.22px"
                    height="35px"
                    alt="logo"
                />
                <span>StreetViz3D</span>
            </Link>

            {showStreetmixToolbar && (
                <div className="layout-topbar-center">
                    <StreetmixTopbarToolbar />
                </div>
            )}

            {!hideEllipsis && (
                <button
                    ref={topbarmenubuttonRef}
                    type="button"
                    className="p-link layout-topbar-menu-button layout-topbar-button"
                    onClick={showProfileSidebar}
                >
                    <i className="pi pi-ellipsis-v" />
                </button>
            )}

            {!hideEllipsis && (
                <div
                    ref={topbarmenuRef}
                    className={classNames('layout-topbar-menu', {
                        'layout-topbar-menu-mobile-active': layoutState.profileSidebarVisible
                    })}
                >
                    <Link href="/auth/profile" className="layout-topbar-profile">
                        <button type="button" className="p-link layout-topbar-button">
                            <i className="pi pi-user" />
                            <span>Profile</span>
                        </button>
                    </Link>
                </div>
            )}
        </div>
    );
});

AppTopbar.displayName = 'AppTopbar';

export default AppTopbar;