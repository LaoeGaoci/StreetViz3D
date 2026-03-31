'use client';

import React from 'react';

interface StreetSceneLightsProps {
    styleInfo?: {
        // 环境光颜色
        ambientLightColor?: string;

        // 环境光强度：越大整体越亮，但过大会让模型“发平”，立体感变差
        ambientLightIntensity?: number;

        // 主方向光颜色，一般可用偏暖白，模拟太阳光
        directionalLightColor?: string;

        // 主方向光强度：越大，明暗对比越明显，阴影通常也更明显
        directionalLightIntensity?: number;

        skyColor?: string;
    } | null;
}

export default function StreetSceneLights({ styleInfo }: StreetSceneLightsProps) {
    // 环境光颜色：整体补亮
    const ambientColor = styleInfo?.ambientLightColor || '#ffffff';

    // 主光颜色：建议偏暖一点，比纯白更自然
    const sunColor = styleInfo?.directionalLightColor || '#fffaf0';

    return (
        <>
            {/* 
              环境光 Ambient Light
              作用：给整个场景一个基础亮度，避免模型背光面全黑
              修改建议：
              - 如果你觉得整体太暗：把 intensity 从 0.35 调到 0.45 / 0.5
              - 如果你觉得阴影不明显、场景太“平”：把 intensity 降到 0.2 / 0.25
            */}
            <a-entity
                light={`type: ambient; color: ${ambientColor}; intensity: 0.35`}
            />

            {/* 
              半球光 Hemisphere Light
              作用：
              - 从“天空方向”给一点冷色补光
              - 从“地面方向”给一点暖色反射
              它不会像方向光那样形成明显投影，但会让整体受光更自然

              修改建议：
              - intensity 越大，暗部越亮
              - color 控制上方光色
              - groundColor 控制地面反射色
            */}
            <a-entity
                light="type: hemisphere; color: #ffffff; groundColor: #8d6e63; intensity: 0.55"
            />

            {/* 
              主方向光 Directional Light
              这是最关键的一盏灯，决定：
              1. 光从哪个方向照过来
              2. 阴影往哪边投
              3. 整体明暗层次是否明显

              position="18 30 20" 怎么理解：
              - x = 18：光从场景右侧打过来
              - y = 30：光从较高位置打下来
              - z = 20：光从场景前方一点打过来

              你想改“光的方向”，主要就是改这个 position

              举例：
              - position="30 30 10"   -> 更偏右上前方
              - position="-30 30 10"  -> 改成从左上前方照过来
              - position="0 40 0"     -> 从正上方照下来，阴影会更短
              - position="20 15 25"   -> 更低角度，阴影更长、更戏剧化

              你想改“光的强度”，主要改 intensity
              - 1.0：比较柔和
              - 1.45：当前较明显
              - 1.8 / 2.0：更强烈，但过高可能发白

              阴影参数说明：
              - castShadow: true       开启这盏灯的阴影
              - shadowMapWidth/Height  阴影贴图分辨率，越高越清晰，但更耗性能
              - shadowCameraLeft...    阴影覆盖范围，范围过大阴影会发糊
              - shadowBias             解决阴影痤疮/闪烁，别乱改太大
            */}
            <a-entity
                light={`
                    type: directional;
                    color: ${sunColor};
                    intensity: 3;
                    castShadow: true;
                    shadowMapWidth: 2048;
                    shadowMapHeight: 2048;
                    shadowCameraLeft: -80;
                    shadowCameraRight: 80;
                    shadowCameraBottom: -80;
                    shadowCameraTop: 80;
                    shadowCameraNear: 1;
                    shadowCameraFar: 180;
                    shadowBias: -0.0008
                `}
                position="18 30 20"
            />

            {/* 
              辅助方向光
              作用：轻微补亮主光照不到的暗面，避免背面死黑
              它不是主角，所以强度一般不要太大

              修改建议：
              - 如果背面太黑：把 intensity 从 0.35 提到 0.45
              - 如果你想让主光方向更明显：把它降到 0.2 左右
              - 改 position 也可以改变它从哪边补光
            */}
            <a-entity
                light={`
                    type: directional;
                    color: #dbeafe;
                    intensity: 0.5
                `}
                position="-14 18 -12"
            />
        </>
    );
}