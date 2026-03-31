import React from 'react';

type AFrameElementProps = React.DetailedHTMLProps<
    React.HTMLAttributes<HTMLElement>,
    HTMLElement
> & {
    [key: string]: any;
};

declare module 'react' {
    namespace JSX {
        interface IntrinsicElements {
            'a-scene': AFrameElementProps;
            'a-entity': AFrameElementProps;
            'a-assets': AFrameElementProps;
            'a-asset-item': AFrameElementProps;
            'a-box': AFrameElementProps;
            'a-plane': AFrameElementProps;
            'a-camera': AFrameElementProps;
            'a-sphere': AFrameElementProps;
            'a-cylinder': AFrameElementProps;
            'a-cone': AFrameElementProps;
            'a-sky': AFrameElementProps;
            'a-light': AFrameElementProps;
        }
    }
}

export {};