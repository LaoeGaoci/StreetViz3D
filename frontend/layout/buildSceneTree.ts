import { AppMenuItem } from '@/types';
import { BoundarySceneDTO, SceneInstanceDTO, SegmentSceneDTO, StreetSceneDTO } from '@/app/api/street';

type SceneTreeStreetDTO = StreetSceneDTO & {
    sceneInstances?: SceneInstanceDTO[];
};

const SELECT_NODE_EVENT = 'streetviz3d:select-node';

function dispatchSelectNode(selectionId: string) {
    if (typeof window === 'undefined') {
        return;
    }

    window.dispatchEvent(
        new CustomEvent(SELECT_NODE_EVENT, {
            detail: { selectionId }
        })
    );
}

function createLeaf(
    label: string,
    selectionId?: string,
    icon = 'pi pi-fw pi-file'
): AppMenuItem {
    return {
        label,
        icon,
        command: selectionId
            ? () => {
                dispatchSelectNode(selectionId);
            }
            : undefined
    };
}

function createFolder(label: string, items: AppMenuItem[] = [], icon = 'pi pi-fw pi-folder'): AppMenuItem {
    return {
        label,
        icon,
        items: items.length
            ? items
            : [
                {
                    label: 'empty',
                    icon: 'pi pi-fw pi-file'
                }
            ]
    };
}

function resolveModelFileName(instance: SceneInstanceDTO, fallback: string): string {
    if (instance.displayName && instance.displayName.trim()) {
        return instance.displayName.trim();
    }

    if (instance.modelUrl && instance.modelUrl.trim()) {
        const normalized = instance.modelUrl.replace(/\\/g, '/');
        const lastPart = normalized.split('/').pop();
        if (lastPart) {
            return lastPart;
        }
    }

    if (instance.modelId && instance.modelId.trim()) {
        return instance.modelId.trim();
    }

    return fallback;
}

function buildInstanceFiles(
    instances: SceneInstanceDTO[] | undefined,
    fallbackPrefix: string,
    selectionIdBuilder: (index: number) => string
): AppMenuItem[] {
    if (!instances?.length) {
        return [];
    }

    return instances.map((instance, index) =>
        createLeaf(
            resolveModelFileName(instance, `${fallbackPrefix}-${index + 1}`),
            selectionIdBuilder(index),
            resolveInstanceIcon(instance)
        )
    );
}

function buildSegmentFolder(segment: SegmentSceneDTO): AppMenuItem {
    const instanceFiles = buildInstanceFiles(
        segment.instances,
        `${segment.type}-model`,
        (index) => `instance-${segment.segmentId}-${index}`
    );

    const surfaceLeaf = createLeaf(
        `${segment.type}-surface`,
        `surface-${segment.segmentId}`,
        'pi pi-fw pi-stop'
    );

    return createFolder(
        segment.type,
        [surfaceLeaf, ...instanceFiles],
        resolveSegmentFolderIcon(segment.type)
    );
}

function buildBoundaryFolder(
    boundary: BoundarySceneDTO | null,
    side: 'left' | 'right'
): AppMenuItem | null {
    if (!boundary) {
        return null;
    }

    const items: AppMenuItem[] = [];

    if (boundary.supportSurface) {
        const supportSelectionId = `${side}-support-${boundary.boundaryId}`;
        items.push(
            createLeaf(
                resolveModelFileName(boundary.supportSurface, `${side}-${boundary.type}-support`),
                supportSelectionId
            )
        );
    }

    items.push(
        ...buildInstanceFiles(
            boundary.instances,
            `${side}-${boundary.type}-model`,
            (index) => `${side}-instance-${boundary.boundaryId}-${index}`
        )
    );

    return createFolder(`${side}-${boundary.type}`, items, resolveBoundaryFolderIcon(boundary.type));
}

function buildSceneFolder(scene: SceneTreeStreetDTO): AppMenuItem {
    const sceneInstances = Array.isArray(scene.sceneInstances) ? scene.sceneInstances : [];

    const files = sceneInstances.length
        ? buildInstanceFiles(
            sceneInstances,
            'scene-model',
            (index) => `scene-instance-${index}`
        )
        : [createLeaf('main-scene-model')];

    return createFolder('Scene', files, 'pi pi-fw pi-folder-open');
}

function buildSegmentsRoot(scene: SceneTreeStreetDTO): AppMenuItem {
    const segmentFolders = (scene.segments || []).map(buildSegmentFolder);
    return createFolder('Segments', segmentFolders, 'pi pi-fw pi-folder-open');
}

function buildBoundariesRoot(scene: SceneTreeStreetDTO): AppMenuItem {
    const boundaryFolders = [
        buildBoundaryFolder(scene.leftBoundary, 'left'),
        buildBoundaryFolder(scene.rightBoundary, 'right')
    ].filter(Boolean) as AppMenuItem[];

    return createFolder('Boundaries', boundaryFolders, 'pi pi-fw pi-folder-open');
}

function resolveSegmentFolderIcon(type: string): string {
    const lower = (type || '').toLowerCase();

    if (lower.includes('sidewalk')) return 'pi pi-fw pi-directions';
    if (lower.includes('parking')) return 'pi pi-fw pi-car';
    if (lower.includes('drive')) return 'pi pi-fw pi-arrow-right-arrow-left';
    if (lower.includes('bus')) return 'pi pi-fw pi-arrow-right-arrow-left';
    if (lower.includes('temporary')) return 'pi pi-fw pi-wrench';

    return 'pi pi-fw pi-folder';
}

function resolveBoundaryFolderIcon(type: string): string {
    const lower = (type || '').toLowerCase();

    if (lower.includes('water')) return 'pi pi-fw pi-images';
    if (lower.includes('parking')) return 'pi pi-fw pi-car';
    if (lower.includes('residential')) return 'pi pi-fw pi-home';
    if (lower.includes('wide')) return 'pi pi-fw pi-home';
    if (lower.includes('narrow')) return 'pi pi-fw pi-home';
    if (lower.includes('grass')) return 'pi pi-fw pi-image';
    if (lower.includes('wall')) return 'pi pi-fw pi-building';
    if (lower.includes('compound')) return 'pi pi-fw pi-building';

    return 'pi pi-fw pi-folder';
}

function resolveInstanceIcon(instance: SceneInstanceDTO): string {
    const text = `${instance.semanticType || ''} ${instance.displayName || ''} ${instance.modelUrl || ''}`.toLowerCase();

    if (text.includes('car') || text.includes('bus') || text.includes('vehicle')) {
        return 'pi pi-fw pi-car';
    }

    if (text.includes('tree') || text.includes('plant') || text.includes('grass')) {
        return 'pi pi-fw pi-image';
    }

    if (text.includes('sign')) {
        return 'pi pi-fw pi-flag';
    }

    if (text.includes('building') || text.includes('house') || text.includes('residential')) {
        return 'pi pi-fw pi-home';
    }

    if (text.includes('ship') || text.includes('cruise') || text.includes('boat')) {
        return 'pi pi-fw pi-send';
    }

    return 'pi pi-fw pi-file';
}

export function buildSceneTree(sceneData: StreetSceneDTO | null): AppMenuItem[] {
    if (!sceneData) {
        return [
            {
                label: 'Scene',
                items: [
                    {
                        label: 'Model Tree',
                        icon: 'pi pi-fw pi-sitemap',
                        items: [createLeaf('empty')]
                    }
                ]
            }
        ];
    }

    const scene = sceneData as SceneTreeStreetDTO;

    return [
        {
            label: 'Scene',
            items: [
                {
                    label: 'Model Tree',
                    icon: 'pi pi-fw pi-sitemap',
                    items: [
                        buildSceneFolder(scene),
                        buildSegmentsRoot(scene),
                        buildBoundariesRoot(scene)
                    ]
                }
            ]
        }
    ];
}