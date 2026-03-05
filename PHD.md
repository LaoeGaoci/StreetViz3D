# PHD.md

## Project Design Document (PDD) - StreetViz3D

### 1. Project Overview

StreetViz3D is a 3D street visualization and editing platform that enables users to:
- Fetch street data from Streetmix API using URLs
- Render 3D street scenes based on the fetched data
- Add, edit, and manipulate 3D models within the street environment
- Save/load street configurations

### 2. System Architecture

#### 2.1 Tech Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| Frontend Framework | Next.js 16.1.6 + React 19 | UI rendering & state management |
| UI Components | PrimeReact | Reusable UI components (Topbar, Sidebar, Panel Inspector) |
| 3D Rendering | Three.js + A-Frame | 3D scene rendering & WebXR support |
| Backend Framework | Spring Boot 4.0.3 | RESTful API & business logic |
| ORM | MyBatis-Plus | Database operations |
| Database | PostgreSQL | Data persistence |
| External API | Streetmix API | Street data source |

#### 2.2 Data Flow

```
User enters Streetmix URL
    ↓
Frontend API Endpoint fetches JSON street data from Streetmix
    ↓
Parse Streetmix JSON into internal model structure
    ↓
Convert street segments to 3D mesh (width, elevation, boundaries)
    ↓
Place models in scene with bounding boxes
    ↓
User manipulates models → transforms saved via Backend API → PostgreSQL
```

### 3. Frontend Architecture

#### 3.1 Layout Structure

```
+-----------------------------------------------------+
| Topbar  |  Logo | StreetViz3D  |  Street URL Input  |
+-----------------------------------------------------+
|         |                                      |     |
| Sidebar |          3D Scene View               |     |
|         |                                      |     |
| Model   |                                      |     |
| Library |                                      |     |
|         |                                      |     |
| Panel   |                                      |     |
|         |                                      |     |
+---------+--------------------------------------+-----+
|         |           Inspector Panel            |     |
| Model Library Panel (Add Model)                 |     |
+-----------------------------------------------------+
```

#### 3.2 Model Types

| Category | Example Models | Data Source |
|----------|----------------|-------------|
| Vehicle | Cars, buses, trucks, motorcycles | User uploads / Streetmix data |
| Lane | Sidewalks, drive lanes, bike lanes, bus lanes | Streetmix segments / user uploads |
| Plants | Trees, bushes, flowers, shrubs | Streetmix data / user uploads |
| Character | Pedestrians | Streetmix data / user uploads |
| Building | Buildings, structures, boundaries, fences | Streetmix boundaries / user uploads |
| Signs | Traffic signs, stop signs, speed limit signs | Streetmix data / user uploads |
| Fixture | Street lamps, benches, trash cans | Streetmix data / user uploads |

#### 3.3 User Interactions

| Action | Description | Technical Notes |
|--------|-------------|-----------------|
| Add Model | Click "Add Model" to open panel, select model, place in scene | Models render with Bounding Box |
| Select Model | Click model in scene to select | Highlight with different color |
| Move Model | Drag selected model | Use Position transform controls |
| Rotate Model | Rotate selected model in 3D space | Use Rotation transform controls (Gizmo) |
| Scale Model | Resize selected model | Use Scale transform controls |
| Duplicate Model | Clone selected model with same properties | Copy Position + Transform |
| Delete Model | Select and Delete or use Delete button | Remove from scene & database |
| Filter Models | Filter models by type in sidebar | Show/hide by category (visible/hidden/deleted) |
| Import/Export Models | Import models from library | Load models with saved transforms |

#### 3.4 Inspector Panel UI

```
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [Model Name]                    [x] % Close
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [Parent Model]                      % Change parent model
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Position
%   X: [____] Y: [____] Z: [____]   %
% Rotation
%   X: [____] Y: [____] Z: [____]   %
% Scale
%   X: [____] Y: [____] Z: [____]   %
|Size                                     |
%   Width: [____] Height: [____] Depth: [____] %
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Model Details
%   Type: [____]                           %
%   ID: [____]                             %
%   Source: [____]                         %
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [Save Changes] [Delete Model]             %
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
```

### 4. Backend Architecture

```
%%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%
%  Streetmix  %  %  Spring  %  %  PostgreSQL   %
%     API     %==>%   Boot   %==>%               %
%              %  %          %  %               %
%%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%
                          %        REST API       %
                          %%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%
%  PrimeReact %  %  React    %  %  Three.js     %
% (UI Lib)    %==>%           %==>%               %
%              %  %           %  %               %
%%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%  %%%%%%%%%%%%%%%%%%%%%%%%
                          %     (3D Render)       %
                          %%%%%%%%%%%%%%%%%%%%%%%%
```

### 5. Implementation Roadmap

#### 5.1 Streetmix Data Parsing & 3D Rendering

1. Parse `segments` array and create mesh based on width
2. Map `type` and `variantString` to lane model types
3. Use `elevation` property for vertical position
4. Generate Building models from `boundary` arrays
5. Implement add/remove/duplicate/delete model functions

#### 5.2 Model Implementation Strategy

- **Lane-based models**: Built from Streetmix data with user-added transformations
- **Other category models**: User uploads with drag-and-drop placement
- **Vehicle/Character**: Placed by user on top of street surface
- **Building**: Generated from boundary data or user-uploaded

### 6. Future Enhancements

| Feature | Description | Implementation Priority |
|---------|-------------|------------------------|
| Model Asset Library | Pre-loaded 3D models for quick placement | High |
| Export Scene | Export 3D scene to GLB/OBJ format | Medium |
| Import Scene | Import saved street configurations | Medium |
| Real-time Collaboration | Multiple users editing same street | Low |
| AR Preview | Mobile AR view of street scene | Low |

### 7. Database Schema

```sql
-- Street configurations
CREATE TABLE streets (
    id SERIAL PRIMARY KEY,
    streetmix_id VARCHAR(255) UNIQUE,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Models in scene
CREATE TABLE models (
    id SERIAL PRIMARY KEY,
    street_id INTEGER REFERENCES streets(id),
    type VARCHAR(50), -- Vehicle, Lane, Plants, Character, Building, Signs, Fixture
    source VARCHAR(255), -- Streetmix or user upload
    source_id VARCHAR(255), -- Original source identifier
    position_x FLOAT,
    position_y FLOAT,
    position_z FLOAT,
    rotation_x FLOAT,
    rotation_y FLOAT,
    rotation_z FLOAT,
    scale_x FLOAT,
    scale_y FLOAT,
    scale_z FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
