/* eslint-disable @next/next/no-img-element */
'use client';

import React, { useEffect, useState } from 'react';
import AppMenuitem from './AppMenuitem';
import { MenuProvider } from './context/menucontext';
import { AppMenuItem } from '@/types';
import AppFooter from './AppFooter';
import { StreetSceneDTO } from '@/app/api/street';
import { buildSceneTree } from './buildSceneTree';

const SCENE_STORAGE_KEY = 'streetviz3d-current-scene';
const SCENE_UPDATED_EVENT = 'streetviz3d:scene-updated';

function readSceneFromStorage(): StreetSceneDTO | null {
    if (typeof window === 'undefined') {
        return null;
    }

    const raw = window.sessionStorage.getItem(SCENE_STORAGE_KEY);
    if (!raw) {
        return null;
    }

    try {
        return JSON.parse(raw) as StreetSceneDTO;
    } catch (error) {
        console.error('读取场景树缓存失败：', error);
        return null;
    }
}

const AppMenu = () => {
    const [sceneData, setSceneData] = useState<StreetSceneDTO | null>(null);

    useEffect(() => {
        const syncScene = () => {
            setSceneData(readSceneFromStorage());
        };

        syncScene();
        window.addEventListener(SCENE_UPDATED_EVENT, syncScene);

        return () => {
            window.removeEventListener(SCENE_UPDATED_EVENT, syncScene);
        };
    }, []);

    const model: AppMenuItem[] = buildSceneTree(sceneData);

    return (
        <MenuProvider>
            <ul className="layout-menu">
                {model.map((item, i) =>
                    !item?.seperator ? (
                        <AppMenuitem
                            item={item}
                            root={true}
                            index={i}
                            key={`${item.label}-${i}`}
                        />
                    ) : (
                        <li className="menu-separator" key={`separator-${i}`}></li>
                    )
                )}
            </ul>
            <AppFooter />
        </MenuProvider>
    );
};

export default AppMenu;