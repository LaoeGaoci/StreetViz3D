# CLAUDE.md

This file provides guidance to Claude Code (claude.ai.ai/code) when working with code in this repository.

## Project Overview

StreetViz3D is a 3D street visualization tool that:
- Fetches street data from Streetmix API
- Renders 3D street scenes using Three.js + A-Frame
- Provides a web-based editor for placing and manipulating 3D models

## Tech Stack

**Frontend (`/frontend/`)**
- Next.js 16.1.6 with React 19.2.3
- Tailwind CSS 4
- PrimeReact (UI components)
- React Compiler enabled (next.config.mjs: `reactCompiler: true`)

**Backend (`/backend/`)**
- Spring Boot 4.0.3
- Java 21
- PostgreSQL
- MyBatis-Plus (ORM)

## Common Commands

**Frontend Development:**
```bash
cd frontend
npm run dev        # Start dev server on http://localhost:3000
npm run build      # Production build
npm run lint       # ESLint
```

**Backend Development:**
```bash
cd backend
./gradlew bootRun   # Start Spring Boot dev server
./gradlew build     # Build
./gradlew test      # Run tests
```

## Architecture

**Three-Tier Architecture:**
```
Frontend (Next.js)  →  Backend (Spring Boot)  →  Database (PostgreSQL)
         ↓                                               ↑
    Streetmix API  ←──────────────────────────────────────┘
```

**Frontend Layout:**
- Topbar: Logo and street URL input
- Sidebar: Model library panel (drag-and-drop models to scene)
- 3D Scene View: Main visualization area
- Inspector: Panel for editing selected model properties (Position, Rotation, Scale)

**Model Types:**
- Vehicle: Cars, buses, trucks，etc.
- Lane: Sidewalks, drive lanes, bike lanes，bus lanes，etc.
- Plants: Trees, bushes, flowers，etc.
- Character: Pedestrians
- Building: Buildings, structures, boundaries，fence
- Signs: Traffic signs, stop signs, speed limit signs，etc.
- Fixture: Street lamps, benches, trash cans，etc.

## Data Flow

1. User enters Streetmix URL → API Endpoint fetches JSON street data
2. Streetmix JSON parsed into internal 3D model structure
3. Street segments converted to 3D mesh (width, elevation, boundaries)
4. Models placed in scene with bounding boxes
5. User manipulates models → transforms saved via Backend API → PostgreSQL

## Streetmix Data Structure

Key Streetmix JSON fields:
- `street.width`: Street width in meters
- `street.segments[]`: Array of street segments (driveways, sidewalks)
- `segments.type`: Segment type (sidewalk, drive-lane, etc.)
- `segments.variantString`: Variant/variant type
- `segments.width`: Segment width
- `segments.elevation`: Elevation
- `boundary.left/right`: Left/right boundary arrays
- `skybox`: Skybox texture/environment
