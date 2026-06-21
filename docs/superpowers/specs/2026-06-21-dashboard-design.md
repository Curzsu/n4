# N4 Dashboard Design Specification

**Date:** 2026-06-21
**Scope:** Dashboard (first iteration) — building device monitoring overview page
**Status:** Approved for implementation

---

## 1. Overview

Build the Dashboard page of the N4 device monitoring and alarm platform, showing the overall working status of building equipment. The page follows a dark-themed data center monitoring style with KPI metrics, alarm summary, system status, and trend charts.

The first iteration focuses on UI development. Real database data is used for device hierarchy and system status queries; mock/hardcoded data is used for alarms, KPI metrics, and trend charts. Alarm and trend database tables (`alarm_event`, `point_history`) will be added in a future iteration.

**Key constraints:**
- All code and comments must be in English
- Frontend uses Vue3 + Vite + Element Plus + ECharts
- Backend uses Spring Boot 4.0.7 + MyBatis + MySQL
- Frontend and backend live in a single git repository (flat monorepo)
- Building visualization area uses a static placeholder image (no 3D rendering)
- No database schema extensions in this iteration

---

## 2. Project Structure

```
D:\N4_alarm\
├── backend/                         # Spring Boot project
│   ├── pom.xml
│   └── src/main/java/com/n4monitor/
│       ├── N4MonitorApplication.java    # Entry point (existing)
│       ├── controller/
│       │   └── DashboardController.java # Dashboard API endpoints
│       │   └── DeviceController.java    # Device tree endpoint
│       ├── service/
│       │   ├── DashboardService.java
│       │   └── DeviceService.java
│       ├── mapper/
│       │   ├── DeviceMapper.java
│       │   └── MonitorPointMapper.java
│       ├── entity/
│       │   ├── Device.java
│       │   ├── MonitorPoint.java
│       │   ├── Product.java
│       │   ├── Location.java
│       │   └── DictValue.java
│       ├── dto/
│       │   ├── KpiMetricsDto.java
│       │   ├── AlarmSummaryDto.java
│       │   ├── AlarmItemDto.java
│       │   ├── SystemStatusDto.java
│       │   ├── TrendDataDto.java
│       │   └── DeviceTreeNodeDto.java
│       └── config/
│           └── CorsConfig.java           # CORS for frontend dev proxy
│   └── src/main/resources/
│       ├── application.yaml              # DB config (existing)
│       └── mapper/
│           ├── DeviceMapper.xml
│           └── MonitorPointMapper.xml
├── frontend/                        # Vue3 + Vite project
│   ├── package.json
│   ├── vite.config.ts                # Dev proxy to backend
│   ├── tsconfig.json
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── api/
│       │   ├── dashboard.ts            # Dashboard API calls
│       │   └── device.ts               # Device tree API calls
│       ├── layouts/
│       │   └── MainLayout.vue          # TopNav + Sidebar + Content + BottomBar
│       ├── views/
│       │   └── DashboardView.vue       # Dashboard page
│       ├── components/
│       │   ├── TopNavbar.vue           # Logo, nav tabs, alert badges, user
│       │   ├── LeftSidebar.vue         # Site tree navigation
│       │   ├── BottomToolbar.vue       # Fixed bottom action bar
│       │   ├── KpiCardsRow.vue         # 6 KPI metric cards
│       │   ├── BuildingPlaceholder.vue # Static building image area
│       │   ├── AlarmSummary.vue        # Alarm level counts + top5 list
│       │   ├── SystemStatus.vue        # System health list
│       │   └── TrendChartsGrid.vue     # 4-chart grid wrapper
│       │   └── charts/
│       │       ├── PowerTrendChart.vue
│       │       ├── EnergyTrendChart.vue
│       │       ├── PueTrendChart.vue
│       │       └── TemperatureTrendChart.vue
│       ├── stores/
│       │   └── dashboard.ts            # Pinia store for dashboard data
│       ├── router/
│       │   └ index.ts                  # Route definitions
│       ├── styles/
│       │   ├── global.scss             # Dark theme base styles
│       │   ├── variables.scss          # Theme color variables
│       │   └── dashboard.scss          # Dashboard-specific styles
│       └── assets/
│           └── building-placeholder.svg # Static building image
├── docs/                            # Design documents & materials
│   ├── 材料/                        # Existing project materials
│   └── superpowers/specs/           # Design specifications
├── .gitignore
└── .git                             # Single git repo at root
```

The existing `n4-monitor/.git` will be removed. Git will be initialized at `D:\N4_alarm` root level.

---

## 3. Dashboard Page Layout

```
┌──────────────────────────────────────────────────────────────────┐
│ TopNavbar: Logo | Tabs(Dashboard/Electrical/...) | AlertBadges | User │
├────────┬───────────────────────────────────┬─────────────────────┤
│        │                                   │                     │
│ Left   │   KPI Cards Row                   │ Alarm Summary       │
│ Sidebar│  PUE | IT Load | Cooling | Fac    │ C:3 M:14 m:27 W:44 │
│        │  Energy | Capacity | Devices      │                     │
│        │                                   │ Top5 Alarm List     │
│ Site   │───────────────────────────────────│                     │
│ tree   │                                   │─────────────────────│
│ nav    │   Building Placeholder             │                     │
│        │   (static image)                   │ System Status       │
│        │                                   │  Electrical Normal   │
│        │───────────────────────────────────│  Cooling Normal     │
│        │                                   │  UPS Normal          │
│        │   Trend Charts (2x2 grid)         │  PDU Warning        │
│        │  Power | Energy                   │  CRAH Normal        │
│        │  PUE   | Temperature              │  Sensor Normal      │
│        │                                   │  Network Normal      │
├────────┴───────────────────────────────────┴─────────────────────┤
│ BottomToolbar: AlarmCenter | Trend | Report | WorkOrder | Settings  │
└──────────────────────────────────────────────────────────────────┘
```

**Color scheme (dark theme):**
- Background: #0a0e17 (deep dark blue)
- Card background: #111827
- Card border: #1f2937
- Text primary: #e5e7eb
- Text secondary: #9ca3af
- Accent green: #10b981 (normal status)
- Accent red: #ef4444 (critical)
- Accent orange: #f97316 (major)
- Accent yellow: #eab308 (minor)
- Accent blue: #3b82f6 (warning)

---

## 4. KPI Metric Cards

Six cards displayed in a horizontal row at the top of the main content area.

| Card | Primary Value | Secondary Info | Mock Value |
|------|---------------|----------------|------------|
| PUE | 1.32 | +0.03 vs yesterday | 1.32 |
| IT Load | 68% | 1,876 / 2,780 kW | 68% |
| Cooling Load | 42% | 1,153 / 2,780 kW | 42% |
| Facility Load | 57% | 1,584 / 2,780 kW | 57% |
| Total Energy | 32,680 kWh | Today | 32,680 |
| Capacity Usage | 78% | 1,426 / 1,820 devices online | 78% |

Each card has an icon, the primary value in large font, and the secondary detail in smaller text. Cards with positive deviations show green; negative show red/orange.

---

## 5. Alarm Summary Panel

Located in the right sidebar, above the System Status panel.

**Alarm level counts** (colored circles with numbers):
- Critical: 3 (red)
- Major: 14 (orange)
- Minor: 27 (yellow)
- Warning: 44 (blue)

**Top 5 active alarms list:**
- Timestamp | Description | Severity indicator dot
- Mock data:
  - 10:21 — CHU-A3 High Pressure (Critical)
  - 10:18 — UPS-A Battery Fault (Major)
  - 10:15 — PDU-B Overload (Major)
  - 10:11 — CHWS Temp High (Minor)
  - 10:05 — AHU-3 Filter Dirty (Minor)

"View All" link at the top-right of the panel.

---

## 6. System Status Panel

Located in the right sidebar, below the Alarm Summary panel.

Each row shows: Icon + System name + Status label + Device count.

| System | Status | Count | Icon |
|--------|--------|-------|------|
| Electrical | Normal | 28 | ⚡ Gear |
| Cooling | Normal | 18 | ❄ Snowflake |
| UPS | Normal | 12 | Battery |
| PDU | Warning | 4 | ⚠ Warning triangle |
| CRAH | Normal | 132 | Fan |
| Sensor | Normal | 1,248 | Thermometer |
| Network | Normal | 8 | Globe |

Device counts are queried from the real database (devices grouped by product type). Health status labels (Normal/Warning/Critical) are mock in this iteration since no aggregation logic exists yet — PDU is hardcoded as Warning to demonstrate the yellow indicator. In a future iteration, status will be derived from device `point_status` aggregation rules.

"View All" link at the top-right.

---

## 7. Trend Charts (2x2 Grid)

Four ECharts panels in a 2x2 grid below the building placeholder area.

**Power Trend (kW):**
- Line chart, 24-hour x-axis (00:00–24:00)
- Two lines: IT Load (blue) and Total Load (gray)
- Y-axis: 0–4,500 kW
- Mock data: sine-wave patterns for realistic appearance

**Energy Trend (kWh/d):**
- Bar chart, hourly bars for Today vs Yesterday
- Green bars for today, gray dashed for yesterday
- Y-axis: 0–3,000 kWh

**PUE Trend:**
- Line chart, green line over 24 hours
- Y-axis: 0.8–2.0
- Mock data: slightly fluctuating around 1.32

**Temperature Trend (°C):**
- Line chart with two lines: Average Temp (green), Max Temp (cyan)
- Y-axis: 10–40°C
- Mock data: realistic temperature curve

---

## 8. Left Sidebar Navigation

Displays the device hierarchy tree based on the `device` table's `sys_parent_id` / `sys_path` structure.

- Top: Site identifier "SITE-JHB-01" with dropdown
- Search bar for filtering
- Tree items loaded from `/api/devices/tree` endpoint
- Items are expandable/collapsible
- "Overview" item highlighted as current page
- Bottom: Collapse toggle ("<< Collapse")

The tree data maps `device` records to a tree structure using `sys_parent_id` self-reference. Leaf nodes show device names; parent nodes show system categories.

---

## 9. Top Navigation Bar

- Left: "DAYONE | N4 Monitor" logo text
- Center: Tab navigation — Dashboard (active/green underline), Electrical, Cooling, Mechanical, Data Hall, Alarms, Trend, Report, System
- Right: Alert summary badges (Critical 3, Major 14, Minor 27, Warning 44), search icon, user profile ("admin")

The tabs beyond Dashboard are placeholders for future pages. Only Dashboard is functional in this iteration.

---

## 10. Bottom Toolbar

Fixed at the bottom of the page, outside the content scroll area.

Five action items with icons:
- Alarm Center (with red badge showing critical count)
- Trend Viewer
- Report Center
- Work Order
- System Settings

All are non-functional placeholders in this iteration, showing icons and labels only.

---

## 11. API Endpoints (First Iteration)

| Endpoint | Method | Data Source | Description |
|----------|--------|-------------|-------------|
| `/api/dashboard/kpi` | GET | Mock JSON | KPI aggregated metrics |
| `/api/dashboard/alarm-summary` | GET | Mock JSON | Alarm counts by level |
| `/api/dashboard/alarm-top5` | GET | Mock JSON | Top 5 active alarms |
| `/api/dashboard/system-status` | GET | DB (device + product) | System health by product type |
| `/api/dashboard/power-trend` | GET | Mock JSON | 24h power trend series |
| `/api/dashboard/energy-trend` | GET | Mock JSON | 24h energy trend series |
| `/api/dashboard/pue-trend` | GET | Mock JSON | 24h PUE trend series |
| `/api/dashboard/temperature-trend` | GET | Mock JSON | 24h temperature trend series |
| `/api/devices/tree` | GET | DB (device hierarchy) | Device tree for sidebar |

Mock endpoints return hardcoded JSON objects (for KPI, alarms) or programmatically generated time-series data (for trends — backend generates 24 hourly data points using realistic patterns like sine waves and gradual curves, not large hardcoded arrays). DB-backed endpoints query the existing `device`, `monitor_point`, `product`, and `dict_value` tables.

---

## 12. Frontend-Backend Communication

- Vite dev server runs on port 5173
- Spring Boot runs on port 8080
- `vite.config.ts` proxy: `/api/*` → `http://localhost:8080`
- Backend CORS config allows `localhost:5173` for development
- API responses use consistent JSON structure:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": { ... }
  }
  ```

---

## 13. Building Placeholder Area

The central area between KPI cards and trend charts displays a static placeholder image representing the campus/building layout.

- Placeholder is an SVG showing simplified building outlines (resolution-independent, created programmatically)
- Labels on the image: "Building A", "Building B", "Utility Plant", "Substation", "Cooling Plant"
- Each building has a small status badge (green = Normal)
- This area is purely visual; no interactivity in this iteration

---

## 14. Future Iterations (Out of Scope)

The following are explicitly excluded from this first iteration:

- 3D building digital twin visualization
- `alarm_event` database table (alarm CRUD, real-time updates)
- `point_history` database table (historical data storage)
- Real-time data push (WebSocket/SSE)
- Alarm detail view, alarm acknowledgment actions
- Trend chart date range selection
- Other navigation tabs (Electrical, Cooling, etc.)
- Bottom toolbar actions (Alarm Center, Report, Work Order)
- Authentication / login system
- User management
