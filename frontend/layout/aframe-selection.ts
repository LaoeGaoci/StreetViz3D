'use client';

import * as THREE from 'three';

export interface SelectableMeta {
    selectionId: string;
    kind: 'segment-surface' | 'segment-instance' | 'boundary-instance' | 'scene-instance';
    semanticType?: string;
    displayName?: string | null;
    modelId?: string | null;
    modelUrl?: string | null;
    width?: number | null;
    height?: number | null;
    depth?: number | null;
}

export interface SelectionPayload {
    el: any;
    object3D: THREE.Object3D;
    meta: SelectableMeta;
    size: THREE.Vector3;
    center: THREE.Vector3;
}

function getSelectableRoot(el: any): any | null {
    let cur = el;
    while (cur) {
        if (cur.dataset?.selectable === 'true') {
            return cur;
        }
        cur = cur.parentElement;
    }
    return null;
}

function parseMeta(el: any): SelectableMeta | null {
    try {
        const raw = el.dataset?.selectionMeta;
        if (!raw) return null;
        return JSON.parse(raw);
    } catch (error) {
        console.error('解析 selectionMeta 失败', error);
        return null;
    }
}

function computeBounds(object3D: THREE.Object3D) {
    const box = new THREE.Box3().setFromObject(object3D);
    const size = new THREE.Vector3();
    const center = new THREE.Vector3();
    box.getSize(size);
    box.getCenter(center);
    return { box, size, center };
}

function createBoxHelper(color: number) {
    const helper = new THREE.BoxHelper(undefined, color);
    helper.visible = false;
    helper.material.depthTest = false;
    helper.material.transparent = true;
    return helper;
}

export interface SelectionControllerOptions {
    sceneEl: any;
    onSelect?: (payload: SelectionPayload | null) => void;
    onHover?: (payload: SelectionPayload | null) => void;
}

export function createSelectionController({
    sceneEl,
    onSelect,
    onHover
}: SelectionControllerOptions) {
    const mouseCursor = document.createElement('a-entity');
    mouseCursor.setAttribute('id', 'streetviz-selection-cursor');
    mouseCursor.setAttribute('cursor', 'rayOrigin', 'mouse');
    mouseCursor.setAttribute('raycaster', {
        interval: 50,
        objects: '[data-selectable="true"]'
    });

    sceneEl.appendChild(mouseCursor);

    const helperLayer = new THREE.Group();
    helperLayer.name = 'streetviz-selection-helpers';

    const hoverBox = createBoxHelper(0xff4d4f);
    const selectedBox = createBoxHelper(0x1faaf2);

    helperLayer.add(hoverBox);
    helperLayer.add(selectedBox);
    sceneEl.object3D.add(helperLayer);

    let selectedEl: any = null;
    const downPos = new THREE.Vector2();
    const upPos = new THREE.Vector2();

    function payloadFromElement(el: any): SelectionPayload | null {
        if (!el) return null;

        const meta = parseMeta(el);
        if (!meta) return null;

        const object3D = el.object3D;
        if (!object3D) return null;

        const { size, center } = computeBounds(object3D);
        return {
            el,
            object3D,
            meta,
            size,
            center
        };
    }

    function updateHelper(helper: THREE.BoxHelper, el: any | null) {
        if (!el?.object3D) {
            helper.visible = false;
            return;
        }

        helper.setFromObject(el.object3D);
        helper.visible = true;
    }

    function applySelection(el: any | null) {
        selectedEl = el;
        updateHelper(selectedBox, el);
        onSelect?.(payloadFromElement(el));
    }

    function getIntersectedSelectable(): any | null {
        const cursorComp = mouseCursor.components?.cursor;
        const intersectedEl = cursorComp?.intersectedEl;
        if (!intersectedEl) return null;
        return getSelectableRoot(intersectedEl);
    }

    function getMousePosition(dom: HTMLElement, x: number, y: number) {
        const rect = dom.getBoundingClientRect();
        return [(x - rect.left) / rect.width, (y - rect.top) / rect.height];
    }

    function onMouseDown(event: MouseEvent) {
        const arr = getMousePosition(sceneEl.canvas, event.clientX, event.clientY);
        downPos.fromArray(arr);
    }

    function onMouseUp(event: MouseEvent) {
        const arr = getMousePosition(sceneEl.canvas, event.clientX, event.clientY);
        upPos.fromArray(arr);
    }

    function onClick() {
        if (downPos.distanceTo(upPos) !== 0) return;

        const el = getIntersectedSelectable();
        applySelection(el);
    }

    function onMouseEnter() {
        const el = getIntersectedSelectable();
        if (!el || el === selectedEl) return;
        updateHelper(hoverBox, el);
        onHover?.(payloadFromElement(el));
    }

    function onMouseLeave() {
        hoverBox.visible = false;
        onHover?.(null);
    }

    function refreshSelected() {
        if (!selectedEl?.object3D) {
            selectedBox.visible = false;
            return;
        }
        selectedBox.setFromObject(selectedEl.object3D);
        selectedBox.visible = true;
    }

    function clearSelection() {
        selectedEl = null;
        selectedBox.visible = false;
        hoverBox.visible = false;
        onSelect?.(null);
        onHover?.(null);
    }

    function findElementBySelectionId(selectionId: string): any | null {
        const selectableNodes = sceneEl.querySelectorAll('[data-selectable="true"]');

        for (const node of selectableNodes) {
            const meta = parseMeta(node);
            if (meta?.selectionId === selectionId) {
                return node;
            }
        }

        return null;
    }

    function selectBySelectionId(selectionId: string): SelectionPayload | null {
        const el = findElementBySelectionId(selectionId);
        if (!el) {
            return null;
        }

        applySelection(el);
        return payloadFromElement(el);
    }

    function focusSelected(options?: { distanceMultiplier?: number }) {
        if (!selectedEl?.object3D) {
            return;
        }

        const cameraEl = sceneEl.camera?.el;
        if (!cameraEl) {
            return;
        }

        const payload = payloadFromElement(selectedEl);
        if (!payload) {
            return;
        }

        const center = payload.center.clone();
        const size = payload.size.clone();
        const maxSize = Math.max(size.x, size.y, size.z, 1);
        const distanceMultiplier = options?.distanceMultiplier ?? 2.4;

        cameraEl.setAttribute('position', {
            x: center.x + maxSize * distanceMultiplier,
            y: center.y + maxSize * 0.8,
            z: center.z + maxSize * distanceMultiplier
        });

        cameraEl.object3D.lookAt(center);
    }

    mouseCursor.addEventListener('click', onClick);
    mouseCursor.addEventListener('mouseenter', onMouseEnter);
    mouseCursor.addEventListener('mouseleave', onMouseLeave);

    sceneEl.canvas?.addEventListener('mousedown', onMouseDown);
    sceneEl.canvas?.addEventListener('mouseup', onMouseUp);

    const tick = () => {
        refreshSelected();
        if (hoverBox.visible) {
            const hoverEl = getIntersectedSelectable();
            if (hoverEl && hoverEl !== selectedEl) {
                hoverBox.setFromObject(hoverEl.object3D);
            }
        }
    };

    sceneEl.addEventListener('renderstart', tick);

    return {
        clearSelection,
        refreshSelected,
        selectBySelectionId,
        focusSelected,
        destroy() {
            mouseCursor.removeEventListener('click', onClick);
            mouseCursor.removeEventListener('mouseenter', onMouseEnter);
            mouseCursor.removeEventListener('mouseleave', onMouseLeave);

            sceneEl.canvas?.removeEventListener('mousedown', onMouseDown);
            sceneEl.canvas?.removeEventListener('mouseup', onMouseUp);

            sceneEl.removeEventListener('renderstart', tick);

            if (helperLayer.parent) {
                helperLayer.parent.remove(helperLayer);
            }
            if (mouseCursor.parentNode) {
                mouseCursor.parentNode.removeChild(mouseCursor);
            }
        }
    };
}