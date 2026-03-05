/* eslint-disable @next/next/no-img-element */

import React, { useContext } from 'react';
import AppMenuitem from './AppMenuitem';
import { LayoutContext } from './context/layoutcontext';
import { MenuProvider } from './context/menucontext';
import { AppMenuItem } from '@/types';
import AppFooter from './AppFooter';

import { Tree } from '@icon-park/react'
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
                        { label: 'Plants', icon: 'pi pi-image' },
                        { label: 'People', icon: 'pi pi-fw pi-user' },
                        { label: 'Signs', icon: 'pi pi-fw pi-flag' },
                        { label: 'Fixtures', icon: 'pi pi-fw pi-bolt' }
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
