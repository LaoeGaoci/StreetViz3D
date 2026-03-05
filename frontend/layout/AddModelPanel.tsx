'use client';

import React, { useState } from 'react';
import { Sidebar } from 'primereact/sidebar';
import { TabMenu } from 'primereact/tabmenu';
import { ScrollPanel } from 'primereact/scrollpanel';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';

interface Props {
    visible: boolean;
    onHide: () => void;
}

const modelData: Record<string, any[]> = {
    "🚦Streets & Intersections": [
        { name: "BRT Station", img: "/models/brt.png" },
    ],
    "🚧Traffic Control": [
        { name: "Traffic Light", img: "/models/light.png" },
    ],
    "🚸Signs": [
        { name: "Stop Sign", img: "/models/stop.png" },
    ],
    "🌳Plants": [
        { name: "Tree", img: "/models/tree.png" },
    ],
    "🪑Fixtures": [
        { name: "Bench", img: "/models/bench.png" },
        { name: "Utility Pole", img: "/models/pole.png" },
        { name: "Lamp Modern", img: "/models/lamp.png" }
    ],
    "🧍People": [
        { name: "Pedestrian", img: "/models/person.png" }
    ],
    "🚲Bicycles": [
        { name: "Bike", img: "/models/bike.png" }
    ],
    "🚗Vehicles": [
        { name: "Car", img: "/models/car.png" },
        { name: "Bus", img: "/models/bus.png" }
    ],
    "🏢Buildings": [
        { name: "House", img: "/models/house.png" }
    ]
};

export default function AddModelPanel({ visible, onHide }: Props) {

    const categories = Object.keys(modelData);

    const items = categories.map((c) => ({
        label: c
    }));

    const [activeIndex, setActiveIndex] = useState(0);

    const currentCategory = categories[activeIndex];
    const models = modelData[currentCategory];

    return (
        <Sidebar
            visible={visible}
            position="bottom"
            onHide={onHide}
            className="streetviz-addmodel"
            showCloseIcon={false}
        >

            {/* Header Tabs */}
            <div className="panel-header">

                <TabMenu
                    model={items}
                    activeIndex={activeIndex}
                    onTabChange={(e) => setActiveIndex(e.index)}
                />

                <div className="panel-close">
                    <Button
                        icon="pi pi-times"
                        className="p-button-primary"
                        onClick={onHide}
                    />
                </div>

            </div>

            {/* Model List */}
            <ScrollPanel style={{ width: '100%', height: '140px' }}>

                <div className="model-row">

                    {models.map((m, i) => (
                        <div key={i} className="model-card">

                            <img src={m.img} alt={m.name} />

                            <div className="model-title">
                                {m.name}
                            </div>

                        </div>
                    ))}

                </div>

            </ScrollPanel>

        </Sidebar>
    );
}