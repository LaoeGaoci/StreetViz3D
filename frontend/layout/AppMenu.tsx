/* eslint-disable @next/next/no-img-element */

import React, { useContext } from 'react';
import AppMenuitem from './AppMenuitem';
import { LayoutContext } from './context/layoutcontext';
import { MenuProvider } from './context/menucontext';
import { AppMenuItem } from '@/types';
import AppFooter from './AppFooter';

const AppMenu = () => {
    const { layoutConfig } = useContext(LayoutContext);

    const model: AppMenuItem[] = [
        {
            label: 'Scene',
            items: [
                {
                    label: 'Model Tree',
                    icon: 'pi pi-fw pi-sitemap',
                    items: [
                        { label: 'Vehicles', icon: 'pi pi-fw pi-car' },
                        { label: 'Buildings', icon: 'pi pi-fw pi-home' },
                        { label: 'Plants', icon: 'pi pi-fw pi-leaf' },
                        { label: 'People', icon: 'pi pi-fw pi-user' },
                        { label: 'Signs', icon: 'pi pi-fw pi-flag' },
                        { label: 'Fixtures', icon: 'pi pi-fw pi-bolt' }
                    ]
                },
                {
                    label: 'Camera Settings',
                    icon: 'pi pi-fw pi-camera',
                    items: [
                        { label: 'Reset Camera', icon: 'pi pi-fw pi-refresh' },
                        { label: 'Top View', icon: 'pi pi-fw pi-angle-up' },
                        { label: 'Street View', icon: 'pi pi-fw pi-eye' },
                        { label: 'Fly Mode', icon: 'pi pi-fw pi-send' },
                        { label: 'Focus Selected', icon: 'pi pi-fw pi-search' }
                    ]
                }
            ]
        }
    ];

    return (
        <MenuProvider>
            <ul className="layout-menu">
                {model.map((item, i) => {
                    return !item?.seperator ? <AppMenuitem item={item} root={true} index={i} key={item.label} /> : <li className="menu-separator"></li>;
                })}
            </ul>
            <AppFooter />
        </MenuProvider>
    );
};

export default AppMenu;
