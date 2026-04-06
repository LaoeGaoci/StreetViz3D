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

export type InteractionMode = 'view' | 'move' | 'rotate';

type MoveAxis = 'x' | 'y' | 'z';

type DragState =
    | {
          type: 'move';
          axis: MoveAxis;
          plane: THREE.Plane;
          startPoint: THREE.Vector3;
          startPosition: THREE.Vector3;
          axisWorld: THREE.Vector3;
      }
    | {
          type: 'rotate';
          center: THREE.Vector3;
          plane: THREE.Plane;
          startAngle: number;
          startRotationY: number;
      }
    | null;

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
        const raw = el?.dataset?.selectionMeta;
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

function canTransform(meta: SelectableMeta | null) {
    if (!meta) return false;
    return meta.kind !== 'segment-surface';
}

function radToDeg(value: number) {
    return THREE.MathUtils.radToDeg(value);
}

function degToRad(value: number) {
    return THREE.MathUtils.degToRad(value);
}

function syncEntityToObject3D(el: any) {
    if (!el?.object3D) return;

    const position = el.getAttribute('position');
    const rotation = el.getAttribute('rotation');
    const scale = el.getAttribute('scale');

    if (position) {
        el.object3D.position.set(position.x, position.y, position.z);
    }

    if (rotation) {
        el.object3D.rotation.set(degToRad(rotation.x), degToRad(rotation.y), degToRad(rotation.z));
    }

    if (scale) {
        el.object3D.scale.set(scale.x, scale.y, scale.z);
    }
}

function syncObject3DToEntity(el: any) {
    if (!el?.object3D) return;

    const object3D = el.object3D;

    el.setAttribute('position', {
        x: object3D.position.x,
        y: object3D.position.y,
        z: object3D.position.z
    });

    el.setAttribute('rotation', {
        x: radToDeg(object3D.rotation.x),
        y: radToDeg(object3D.rotation.y),
        z: radToDeg(object3D.rotation.z)
    });

    el.setAttribute('scale', {
        x: object3D.scale.x,
        y: object3D.scale.y,
        z: object3D.scale.z
    });
}

function createTextSprite(text: string, color: string) {
    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 128;

    const ctx = canvas.getContext('2d');
    if (!ctx) {
        const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ color: 0xffffff }));
        sprite.scale.set(0.7, 0.7, 0.7);
        return sprite;
    }

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = 'rgba(0,0,0,0)';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.beginPath();
    ctx.arc(64, 64, 42, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0,0,0,0.35)';
    ctx.fill();

    ctx.lineWidth = 6;
    ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.stroke();

    ctx.font = 'bold 64px Arial';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = color;
    ctx.fillText(text, 64, 64);

    const texture = new THREE.CanvasTexture(canvas);
    texture.needsUpdate = true;

    const material = new THREE.SpriteMaterial({
        map: texture,
        transparent: true,
        depthTest: false
    });

    const sprite = new THREE.Sprite(material);
    sprite.scale.set(0.85, 0.85, 0.85);
    return sprite;
}

function createVisibleAxisMaterial(color: number) {
    return new THREE.MeshBasicMaterial({
        color,
        depthTest: false,
        transparent: true,
        opacity: 0.98
    });
}

function createInvisibleHitMaterial() {
    return new THREE.MeshBasicMaterial({
        color: 0xffffff,
        transparent: true,
        opacity: 0.001,
        depthTest: false,
        depthWrite: false
    });
}

export interface SelectionControllerOptions {
    sceneEl: any;
    onSelect?: (payload: SelectionPayload | null) => void;
    onHover?: (payload: SelectionPayload | null) => void;
}

export function createSelectionController({ sceneEl, onSelect, onHover }: SelectionControllerOptions) {
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

    const raycaster = new THREE.Raycaster();
    const ndc = new THREE.Vector2();
    const tmpVec3 = new THREE.Vector3();
    const dragHitPoint = new THREE.Vector3();
    const tmpCenter = new THREE.Vector3();

    let selectedEl: any = null;
    let currentMode: InteractionMode = 'view';
    let dragState: DragState = null;
    let isDragging = false;
    let animationFrameId = 0;

    const downPos = new THREE.Vector2();
    const upPos = new THREE.Vector2();

    const gizmoRoot = new THREE.Group();
    gizmoRoot.visible = false;
    helperLayer.add(gizmoRoot);

    const moveGizmo = new THREE.Group();
    const rotateGizmo = new THREE.Group();
    gizmoRoot.add(moveGizmo);
    gizmoRoot.add(rotateGizmo);

    const axisHitMap = new Map<THREE.Object3D, MoveAxis>();
    const moveLabelSprites: THREE.Sprite[] = [];

    let rotateRing: THREE.Mesh | null = null;

    const moveConfig = {
        visualLength: 3.0,
        shaftRadius: 0.03,
        headRadius: 0.1,
        headLength: 0.24,
        hitLength: 6.6, // 正负轴全长
        hitThickness: 0.42 // 命中体厚度
    };

    const rotateConfig = {
        innerRadius: 1.35,
        outerRadius: 2.05
    };

    function buildMoveGizmo() {
        const { visualLength, shaftRadius, headRadius, headLength, hitLength, hitThickness } = moveConfig;

        const xMat = createVisibleAxisMaterial(0xff4d4f);
        const yMat = createVisibleAxisMaterial(0x22c55e);
        const zMat = createVisibleAxisMaterial(0x3b82f6);
        const hitMat = createInvisibleHitMaterial();

        const shaftGeometry = new THREE.CylinderGeometry(shaftRadius, shaftRadius, visualLength * 2, 12);
        const headGeometry = new THREE.ConeGeometry(headRadius, headLength, 16);

        const xHitGeometry = new THREE.BoxGeometry(hitLength, hitThickness, hitThickness);
        const yHitGeometry = new THREE.BoxGeometry(hitThickness, hitLength, hitThickness);
        const zHitGeometry = new THREE.BoxGeometry(hitThickness, hitThickness, hitLength);

        // X 轴：负轴 + 正轴
        {
            const group = new THREE.Group();

            const shaft = new THREE.Mesh(shaftGeometry, xMat);
            shaft.rotation.z = -Math.PI / 2;

            const headPos = new THREE.Mesh(headGeometry, xMat);
            headPos.rotation.z = -Math.PI / 2;
            headPos.position.x = visualLength + headLength * 0.5;

            const headNeg = new THREE.Mesh(headGeometry, xMat);
            headNeg.rotation.z = Math.PI / 2;
            headNeg.position.x = -(visualLength + headLength * 0.5);

            const label = createTextSprite('X', '#ff4d4f');
            label.position.set(visualLength + 0.6, 0, 0);
            moveLabelSprites.push(label);

            const hit = new THREE.Mesh(xHitGeometry, hitMat);
            axisHitMap.set(hit, 'x');

            group.add(shaft, headPos, headNeg, label, hit);
            moveGizmo.add(group);
        }

        // Y 轴：负轴 + 正轴
        {
            const group = new THREE.Group();

            const shaft = new THREE.Mesh(shaftGeometry, yMat);

            const headPos = new THREE.Mesh(headGeometry, yMat);
            headPos.position.y = visualLength + headLength * 0.5;

            const headNeg = new THREE.Mesh(headGeometry, yMat);
            headNeg.rotation.z = Math.PI;
            headNeg.position.y = -(visualLength + headLength * 0.5);

            const label = createTextSprite('Y', '#22c55e');
            label.position.set(0, visualLength + 0.6, 0);
            moveLabelSprites.push(label);

            const hit = new THREE.Mesh(yHitGeometry, hitMat);
            axisHitMap.set(hit, 'y');

            group.add(shaft, headPos, headNeg, label, hit);
            moveGizmo.add(group);
        }

        // Z 轴：负轴 + 正轴
        {
            const group = new THREE.Group();

            const shaft = new THREE.Mesh(shaftGeometry, zMat);
            shaft.rotation.x = Math.PI / 2;

            const headPos = new THREE.Mesh(headGeometry, zMat);
            headPos.rotation.x = Math.PI / 2;
            headPos.position.z = visualLength + headLength * 0.5;

            const headNeg = new THREE.Mesh(headGeometry, zMat);
            headNeg.rotation.x = -Math.PI / 2;
            headNeg.position.z = -(visualLength + headLength * 0.5);

            const label = createTextSprite('Z', '#3b82f6');
            label.position.set(0, 0, visualLength + 0.6);
            moveLabelSprites.push(label);

            const hit = new THREE.Mesh(zHitGeometry, hitMat);
            axisHitMap.set(hit, 'z');

            group.add(shaft, headPos, headNeg, label, hit);
            moveGizmo.add(group);
        }
    }

    function buildRotateGizmo() {
        const geometry = new THREE.TorusGeometry(1.8, 0.05, 12, 64);
        const material = new THREE.MeshBasicMaterial({
            color: 0xf59e0b,
            transparent: true,
            opacity: 0.95,
            depthTest: false
        });

        rotateRing = new THREE.Mesh(geometry, material);
        rotateRing.rotation.x = Math.PI / 2;
        rotateGizmo.add(rotateRing);
    }

    buildMoveGizmo();
    buildRotateGizmo();

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

    function updateGizmoVisibility() {
        if (!selectedEl) {
            gizmoRoot.visible = false;
            return;
        }

        const payload = payloadFromElement(selectedEl);
        const meta = parseMeta(selectedEl);

        if (!payload || !canTransform(meta)) {
            gizmoRoot.visible = false;
            return;
        }

        if (currentMode === 'view') {
            gizmoRoot.visible = false;
            return;
        }

        gizmoRoot.visible = true;
        moveGizmo.visible = currentMode === 'move';
        rotateGizmo.visible = currentMode === 'rotate';
    }

    function updateGizmoTransform() {
        const payload = payloadFromElement(selectedEl);
        if (!payload) {
            gizmoRoot.visible = false;
            return;
        }

        const { box, center, size } = computeBounds(payload.object3D);
        gizmoRoot.position.copy(center);

        if (currentMode === 'move') {
            moveGizmo.position.set(0, 0, 0);
        }

        if (currentMode === 'rotate' && rotateRing) {
            const ringY = box.min.y - 0.08 - center.y;
            rotateGizmo.position.set(0, ringY, 0);

            const baseRadius = Math.max(size.x, size.z) * 0.5 + 0.45;
            const ringRadius = Math.max(baseRadius, 0.9);

            rotateRing.geometry.dispose();
            rotateRing.geometry = new THREE.TorusGeometry(ringRadius, 0.05, 12, 64);

            rotateConfig.innerRadius = Math.max(ringRadius - 0.35, 0.45);
            rotateConfig.outerRadius = ringRadius + 0.35;
        }
    }

    function emitSelection(el: any | null) {
        onSelect?.(payloadFromElement(el));
    }

    function applySelection(el: any | null) {
        selectedEl = el;
        updateHelper(selectedBox, el);
        updateGizmoVisibility();
        updateGizmoTransform();
        emitSelection(el);
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

    function updateRayFromEvent(event: MouseEvent) {
        if (!sceneEl.canvas || !sceneEl.camera) return false;

        const [nx, ny] = getMousePosition(sceneEl.canvas, event.clientX, event.clientY);
        ndc.set(nx * 2 - 1, -(ny * 2) + 1);
        raycaster.setFromCamera(ndc, sceneEl.camera);
        return true;
    }

    function setNavigationEnabled(enabled: boolean) {
        const cameraEl = sceneEl.camera?.el;
        if (!cameraEl) return;

        if (cameraEl.hasAttribute('look-controls')) {
            cameraEl.setAttribute('look-controls', 'enabled', enabled);
        }

        if (cameraEl.hasAttribute('wasd-controls')) {
            cameraEl.setAttribute('wasd-controls', 'enabled', enabled);
        }

        const rigEl = cameraEl.parentElement;
        if (rigEl?.hasAttribute('fly-controls-y')) {
            rigEl.setAttribute('fly-controls-y', 'enabled', enabled);
        }
    }

    function getSelectedWorldCenter() {
        const payload = payloadFromElement(selectedEl);
        if (!payload) return null;
        return payload.center.clone();
    }

    function makeMovePlane(axisWorld: THREE.Vector3, center: THREE.Vector3) {
        const cameraDir = new THREE.Vector3();
        sceneEl.camera?.getWorldDirection(cameraDir);

        const normal = new THREE.Vector3().crossVectors(cameraDir, axisWorld).cross(axisWorld).normalize();

        if (normal.lengthSq() < 1e-6) {
            normal.set(0, 1, 0);
        }

        return new THREE.Plane().setFromNormalAndCoplanarPoint(normal, center);
    }

    function tryStartMoveDrag(event: MouseEvent) {
        if (!selectedEl || currentMode !== 'move') return false;
        if (!updateRayFromEvent(event)) return false;

        const hitTargets = Array.from(axisHitMap.keys());
        const intersects = raycaster.intersectObjects(hitTargets, false);
        if (!intersects.length) return false;

        const hit = intersects[0].object;
        const axis = axisHitMap.get(hit);
        if (!axis) return false;

        syncEntityToObject3D(selectedEl);

        const center = getSelectedWorldCenter();
        if (!center) return false;

        const axisWorld = axis === 'x' ? new THREE.Vector3(1, 0, 0) : axis === 'y' ? new THREE.Vector3(0, 1, 0) : new THREE.Vector3(0, 0, 1);

        const plane = makeMovePlane(axisWorld, center);
        if (!raycaster.ray.intersectPlane(plane, dragHitPoint)) return false;

        dragState = {
            type: 'move',
            axis,
            plane,
            startPoint: dragHitPoint.clone(),
            startPosition: selectedEl.object3D.position.clone(),
            axisWorld: axisWorld.clone()
        };

        isDragging = true;
        setNavigationEnabled(false);
        return true;
    }

    function tryStartRotateDrag(event: MouseEvent) {
        if (!selectedEl || currentMode !== 'rotate') return false;
        if (!updateRayFromEvent(event)) return false;

        syncEntityToObject3D(selectedEl);

        const center = getSelectedWorldCenter();
        if (!center) return false;

        const payload = payloadFromElement(selectedEl);
        if (!payload) return false;

        const { box } = computeBounds(payload.object3D);
        const ringY = box.min.y - 0.08;

        const plane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -ringY);

        if (!raycaster.ray.intersectPlane(plane, dragHitPoint)) return false;

        tmpCenter.copy(center);
        tmpCenter.y = ringY;

        const dx = dragHitPoint.x - tmpCenter.x;
        const dz = dragHitPoint.z - tmpCenter.z;
        const dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < rotateConfig.innerRadius || dist > rotateConfig.outerRadius) {
            return false;
        }

        const startAngle = Math.atan2(dragHitPoint.z - tmpCenter.z, dragHitPoint.x - tmpCenter.x);

        dragState = {
            type: 'rotate',
            center: tmpCenter.clone(),
            plane,
            startAngle,
            startRotationY: selectedEl.object3D.rotation.y
        };

        isDragging = true;
        setNavigationEnabled(false);
        return true;
    }

    function onPointerDown(event: MouseEvent) {
        if (!selectedEl) return;

        if (currentMode === 'move') {
            if (tryStartMoveDrag(event)) {
                event.preventDefault();
                event.stopPropagation();
            }
            return;
        }

        if (currentMode === 'rotate') {
            if (tryStartRotateDrag(event)) {
                event.preventDefault();
                event.stopPropagation();
            }
        }
    }

    function onPointerMove(event: MouseEvent) {
        if (!dragState || !selectedEl) return;
        if (!updateRayFromEvent(event)) return;

        if (dragState.type === 'move') {
            if (!raycaster.ray.intersectPlane(dragState.plane, dragHitPoint)) return;

            const delta = dragHitPoint.clone().sub(dragState.startPoint);
            const distance = delta.dot(dragState.axisWorld);

            tmpVec3.copy(dragState.axisWorld).multiplyScalar(distance);
            const nextPosition = dragState.startPosition.clone().add(tmpVec3);

            selectedEl.object3D.position.copy(nextPosition);
            syncObject3DToEntity(selectedEl);
            updateHelper(selectedBox, selectedEl);
            updateGizmoTransform();
            emitSelection(selectedEl);
            return;
        }

        if (dragState.type === 'rotate') {
            if (!raycaster.ray.intersectPlane(dragState.plane, dragHitPoint)) return;

            const currentAngle = Math.atan2(dragHitPoint.z - dragState.center.z, dragHitPoint.x - dragState.center.x);

            let deltaAngle = currentAngle - dragState.startAngle;
            if (deltaAngle > Math.PI) deltaAngle -= Math.PI * 2;
            if (deltaAngle < -Math.PI) deltaAngle += Math.PI * 2;

            selectedEl.object3D.rotation.y = dragState.startRotationY + deltaAngle;
            syncObject3DToEntity(selectedEl);
            updateHelper(selectedBox, selectedEl);
            updateGizmoTransform();
            emitSelection(selectedEl);
        }
    }

    function onPointerUp() {
        if (!isDragging) return;

        dragState = null;
        isDragging = false;
        setNavigationEnabled(true);
    }

    function onMouseDown(event: MouseEvent) {
        if (!sceneEl.canvas) return;
        const arr = getMousePosition(sceneEl.canvas, event.clientX, event.clientY);
        downPos.fromArray(arr);
        onPointerDown(event);
    }

    function onMouseUp(event: MouseEvent) {
        if (!sceneEl.canvas) return;
        const arr = getMousePosition(sceneEl.canvas, event.clientX, event.clientY);
        upPos.fromArray(arr);
        onPointerUp();
    }

    function onClick() {
        if (isDragging) return;
        if (downPos.distanceTo(upPos) !== 0) return;

        const el = getIntersectedSelectable();
        applySelection(el);
    }

    function onMouseEnter() {
        const el = getIntersectedSelectable();
        if (!el || el === selectedEl || isDragging) return;
        updateHelper(hoverBox, el);
        onHover?.(payloadFromElement(el));
    }

    function onMouseLeave() {
        if (!isDragging) {
            hoverBox.visible = false;
        }
        onHover?.(null);
    }

    function refreshSelected() {
        if (!selectedEl?.object3D) {
            selectedBox.visible = false;
            gizmoRoot.visible = false;
            return;
        }

        selectedBox.setFromObject(selectedEl.object3D);
        selectedBox.visible = true;
        updateGizmoVisibility();
        updateGizmoTransform();

        if (sceneEl.camera) {
            for (const label of moveLabelSprites) {
                label.quaternion.copy(sceneEl.camera.quaternion);
            }
        }
    }

    function clearSelection() {
        selectedEl = null;
        selectedBox.visible = false;
        hoverBox.visible = false;
        gizmoRoot.visible = false;
        dragState = null;
        isDragging = false;
        onSelect?.(null);
        onHover?.(null);
        setNavigationEnabled(true);
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

    function setInteractionMode(mode: InteractionMode) {
        currentMode = mode;
        updateGizmoVisibility();
        updateGizmoTransform();
    }

    function getInteractionMode(): InteractionMode {
        return currentMode;
    }

    function animationLoop() {
        refreshSelected();

        if (!isDragging && hoverBox.visible) {
            const hoverEl = getIntersectedSelectable();
            if (hoverEl && hoverEl !== selectedEl) {
                hoverBox.setFromObject(hoverEl.object3D);
            }
        }

        animationFrameId = window.requestAnimationFrame(animationLoop);
    }

    mouseCursor.addEventListener('click', onClick);
    mouseCursor.addEventListener('mouseenter', onMouseEnter);
    mouseCursor.addEventListener('mouseleave', onMouseLeave);

    sceneEl.canvas?.addEventListener('mousedown', onMouseDown);
    sceneEl.canvas?.addEventListener('mouseup', onMouseUp);
    sceneEl.canvas?.addEventListener('mousemove', onPointerMove);

    animationFrameId = window.requestAnimationFrame(animationLoop);

    return {
        clearSelection,
        refreshSelected,
        selectBySelectionId,
        focusSelected,
        setInteractionMode,
        getInteractionMode,
        destroy() {
            mouseCursor.removeEventListener('click', onClick);
            mouseCursor.removeEventListener('mouseenter', onMouseEnter);
            mouseCursor.removeEventListener('mouseleave', onMouseLeave);

            sceneEl.canvas?.removeEventListener('mousedown', onMouseDown);
            sceneEl.canvas?.removeEventListener('mouseup', onMouseUp);
            sceneEl.canvas?.removeEventListener('mousemove', onPointerMove);

            if (animationFrameId) {
                window.cancelAnimationFrame(animationFrameId);
            }

            setNavigationEnabled(true);

            if (helperLayer.parent) {
                helperLayer.parent.remove(helperLayer);
            }
            if (mouseCursor.parentNode) {
                mouseCursor.parentNode.removeChild(mouseCursor);
            }
        }
    };
}
