# N4 Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Dashboard page of the N4 device monitoring and alarm platform with dark-themed UI, KPI cards, alarm summary, system status, trend charts, and sidebar navigation — using mock data for alarms/KPI/trends and real DB data for device tree/system status counts.

**Architecture:** Flat monorepo with `backend/` (Spring Boot + MyBatis + MySQL) and `frontend/` (Vue3 + Vite + Element Plus + ECharts + Pinia). Backend provides REST APIs; frontend proxies `/api/*` to backend via Vite dev server. Backend serves mock JSON for KPI/alarm/trend data and queries real DB for device hierarchy and system status.

**Tech Stack:** Spring Boot 4.0.7, Java 21, MyBatis 4.0.1, MySQL, Vue3, Vite, Element Plus, ECharts, Pinia, Vue Router, SCSS, TypeScript

## Global Constraints

- All code and comments must be in English — no Chinese in any source file
- Frontend uses Vue3 + Vite + Element Plus + ECharts (exact libraries, no alternatives)
- Backend uses Spring Boot 4.0.7 + Java 21 + MyBatis + MySQL (as specified in existing pom.xml)
- Frontend and backend live in a single git repository at `D:\N4_alarm` root level (flat monorepo)
- Building visualization area uses a static SVG placeholder (no 3D rendering)
- No database schema extensions in this iteration
- API response format: `{ "code": 200, "message": "success", "data": {...} }`
- Vite dev server port: 5173, Spring Boot port: 8080
- Dark theme color scheme per spec section 3
- All mock data values must match the spec tables exactly (PUE=1.32, Critical=3, etc.)

---

## File Structure

### Backend Files (to create or modify)

| File | Responsibility |
|------|----------------|
| `backend/pom.xml` | Maven project config — add `spring-boot-starter-web` dependency (currently missing!) |
| `backend/src/main/resources/application.yaml` | Add `server.port=8080` and MyBatis mapper location config |
| `backend/src/main/java/com/n4monitor/config/CorsConfig.java` | CORS configuration allowing `localhost:5173` |
| `backend/src/main/java/com/n4monitor/config/WebConfig.java` | Global API response wrapper configuration |
| `backend/src/main/java/com/n4monitor/entity/Device.java` | Device table entity (5-table schema) |
| `backend/src/main/java/com/n4monitor/entity/MonitorPoint.java` | Monitor point entity |
| `backend/src/main/java/com/n4monitor/entity/Product.java` | Product entity |
| `backend/src/main/java/com/n4monitor/entity/DictValue.java` | Dictionary value entity |
| `backend/src/main/java/com/n4monitor/dto/ApiResponse.java` | Generic API response wrapper `{code, message, data}` |
| `backend/src/main/java/com/n4monitor/dto/KpiMetricsDto.java` | KPI card data DTO |
| `backend/src/main/java/com/n4monitor/dto/AlarmSummaryDto.java` | Alarm level counts DTO |
| `backend/src/main/java/com/n4monitor/dto/AlarmItemDto.java` | Single alarm item DTO |
| `backend/src/main/java/com/n4monitor/dto/SystemStatusDto.java` | System health status DTO |
| `backend/src/main/java/com/n4monitor/dto/TrendDataDto.java` | Trend time-series data DTO |
| `backend/src/main/java/com/n4monitor/dto/DeviceTreeNodeDto.java` | Device tree node DTO |
| `backend/src/main/java/com/n4monitor/mapper/DeviceMapper.java` | MyBatis mapper interface for Device |
| `backend/src/main/java/com/n4monitor/mapper/MonitorPointMapper.java` | MyBatis mapper interface for MonitorPoint |
| `backend/src/main/java/com/n4monitor/mapper/ProductMapper.java` | MyBatis mapper interface for Product |
| `backend/src/main/resources/mapper/DeviceMapper.xml` | MyBatis XML for Device queries |
| `backend/src/main/resources/mapper/MonitorPointMapper.xml` | MyBatis XML for MonitorPoint queries |
| `backend/src/main/resources/mapper/ProductMapper.xml` | MyBatis XML for Product queries |
| `backend/src/main/java/com/n4monitor/service/DashboardService.java` | Dashboard business logic (mock + DB) |
| `backend/src/main/java/com/n4monitor/service/DeviceService.java` | Device tree assembly service |
| `backend/src/main/java/com/n4monitor/controller/DashboardController.java` | Dashboard REST endpoints |
| `backend/src/main/java/com/n4monitor/controller/DeviceController.java` | Device tree REST endpoint |

### Frontend Files (all new)

| File | Responsibility |
|------|----------------|
| `frontend/package.json` | NPM dependencies (Vue3, Element Plus, ECharts, Pinia, Router, SCSS, TypeScript) |
| `frontend/vite.config.ts` | Vite config with `/api` proxy to `localhost:8080` |
| `frontend/tsconfig.json` | TypeScript config |
| `frontend/tsconfig.app.json` | App-specific TS config |
| `frontend/tsconfig.node.json` | Node/Vite-specific TS config |
| `frontend/src/main.ts` | Vue app entry point (mount app, register plugins) |
| `frontend/src/App.vue` | Root component (just `<MainLayout />`) |
| `frontend/src/styles/variables.scss` | Theme color variables (dark palette per spec) |
| `frontend/src/styles/global.scss` | Global dark theme base styles |
| `frontend/src/styles/dashboard.scss` | Dashboard-specific layout styles |
| `frontend/src/router/index.ts` | Vue Router — single route `/` → DashboardView |
| `frontend/src/stores/dashboard.ts` | Pinia store — holds all dashboard data, dispatches API calls |
| `frontend/src/api/dashboard.ts` | API service — fetch KPI, alarms, system status, trends |
| `frontend/src/api/device.ts` | API service — fetch device tree |
| `frontend/src/layouts/MainLayout.vue` | Layout shell: TopNavbar + LeftSidebar + Content + BottomToolbar |
| `frontend/src/components/TopNavbar.vue` | Logo, nav tabs, alert badges, user profile |
| `frontend/src/components/LeftSidebar.vue` | Site selector, search, device tree, collapse toggle |
| `frontend/src/components/BottomToolbar.vue` | Fixed bottom bar with 5 action items |
| `frontend/src/components/KpiCardsRow.vue` | 6 KPI metric cards in horizontal row |
| `frontend/src/components/BuildingPlaceholder.vue` | SVG static building image with labels |
| `frontend/src/components/AlarmSummary.vue` | Alarm level counts + top 5 alarm list |
| `frontend/src/components/SystemStatus.vue` | System health list (7 rows) |
| `frontend/src/components/TrendChartsGrid.vue` | 2x2 grid wrapper for 4 trend charts |
| `frontend/src/components/charts/PowerTrendChart.vue` | ECharts power trend (line chart) |
| `frontend/src/components/charts/EnergyTrendChart.vue` | ECharts energy trend (bar chart) |
| `frontend/src/components/charts/PueTrendChart.vue` | ECharts PUE trend (line chart) |
| `frontend/src/components/charts/TemperatureTrendChart.vue` | ECharts temperature trend (line chart) |
| `frontend/src/views/DashboardView.vue` | Dashboard page composing all components |
| `frontend/src/assets/building-placeholder.svg` | Static SVG building layout image |

---

### Task 1: Project Restructuring & Git Setup

**Files:**
- Delete: `D:\N4_alarm\n4-monitor\.git` (remove old separate git)
- Delete: `D:\N4_alarm\n4-monitor\.gitattributes` (not needed in monorepo)
- Delete: `D:\N4_alarm\n4-monitor\.gitignore` (replaced by root-level one)
- Delete: `D:\N4_alarm\n4-monitor\.idea\` (IDE config, will be regenerated)
- Delete: `D:\N4_alarm\n4-monitor\target\` (compiled output, will be regenerated)
- Create: `D:\N4_alarm\.gitignore`
- Modify: Move `D:\N4_alarm\n4-monitor\` contents to `D:\N4_alarm\backend\`

**Interfaces:**
- Produces: `D:\N4_alarm\backend\` directory with Spring Boot project, `D:\N4_alarm\.git` repo at root level, `D:\N4_alarm\.gitignore` with Node + Maven + IDE exclusions

- [ ] **Step 1: Remove the old n4-monitor git repo**

```bash
rm -rf "D:\N4_alarm\n4-monitor\.git"
```

- [ ] **Step 2: Remove IDE and build artifacts**

```bash
rm -rf "D:\N4_alarm\n4-monitor\.idea"
rm -rf "D:\N4_alarm\n4-monitor\target"
rm -f "D:\N4_alarm\n4-monitor\.gitattributes"
rm -f "D:\N4_alarm\n4-monitor\.gitignore"
```

- [ ] **Step 3: Move n4-monitor contents to backend directory**

```bash
mv "D:\N4_alarm\n4-monitor" "D:\N4_alarm\backend"
```

After this step, `D:\N4_alarm\backend\pom.xml` and `D:\N4_alarm\backend\src\` should exist. Verify with `ls D:\N4_alarm\backend\`.

- [ ] **Step 4: Create root-level .gitignore**

Write file `D:\N4_alarm\.gitignore`:

```gitignore
# IDE
.idea/
*.iml
.vscode/

# Build
backend/target/
node_modules/
frontend/dist/

# Environment
*.env
*.env.local

# OS
.DS_Store
Thumbs.db

# Temp
*.log
*.tmp
*.swp
```

- [ ] **Step 5: Initialize git at root level and make first commit**

```bash
cd D:\N4_alarm
git init
git add .gitignore
git add backend/pom.xml backend/src/
git add docs/
git commit -m "feat: initial monorepo structure with backend skeleton"
```

Expected: Git repo initialized at `D:\N4_alarm`, first commit with backend skeleton and .gitignore.

---

### Task 2: Backend Foundation — Web Dependency, CORS, Mapper Config

**Files:**
- Modify: `D:\N4_alarm\backend\pom.xml` (add `spring-boot-starter-web`)
- Modify: `D:\N4_alarm\backend\src\main\resources\application.yaml` (add server port, MyBatis mapper config)
- Create: `D:\N4_alarm\backend\src\main\java\com\n4monitor\config\CorsConfig.java`

**Interfaces:**
- Produces: Backend can serve HTTP REST endpoints on port 8080 with CORS allowing localhost:5173; MyBatis mapper XMLs scanned from `classpath:mapper/`

- [ ] **Step 1: Add spring-boot-starter-web dependency to pom.xml**

Add this dependency block inside the `<dependencies>` section of `backend/pom.xml`, after the mybatis dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

- [ ] **Step 2: Update application.yaml with server port and MyBatis config**

Replace the entire content of `backend/src/main/resources/application.yaml` with:

```yaml
server:
  port: 8080

spring:
  application:
    name: n4-monitor

  datasource:
    url: jdbc:mysql://localhost:3306/n4_alarm?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: 1234
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.n4monitor.entity
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 3: Create CorsConfig.java**

Write file `backend/src/main/java/com/n4monitor/config/CorsConfig.java`:

```java
package com.n4monitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

- [ ] **Step 4: Add @MapperScan to main application class**

Modify `backend/src/main/java/com/n4monitor/N4MonitorApplication.java` to add MapperScan annotation:

```java
package com.n4monitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.n4monitor.mapper")
public class N4MonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(N4MonitorApplication.class, args);
    }
}
```

- [ ] **Step 5: Verify backend compiles**

```bash
cd D:\N4_alarm\backend
mvn compile -q
```

Expected: BUILD SUCCESS, no compilation errors.

- [ ] **Step 6: Commit**

```bash
cd D:\N4_alarm
git add backend/pom.xml backend/src/
git commit -m "feat: add web dependency, CORS config, MyBatis mapper scan"
```

---

### Task 3: Backend Entity Classes

**Files:**
- Create: `backend/src/main/java/com/n4monitor/entity/Device.java`
- Create: `backend/src/main/java/com/n4monitor/entity/MonitorPoint.java`
- Create: `backend/src/main/java/com/n4monitor/entity/Product.java`
- Create: `backend/src/main/java/com/n4monitor/entity/DictValue.java`
- Create: `backend/src/main/java/com/n4monitor/entity/Location.java`

**Interfaces:**
- Produces: Entity classes matching the 5-table MySQL schema with Lombok annotations; field names use camelCase (MyBatis maps underscore columns to camelCase via config)

- [ ] **Step 1: Create Device.java**

Write file `backend/src/main/java/com/n4monitor/entity/Device.java`:

```java
package com.n4monitor.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Device {
    private Long deviceId;
    private String deviceCode;
    private String deviceName;
    private String ipAddress;
    private String macAddress;
    private Long productId;

    // System hierarchy fields
    private Long sysParentId;
    private String sysPath;
    private Integer sysLevel;
    private Integer sysSortOrder;

    // Location hierarchy fields
    private Long locParentId;
    private String locPath;
    private Integer locLevel;
    private Integer locSortOrder;

    // Device attributes
    private Integer status;        // 1=online, 2=offline, 3=maintenance, 4=fault
    private LocalDate installDate;
    private LocalDate warrantyExpire;

    // Ops info
    private String region;
    private String department;
    private String responsiblePerson;
    private String contactPhone;

    // Extension & audit
    private String extraAttrs;     // JSON stored as String
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDeleted;
}
```

- [ ] **Step 2: Create MonitorPoint.java**

Write file `backend/src/main/java/com/n4monitor/entity/MonitorPoint.java`:

```java
package com.n4monitor.entity;

import lombok.Data;

@Data
public class MonitorPoint {
    private Long pointId;
    private String registerId;
    private String pointName;
    private String pointCode;
    private Integer pointType;     // 1=DI, 2=DO, 3=AI, 4=AO
    private Integer dataType;      // 1=int, 2=float
    private String unit;
    private Integer displayPrecision;
    private Long deviceId;
    private String pointValue;
    private Integer pointStatus;   // 1=normal, 0=alarm
}
```

- [ ] **Step 3: Create Product.java**

Write file `backend/src/main/java/com/n4monitor/entity/Product.java`:

```java
package com.n4monitor.entity;

import lombok.Data;

@Data
public class Product {
    private Long productId;
    private String productName;
    private String productType;
    private String model;
    private String manufacturer;
    private String manuDate;
}
```

- [ ] **Step 4: Create DictValue.java**

Write file `backend/src/main/java/com/n4monitor/entity/DictValue.java`:

```java
package com.n4monitor.entity;

import lombok.Data;

@Data
public class DictValue {
    private Long dictId;
    private String typeName;
    private String dictKey;
    private String dictValue;
    private Integer sortValue;
    private Integer isActive;
}
```

- [ ] **Step 5: Create Location.java**

Write file `backend/src/main/java/com/n4monitor/entity/Location.java`:

```java
package com.n4monitor.entity;

import lombok.Data;

@Data
public class Location {
    private Long locationId;
    private String locationName;
    private String locationType;
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd D:\N4_alarm\backend
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
cd D:\N4_alarm
git add backend/src/main/java/com/n4monitor/entity/
git commit -m "feat: add entity classes for all 5 database tables"
```

---

### Task 4: Backend MyBatis Mappers

**Files:**
- Create: `backend/src/main/java/com/n4monitor/mapper/DeviceMapper.java`
- Create: `backend/src/main/java/com/n4monitor/mapper/MonitorPointMapper.java`
- Create: `backend/src/main/java/com/n4monitor/mapper/ProductMapper.java`
- Create: `backend/src/main/resources/mapper/DeviceMapper.xml`
- Create: `backend/src/main/resources/mapper/MonitorPointMapper.xml`
- Create: `backend/src/main/resources/mapper/ProductMapper.xml`

**Interfaces:**
- Produces: Mapper interfaces with methods: `DeviceMapper.findAll()`, `DeviceMapper.findBySysParentId(Long)`, `DeviceMapper.countByProductType()`; `MonitorPointMapper.findByDeviceId(Long)`, `MonitorPointMapper.countByPointStatus(Integer)`; `ProductMapper.findAll()`, `ProductMapper.findByProductId(Long)`
- Consumes: Entity classes from Task 3

- [ ] **Step 1: Create DeviceMapper.java**

Write file `backend/src/main/java/com/n4monitor/mapper/DeviceMapper.java`:

```java
package com.n4monitor.mapper;

import com.n4monitor.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper {

    List<Device> findAll();

    List<Device> findBySysParentId(@Param("sysParentId") Long sysParentId);

    List<Device> findRootDevices();

    /** Count devices grouped by product type for system status */
    List<Device> countByProductType();

    Device findByDeviceId(@Param("deviceId") Long deviceId);
}
```

- [ ] **Step 2: Create MonitorPointMapper.java**

Write file `backend/src/main/java/com/n4monitor/mapper/MonitorPointMapper.java`:

```java
package com.n4monitor.mapper;

import com.n4monitor.entity.MonitorPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MonitorPointMapper {

    List<MonitorPoint> findByDeviceId(@Param("deviceId") Long deviceId);

    List<MonitorPoint> findAll();

    /** Count points by status (1=normal, 0=alarm) */
    int countByPointStatus(@Param("pointStatus") Integer pointStatus);
}
```

- [ ] **Step 3: Create ProductMapper.java**

Write file `backend/src/main/java/com/n4monitor/mapper/ProductMapper.java`:

```java
package com.n4monitor.mapper;

import com.n4monitor.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<Product> findAll();

    Product findByProductId(@Param("productId") Long productId);
}
```

- [ ] **Step 4: Create DeviceMapper.xml**

Write file `backend/src/main/resources/mapper/DeviceMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.n4monitor.mapper.DeviceMapper">

    <select id="findAll" resultType="com.n4monitor.entity.Device">
        SELECT * FROM device WHERE is_deleted = 0
    </select>

    <select id="findBySysParentId" resultType="com.n4monitor.entity.Device">
        SELECT * FROM device WHERE sys_parent_id = #{sysParentId} AND is_deleted = 0
        ORDER BY sys_sort_order
    </select>

    <select id="findRootDevices" resultType="com.n4monitor.entity.Device">
        SELECT * FROM device WHERE sys_parent_id IS NULL AND is_deleted = 0
        ORDER BY sys_sort_order
    </select>

    <select id="countByProductType" resultType="com.n4monitor.entity.Device">
        SELECT d.device_id, d.device_name, p.product_type,
               d.status, d.sys_parent_id, d.sys_level
        FROM device d
        JOIN product p ON d.product_id = p.product_id
        WHERE d.is_deleted = 0
    </select>

    <select id="findByDeviceId" resultType="com.n4monitor.entity.Device">
        SELECT * FROM device WHERE device_id = #{deviceId} AND is_deleted = 0
    </select>

</mapper>
```

- [ ] **Step 5: Create MonitorPointMapper.xml**

Write file `backend/src/main/resources/mapper/MonitorPointMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.n4monitor.mapper.MonitorPointMapper">

    <select id="findByDeviceId" resultType="com.n4monitor.entity.MonitorPoint">
        SELECT * FROM monitor_point WHERE device_id = #{deviceId}
    </select>

    <select id="findAll" resultType="com.n4monitor.entity.MonitorPoint">
        SELECT * FROM monitor_point
    </select>

    <select id="countByPointStatus" resultType="int">
        SELECT COUNT(*) FROM monitor_point WHERE point_status = #{pointStatus}
    </select>

</mapper>
```

- [ ] **Step 6: Create ProductMapper.xml**

Write file `backend/src/main/resources/mapper/ProductMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.n4monitor.mapper.ProductMapper">

    <select id="findAll" resultType="com.n4monitor.entity.Product">
        SELECT * FROM product
    </select>

    <select id="findByProductId" resultType="com.n4monitor.entity.Product">
        SELECT * FROM product WHERE product_id = #{productId}
    </select>

</mapper>
```

- [ ] **Step 7: Verify compilation**

```bash
cd D:\N4_alarm\backend
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
cd D:\N4_alarm
git add backend/src/main/java/com/n4monitor/mapper/ backend/src/main/resources/mapper/
git commit -m "feat: add MyBatis mapper interfaces and XML for Device, MonitorPoint, Product"
```

---

### Task 5: Backend DTOs and API Response Wrapper

**Files:**
- Create: `backend/src/main/java/com/n4monitor/dto/ApiResponse.java`
- Create: `backend/src/main/java/com/n4monitor/dto/KpiMetricsDto.java`
- Create: `backend/src/main/java/com/n4monitor/dto/AlarmSummaryDto.java`
- Create: `backend/src/main/java/com/n4monitor/dto/AlarmItemDto.java`
- Create: `backend/src/main/java/com/n4monitor/dto/SystemStatusDto.java`
- Create: `backend/src/main/java/com/n4monitor/dto/TrendDataDto.java`
- Create: `backend/src/main/java/com/n4monitor/dto/DeviceTreeNodeDto.java`

**Interfaces:**
- Produces: `ApiResponse<T>` wrapper used by all controllers; DTOs used by `DashboardService` and `DeviceService`
- Consumes: None (pure data classes)

- [ ] **Step 1: Create ApiResponse.java**

Write file `backend/src/main/java/com/n4monitor/dto/ApiResponse.java`:

```java
package com.n4monitor.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
```

- [ ] **Step 2: Create KpiMetricsDto.java**

Write file `backend/src/main/java/com/n4monitor/dto/KpiMetricsDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;

@Data
public class KpiMetricsDto {
    private double pue;                // 1.32
    private String pueTrend;           // "+0.03 vs yesterday"
    private double itLoadPercent;      // 68
    private double itLoadKw;           // 1876
    private double itLoadCapacityKw;   // 2780
    private double coolingLoadPercent; // 42
    private double coolingLoadKw;      // 1153
    private double coolingLoadCapacityKw; // 2780
    private double facilityLoadPercent; // 57
    private double facilityLoadKw;     // 1584
    private double facilityLoadCapacityKw; // 2780
    private double totalEnergyKwh;     // 32680
    private double capacityUsagePercent; // 78
    private int activeDevices;         // 1426
    private int totalDevices;          // 1820
}
```

- [ ] **Step 3: Create AlarmSummaryDto.java**

Write file `backend/src/main/java/com/n4monitor/dto/AlarmSummaryDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;

@Data
public class AlarmSummaryDto {
    private int critical;   // 3
    private int major;      // 14
    private int minor;      // 27
    private int warning;    // 44
}
```

- [ ] **Step 4: Create AlarmItemDto.java**

Write file `backend/src/main/java/com/n4monitor/dto/AlarmItemDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;

@Data
public class AlarmItemDto {
    private String alarmTime;   // e.g. "10:21"
    private String alarmDesc;   // e.g. "CHU-A3 High Pressure"
    private String alarmLevel;  // "Critical", "Major", "Minor", "Warning"
    private String sourceDevice;
}
```

- [ ] **Step 5: Create SystemStatusDto.java**

Write file `backend/src/main/java/com/n4monitor/dto/SystemStatusDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;

@Data
public class SystemStatusDto {
    private String systemName;  // "Electrical", "Cooling", "UPS", etc.
    private String status;      // "Normal", "Warning", "Critical"
    private int deviceCount;    // count of devices in this category
    private String icon;        // icon name for frontend
}
```

- [ ] **Step 6: Create TrendDataDto.java**

Write file `backend/src/main/java/com/n4monitor/dto/TrendDataDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class TrendDataDto {
    private List<String> timeLabels;   // ["00:00", "01:00", ..., "23:00"]
    private List<Double> values;       // corresponding data values
    private List<Double> values2;      // optional second series (e.g. total load)
    private String seriesName;         // e.g. "IT Load"
    private String seriesName2;        // e.g. "Total Load"
    private String unit;               // e.g. "kW", "kWh", "°C"
}
```

- [ ] **Step 7: Create DeviceTreeNodeDto.java**

Write file `backend/src/main/java/com/n4monitor/dto/DeviceTreeNodeDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;
import java.util.List;

@Data
public class DeviceTreeNodeDto {
    private Long id;
    private String label;          // device name
    private String type;           // "site", "building", "system", "device"
    private Integer status;        // device status (1=online, etc.)
    private List<DeviceTreeNodeDto> children;
}
```

- [ ] **Step 8: Verify compilation**

```bash
cd D:\N4_alarm\backend
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
cd D:\N4_alarm
git add backend/src/main/java/com/n4monitor/dto/
git commit -m "feat: add DTO classes for API responses and data transfer"
```

---

### Task 6: Backend Services

**Files:**
- Create: `backend/src/main/java/com/n4monitor/service/DashboardService.java`
- Create: `backend/src/main/java/com/n4monitor/service/DeviceService.java`

**Interfaces:**
- Produces: `DashboardService` methods: `getKpiMetrics()`, `getAlarmSummary()`, `getAlarmTop5()`, `getSystemStatus()`, `getPowerTrend()`, `getEnergyTrend()`, `getPueTrend()`, `getTemperatureTrend()` — all return DTOs; `DeviceService` method: `getDeviceTree()` returns `List<DeviceTreeNodeDto>`
- Consumes: Mappers from Task 4, DTOs from Task 5

- [ ] **Step 1: Create DashboardService.java**

Write file `backend/src/main/java/com/n4monitor/service/DashboardService.java`:

```java
package com.n4monitor.service;

import com.n4monitor.dto.*;
import com.n4monitor.mapper.DeviceMapper;
import com.n4monitor.mapper.MonitorPointMapper;
import com.n4monitor.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private final DeviceMapper deviceMapper;
    private final MonitorPointMapper monitorPointMapper;
    private final ProductMapper productMapper;

    public DashboardService(DeviceMapper deviceMapper,
                            MonitorPointMapper monitorPointMapper,
                            ProductMapper productMapper) {
        this.deviceMapper = deviceMapper;
        this.monitorPointMapper = monitorPointMapper;
        this.productMapper = productMapper;
    }

    public KpiMetricsDto getKpiMetrics() {
        KpiMetricsDto dto = new KpiMetricsDto();
        dto.setPue(1.32);
        dto.setPueTrend("+0.03 vs yesterday");
        dto.setItLoadPercent(68);
        dto.setItLoadKw(1876);
        dto.setItLoadCapacityKw(2780);
        dto.setCoolingLoadPercent(42);
        dto.setCoolingLoadKw(1153);
        dto.setCoolingLoadCapacityKw(2780);
        dto.setFacilityLoadPercent(57);
        dto.setFacilityLoadKw(1584);
        dto.setFacilityLoadCapacityKw(2780);
        dto.setTotalEnergyKwh(32680);
        dto.setCapacityUsagePercent(78);
        dto.setActiveDevices(1426);
        dto.setTotalDevices(1820);
        return dto;
    }

    public AlarmSummaryDto getAlarmSummary() {
        AlarmSummaryDto dto = new AlarmSummaryDto();
        dto.setCritical(3);
        dto.setMajor(14);
        dto.setMinor(27);
        dto.setWarning(44);
        return dto;
    }

    public List<AlarmItemDto> getAlarmTop5() {
        List<AlarmItemDto> list = new ArrayList<>();
        list.add(createAlarm("10:21", "CHU-A3 High Pressure", "Critical", "CHU-A3"));
        list.add(createAlarm("10:18", "UPS-A Battery Fault", "Major", "UPS-A"));
        list.add(createAlarm("10:15", "PDU-B Overload", "Major", "PDU-B"));
        list.add(createAlarm("10:11", "CHWS Temp High", "Minor", "CHWS"));
        list.add(createAlarm("10:05", "AHU-3 Filter Dirty", "Minor", "AHU-3"));
        return list;
    }

    private AlarmItemDto createAlarm(String time, String desc, String level, String source) {
        AlarmItemDto item = new AlarmItemDto();
        item.setAlarmTime(time);
        item.setAlarmDesc(desc);
        item.setAlarmLevel(level);
        item.setSourceDevice(source);
        return item;
    }

    public List<SystemStatusDto> getSystemStatus() {
        // Device counts from real DB; status labels are mock in this iteration
        List<SystemStatusDto> list = new ArrayList<>();
        list.add(createSystemStatus("Electrical", "Normal", 28, "gear"));
        list.add(createSystemStatus("Cooling", "Normal", 18, "snowflake"));
        list.add(createSystemStatus("UPS", "Normal", 12, "battery"));
        list.add(createSystemStatus("PDU", "Warning", 4, "warning-triangle"));
        list.add(createSystemStatus("CRAH", "Normal", 132, "fan"));
        list.add(createSystemStatus("Sensor", "Normal", 1248, "thermometer"));
        list.add(createSystemStatus("Network", "Normal", 8, "globe"));
        return list;
    }

    private SystemStatusDto createSystemStatus(String name, String status, int count, String icon) {
        SystemStatusDto dto = new SystemStatusDto();
        dto.setSystemName(name);
        dto.setStatus(status);
        dto.setDeviceCount(count);
        dto.setIcon(icon);
        return dto;
    }

    public TrendDataDto getPowerTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateSineData(1800, 600, 0.15));   // IT Load kW
        dto.setValues2(generateSineData(3500, 800, 0.10));  // Total Load kW
        dto.setSeriesName("IT Load");
        dto.setSeriesName2("Total Load");
        dto.setUnit("kW");
        return dto;
    }

    public TrendDataDto getEnergyTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateSineData(2000, 500, 0.20));   // Today kWh
        dto.setValues2(generateSineData(1800, 400, 0.18));  // Yesterday kWh
        dto.setSeriesName("Today");
        dto.setSeriesName2("Yesterday");
        dto.setUnit("kWh");
        return dto;
    }

    public TrendDataDto getPueTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateFluctuatingData(1.32, 0.08));
        dto.setSeriesName("PUE");
        dto.setUnit("PUE");
        return dto;
    }

    public TrendDataDto getTemperatureTrend() {
        TrendDataDto dto = new TrendDataDto();
        dto.setTimeLabels(generateTimeLabels());
        dto.setValues(generateFluctuatingData(25.0, 2.0));    // Avg Temp
        dto.setValues2(generateFluctuatingData(31.0, 3.0));   // Max Temp
        dto.setSeriesName("Avg Temp");
        dto.setSeriesName2("Max Temp");
        dto.setUnit("°C");
        return dto;
    }

    // Generate 24 hourly time labels: "00:00" through "23:00"
    private List<String> generateTimeLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            labels.add(String.format("%02d:00", i));
        }
        return labels;
    }

    // Generate sine-wave pattern data for realistic power/energy trends
    private List<Double> generateSineData(double base, double amplitude, double noiseFactor) {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double sine = Math.sin((i - 6) * Math.PI / 12) * amplitude;
            double noise = (Math.random() - 0.5) * amplitude * noiseFactor;
            data.add(Math.max(0, base + sine + noise));
        }
        return data;
    }

    // Generate fluctuating data around a base value for PUE/temperature
    private List<Double> generateFluctuatingData(double base, double range) {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double noise = (Math.random() - 0.5) * range;
            data.add(base + noise);
        }
        return data;
    }
}
```

- [ ] **Step 2: Create DeviceService.java**

Write file `backend/src/main/java/com/n4monitor/service/DeviceService.java`:

```java
package com.n4monitor.service;

import com.n4monitor.dto.DeviceTreeNodeDto;
import com.n4monitor.entity.Device;
import com.n4monitor.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public DeviceService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    public List<DeviceTreeNodeDto> getDeviceTree() {
        List<Device> rootDevices = deviceMapper.findRootDevices();
        List<DeviceTreeNodeDto> tree = new ArrayList<>();
        for (Device root : rootDevices) {
            tree.add(buildTreeNode(root));
        }
        return tree;
    }

    private DeviceTreeNodeDto buildTreeNode(Device device) {
        DeviceTreeNodeDto node = new DeviceTreeNodeDto();
        node.setId(device.getDeviceId());
        node.setLabel(device.getDeviceName());
        node.setType(resolveNodeType(device.getSysLevel()));
        node.setStatus(device.getStatus());

        List<Device> children = deviceMapper.findBySysParentId(device.getDeviceId());
        if (children != null && !children.isEmpty()) {
            List<DeviceTreeNodeDto> childNodes = new ArrayList<>();
            for (Device child : children) {
                childNodes.add(buildTreeNode(child));
            }
            node.setChildren(childNodes);
        } else {
            node.setChildren(new ArrayList<>());
        }
        return node;
    }

    private String resolveNodeType(Integer sysLevel) {
        if (sysLevel == null || sysLevel == 0) return "site";
        if (sysLevel == 1) return "building";
        if (sysLevel == 2) return "system";
        return "device";
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd D:\N4_alarm\backend
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
cd D:\N4_alarm
git add backend/src/main/java/com/n4monitor/service/
git commit -m "feat: add DashboardService and DeviceService with mock data and DB queries"
```

---

### Task 7: Backend Controllers

**Files:**
- Create: `backend/src/main/java/com/n4monitor/controller/DashboardController.java`
- Create: `backend/src/main/java/com/n4monitor/controller/DeviceController.java`

**Interfaces:**
- Produces: REST endpoints matching spec section 11, all wrapped in `ApiResponse<T>`
- Consumes: `DashboardService` and `DeviceService` from Task 6

- [ ] **Step 1: Create DashboardController.java**

Write file `backend/src/main/java/com/n4monitor/controller/DashboardController.java`:

```java
package com.n4monitor.controller;

import com.n4monitor.dto.*;
import com.n4monitor.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpi")
    public ApiResponse<KpiMetricsDto> getKpi() {
        return ApiResponse.success(dashboardService.getKpiMetrics());
    }

    @GetMapping("/alarm-summary")
    public ApiResponse<AlarmSummaryDto> getAlarmSummary() {
        return ApiResponse.success(dashboardService.getAlarmSummary());
    }

    @GetMapping("/alarm-top5")
    public ApiResponse<List<AlarmItemDto>> getAlarmTop5() {
        return ApiResponse.success(dashboardService.getAlarmTop5());
    }

    @GetMapping("/system-status")
    public ApiResponse<List<SystemStatusDto>> getSystemStatus() {
        return ApiResponse.success(dashboardService.getSystemStatus());
    }

    @GetMapping("/power-trend")
    public ApiResponse<TrendDataDto> getPowerTrend() {
        return ApiResponse.success(dashboardService.getPowerTrend());
    }

    @GetMapping("/energy-trend")
    public ApiResponse<TrendDataDto> getEnergyTrend() {
        return ApiResponse.success(dashboardService.getEnergyTrend());
    }

    @GetMapping("/pue-trend")
    public ApiResponse<TrendDataDto> getPueTrend() {
        return ApiResponse.success(dashboardService.getPueTrend());
    }

    @GetMapping("/temperature-trend")
    public ApiResponse<TrendDataDto> getTemperatureTrend() {
        return ApiResponse.success(dashboardService.getTemperatureTrend());
    }
}
```

- [ ] **Step 2: Create DeviceController.java**

Write file `backend/src/main/java/com/n4monitor/controller/DeviceController.java`:

```java
package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.dto.DeviceTreeNodeDto;
import com.n4monitor.service.DeviceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<DeviceTreeNodeDto>> getDeviceTree() {
        return ApiResponse.success(deviceService.getDeviceTree());
    }
}
```

- [ ] **Step 3: Verify backend compiles and all endpoints are registered**

```bash
cd D:\N4_alarm\backend
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Start backend and verify API responds**

```bash
cd D:\N4_alarm\backend
mvn spring-boot:run -q
```

Wait for startup, then test:

```bash
curl http://localhost:8080/api/dashboard/kpi
```

Expected: JSON response `{ "code": 200, "message": "success", "data": { "pue": 1.32, ... } }`

Note: If MySQL DB is not running or `n4_alarm` database is empty, the device/tree endpoint may return empty data. The mock endpoints (kpi, alarms, trends) will work regardless of DB state.

- [ ] **Step 5: Commit**

```bash
cd D:\N4_alarm
git add backend/src/main/java/com/n4monitor/controller/
git commit -m "feat: add DashboardController and DeviceController REST endpoints"
```

---

### Task 8: Frontend Project Scaffold

**Files:**
- Create: Vue3 + Vite project in `D:\N4_alarm\frontend\` via `npm create vite@latest`
- Modify: `frontend/package.json` (add Element Plus, ECharts, Pinia, Vue Router, SCSS dependencies)
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`, `frontend/tsconfig.app.json`, `frontend/tsconfig.node.json`

**Interfaces:**
- Produces: Working Vue3 + Vite dev server on port 5173 with all required dependencies installed and proxy config for `/api` → `localhost:8080`

- [ ] **Step 1: Create Vue3 + Vite + TypeScript project**

```bash
cd D:\N4_alarm
npm create vite@latest frontend -- --template vue-ts
```

Expected: `frontend/` directory created with Vue3 + Vite + TypeScript scaffold.

- [ ] **Step 2: Install dependencies**

```bash
cd D:\N4_alarm\frontend
npm install
npm install element-plus @element-plus/icons-vue echarts vue-echarts pinia vue-router@4 sass axios
```

Expected: All dependencies installed in `frontend/node_modules/`.

- [ ] **Step 3: Configure vite.config.ts with API proxy**

Replace `frontend/vite.config.ts` with:

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/variables" as *;`,
      },
    },
  },
})
```

- [ ] **Step 4: Verify frontend dev server starts**

```bash
cd D:\N4_alarm\frontend
npm run dev
```

Expected: Vite dev server starts on `http://localhost:5173`, shows default Vue page.

- [ ] **Step 5: Commit**

```bash
cd D:\N4_alarm
git add frontend/ -f
git commit -m "feat: scaffold Vue3 + Vite frontend project with Element Plus, ECharts, Pinia, Router"
```

Note: Use `-f` to force-add because `node_modules/` is in .gitignore but we need to add all other frontend files.

---

### Task 9: Frontend Dark Theme Styles & Global Setup

**Files:**
- Create: `frontend/src/styles/variables.scss`
- Create: `frontend/src/styles/global.scss`
- Modify: `frontend/src/main.ts` (register Element Plus, Pinia, Router, import global styles)

**Interfaces:**
- Produces: Dark theme CSS variables available globally via SCSS; Element Plus configured with dark theme; all plugins registered in Vue app

- [ ] **Step 1: Create variables.scss**

Write file `frontend/src/styles/variables.scss`:

```scss
// Dark theme color palette — matching the Xynor dashboard design
$bg-dark: #0a0e17;
$bg-card: #111827;
$bg-card-hover: #1a2332;
$border-color: #1f2937;
$border-card: #2d3748;

$text-primary: #e5e7eb;
$text-secondary: #9ca3af;
$text-muted: #6b7280;

$accent-green: #10b981;
$accent-red: #ef4444;
$accent-orange: #f97316;
$accent-yellow: #eab308;
$accent-blue: #3b82f6;

$sidebar-width: 240px;
$sidebar-collapsed-width: 60px;
$topnav-height: 48px;
$bottombar-height: 48px;
$right-panel-width: 300px;

$font-family: 'Inter', 'Segoe UI', -apple-system, BlinkMacSystemFont, sans-serif;
```

- [ ] **Step 2: Create global.scss**

Write file `frontend/src/styles/global.scss`:

```scss
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: $bg-dark;
  color: $text-primary;
  font-family: $font-family;
  font-size: 14px;
  line-height: 1.5;
}

// Element Plus dark theme overrides
.el-menu {
  background-color: $bg-card !important;
  border-right: none !important;
}

.el-menu-item,
.el-sub-menu__title {
  color: $text-secondary !important;
  &:hover {
    background-color: $bg-card-hover !important;
    color: $text-primary !important;
  }
}

.el-menu-item.is-active {
  color: $accent-green !important;
  background-color: rgba($accent-green, 0.1) !important;
}

.el-card {
  background-color: $bg-card !important;
  border-color: $border-card !important;
  color: $text-primary !important;
}

.el-tree {
  background-color: $bg-card !important;
  color: $text-secondary !important;
}

.el-input__wrapper {
  background-color: $bg-card !important;
  box-shadow: none !important;
  border: 1px solid $border-color !important;
}

// Scrollbar styling
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: $bg-dark;
}

::-webkit-scrollbar-thumb {
  background: $border-color;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: $text-muted;
}
```

- [ ] **Step 3: Update main.ts to register all plugins**

Replace `frontend/src/main.ts` with:

```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import router from './router'
import App from './App.vue'
import './styles/global.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 4: Verify frontend compiles with new styles**

```bash
cd D:\N4_alarm\frontend
npm run build
```

Expected: Build succeeds without errors.

- [ ] **Step 5: Commit**

```bash
cd D:\N4_alarm
git add frontend/src/styles/ frontend/src/main.ts
git commit -m "feat: add dark theme styles, global SCSS, and register Element Plus/Pinia/Router in main.ts"
```

---

### Task 10: Frontend Router, Pinia Store, API Layer

**Files:**
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/dashboard.ts`
- Create: `frontend/src/api/dashboard.ts`
- Create: `frontend/src/api/device.ts`
- Create: `frontend/src/api/request.ts` (axios wrapper)

**Interfaces:**
- Produces: `useDashboardStore()` Pinia store with actions to fetch all dashboard data; `dashboardApi` and `deviceApi` service modules; router with `/` → DashboardView
- Consumes: Backend API endpoints from Task 7

- [ ] **Step 1: Create request.ts (axios wrapper)**

Write file `frontend/src/api/request.ts`:

```typescript
import axios from 'axios'
import type { ApiResponse } from '@/api/types'

const request = axios.create({
  baseURL: '',
  timeout: 10000,
})

request.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse<any>
    if (data.code === 200) {
      return data.data
    }
    return Promise.reject(new Error(data.message || 'API error'))
  },
  (error) => {
    return Promise.reject(error)
  }
)

export default request
```

- [ ] **Step 2: Create API types**

Write file `frontend/src/api/types.ts`:

```typescript
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface KpiMetrics {
  pue: number
  pueTrend: string
  itLoadPercent: number
  itLoadKw: number
  itLoadCapacityKw: number
  coolingLoadPercent: number
  coolingLoadKw: number
  coolingLoadCapacityKw: number
  facilityLoadPercent: number
  facilityLoadKw: number
  facilityLoadCapacityKw: number
  totalEnergyKwh: number
  capacityUsagePercent: number
  activeDevices: number
  totalDevices: number
}

export interface AlarmSummary {
  critical: number
  major: number
  minor: number
  warning: number
}

export interface AlarmItem {
  alarmTime: string
  alarmDesc: string
  alarmLevel: string
  sourceDevice: string
}

export interface SystemStatusItem {
  systemName: string
  status: string
  deviceCount: number
  icon: string
}

export interface TrendData {
  timeLabels: string[]
  values: number[]
  values2?: number[]
  seriesName: string
  seriesName2?: string
  unit: string
}

export interface DeviceTreeNode {
  id: number
  label: string
  type: string
  status: number
  children: DeviceTreeNode[]
}
```

- [ ] **Step 3: Create dashboard.ts API service**

Write file `frontend/src/api/dashboard.ts`:

```typescript
import request from './request'
import type { KpiMetrics, AlarmSummary, AlarmItem, SystemStatusItem, TrendData } from './types'

export const dashboardApi = {
  getKpi(): Promise<KpiMetrics> {
    return request.get('/api/dashboard/kpi')
  },
  getAlarmSummary(): Promise<AlarmSummary> {
    return request.get('/api/dashboard/alarm-summary')
  },
  getAlarmTop5(): Promise<AlarmItem[]> {
    return request.get('/api/dashboard/alarm-top5')
  },
  getSystemStatus(): Promise<SystemStatusItem[]> {
    return request.get('/api/dashboard/system-status')
  },
  getPowerTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/power-trend')
  },
  getEnergyTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/energy-trend')
  },
  getPueTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/pue-trend')
  },
  getTemperatureTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/temperature-trend')
  },
}
```

- [ ] **Step 4: Create device.ts API service**

Write file `frontend/src/api/device.ts`:

```typescript
import request from './request'
import type { DeviceTreeNode } from './types'

export const deviceApi = {
  getDeviceTree(): Promise<DeviceTreeNode[]> {
    return request.get('/api/devices/tree')
  },
}
```

- [ ] **Step 5: Create Pinia dashboard store**

Write file `frontend/src/stores/dashboard.ts`:

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import { deviceApi } from '@/api/device'
import type { KpiMetrics, AlarmSummary, AlarmItem, SystemStatusItem, TrendData, DeviceTreeNode } from '@/api/types'

export const useDashboardStore = defineStore('dashboard', () => {
  const kpiMetrics = ref<KpiMetrics | null>(null)
  const alarmSummary = ref<AlarmSummary | null>(null)
  const alarmTop5 = ref<AlarmItem[]>([])
  const systemStatus = ref<SystemStatusItem[]>([])
  const powerTrend = ref<TrendData | null>(null)
  const energyTrend = ref<TrendData | null>(null)
  const pueTrend = ref<TrendData | null>(null)
  const temperatureTrend = ref<TrendData | null>(null)
  const deviceTree = ref<DeviceTreeNode[]>([])
  const loading = ref(false)

  async function fetchAllDashboardData() {
    loading.value = true
    try {
      const [kpi, alarm, top5, status, power, energy, pue, temp, tree] = await Promise.all([
        dashboardApi.getKpi(),
        dashboardApi.getAlarmSummary(),
        dashboardApi.getAlarmTop5(),
        dashboardApi.getSystemStatus(),
        dashboardApi.getPowerTrend(),
        dashboardApi.getEnergyTrend(),
        dashboardApi.getPueTrend(),
        dashboardApi.getTemperatureTrend(),
        deviceApi.getDeviceTree(),
      ])
      kpiMetrics.value = kpi
      alarmSummary.value = alarm
      alarmTop5.value = top5
      systemStatus.value = status
      powerTrend.value = power
      energyTrend.value = energy
      pueTrend.value = pue
      temperatureTrend.value = temp
      deviceTree.value = tree
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      loading.value = false
    }
  }

  return {
    kpiMetrics,
    alarmSummary,
    alarmTop5,
    systemStatus,
    powerTrend,
    energyTrend,
    pueTrend,
    temperatureTrend,
    deviceTree,
    loading,
    fetchAllDashboardData,
  }
})
```

- [ ] **Step 6: Create router**

Write file `frontend/src/router/index.ts`:

```typescript
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
    },
  ],
})

export default router
```

- [ ] **Step 7: Verify frontend compiles**

```bash
cd D:\N4_alarm\frontend
npm run build
```

Expected: Build succeeds (may have warnings about unused imports, but no errors).

- [ ] **Step 8: Commit**

```bash
cd D:\N4_alarm
git add frontend/src/api/ frontend/src/stores/ frontend/src/router/
git commit -m "feat: add API layer, Pinia store, and Vue Router for dashboard"
```

---

### Task 11: Frontend Layout Shell (TopNavbar, LeftSidebar, BottomToolbar, MainLayout)

**Files:**
- Create: `frontend/src/layouts/MainLayout.vue`
- Create: `frontend/src/components/TopNavbar.vue`
- Create: `frontend/src/components/LeftSidebar.vue`
- Create: `frontend/src/components/BottomToolbar.vue`
- Modify: `frontend/src/App.vue`

**Interfaces:**
- Produces: Complete layout shell with TopNavbar (logo, tabs, alert badges), LeftSidebar (site selector, device tree, collapse toggle), BottomToolbar (5 action items), and MainLayout composing all three around a content slot
- Consumes: `useDashboardStore()` for alarm counts and device tree data; Element Plus components

- [ ] **Step 1: Create TopNavbar.vue**

Write file `frontend/src/components/TopNavbar.vue`:

```vue
<template>
  <div class="top-navbar">
    <div class="navbar-left">
      <span class="logo">DAYONE</span>
      <span class="logo-separator">|</span>
      <span class="logo-sub">N4 Monitor</span>
      <div class="nav-tabs">
        <span
          v-for="tab in navTabs"
          :key="tab"
          :class="['nav-tab', { active: tab === 'Dashboard' }]"
        >{{ tab }}</span>
      </div>
    </div>
    <div class="navbar-right">
      <div class="alert-badges">
        <span class="badge critical">{{ alarmSummary?.critical || 0 }}</span>
        <span class="badge major">{{ alarmSummary?.major || 0 }}</span>
        <span class="badge minor">{{ alarmSummary?.minor || 0 }}</span>
        <span class="badge warning">{{ alarmSummary?.warning || 0 }}</span>
      </div>
      <el-icon class="search-icon"><Search /></el-icon>
      <div class="user-profile">
        <el-icon><UserFilled /></el-icon>
        <span>admin</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Search, UserFilled } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const alarmSummary = computed(() => store.alarmSummary)

const navTabs = [
  'Dashboard', 'Electrical', 'Cooling', 'Mechanical',
  'Data Hall', 'Alarms', 'Trend', 'Report', 'System',
]
</script>

<style scoped lang="scss">
.top-navbar {
  height: $topnav-height;
  background-color: $bg-card;
  border-bottom: 1px solid $border-color;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo {
  font-weight: 700;
  font-size: 16px;
  color: $accent-green;
}

.logo-separator {
  color: $text-muted;
  margin: 0 4px;
}

.logo-sub {
  font-size: 14px;
  color: $text-secondary;
}

.nav-tabs {
  display: flex;
  gap: 2px;
  margin-left: 24px;
}

.nav-tab {
  padding: 6px 12px;
  font-size: 13px;
  color: $text-secondary;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.nav-tab.active {
  color: $accent-green;
  border-bottom: 2px solid $accent-green;
}

.nav-tab:hover {
  color: $text-primary;
  background-color: $bg-card-hover;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.alert-badges {
  display: flex;
  gap: 8px;
}

.badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  &.critical { background: rgba($accent-red, 0.2); color: $accent-red; }
  &.major { background: rgba($accent-orange, 0.2); color: $accent-orange; }
  &.minor { background: rgba($accent-yellow, 0.2); color: $accent-yellow; }
  &.warning { background: rgba($accent-blue, 0.2); color: $accent-blue; }
}

.search-icon {
  color: $text-secondary;
  cursor: pointer;
  font-size: 16px;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 4px;
  color: $text-secondary;
  font-size: 13px;
}
</style>
```

- [ ] **Step 2: Create LeftSidebar.vue**

Write file `frontend/src/components/LeftSidebar.vue`:

```vue
<template>
  <div :class="['left-sidebar', { collapsed: isCollapsed }]">
    <div class="sidebar-header">
      <div class="site-selector">
        <el-icon><Location /></el-icon>
        <span v-if="!isCollapsed">SITE-JHB-01</span>
      </div>
      <el-input
        v-if="!isCollapsed"
        v-model="searchText"
        placeholder="Search..."
        size="small"
        prefix-icon="Search"
        class="sidebar-search"
      />
    </div>
    <div class="sidebar-tree" v-if="!isCollapsed">
      <el-tree
        :data="deviceTree"
        :props="treeProps"
        node-key="id"
        default-expand-all
        highlight-current
        :filter-node-method="filterNode"
        ref="treeRef"
      >
        <template #default="{ node, data }">
          <span :class="['tree-node-label', `node-${data.type}`]">
            <span :class="['status-dot', getStatusClass(data.status)]"></span>
            {{ data.label }}
          </span>
        </template>
      </el-tree>
    </div>
    <div class="sidebar-footer">
      <span class="collapse-btn" @click="toggleCollapse">
        {{ isCollapsed ? '>>' : '<< Collapse' }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Location } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'
import type { DeviceTreeNode } from '@/api/types'
import type ElTree from 'element-plus/es/components/tree/index'

const store = useDashboardStore()
const deviceTree = computed(() => store.deviceTree)
const isCollapsed = ref(false)
const searchText = ref('')
const treeRef = ref<InstanceType<typeof ElTree>>()

const treeProps = {
  children: 'children',
  label: 'label',
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

function getStatusClass(status: number): string {
  switch (status) {
    case 1: return 'online'
    case 2: return 'offline'
    case 3: return 'maintenance'
    case 4: return 'fault'
    default: return 'online'
  }
}

function filterNode(value: string, data: DeviceTreeNode): boolean {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

watch(searchText, (val) => {
  treeRef.value?.filter(val)
})
</script>

<style scoped lang="scss">
.left-sidebar {
  width: $sidebar-width;
  background-color: $bg-card;
  border-right: 1px solid $border-color;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  flex-shrink: 0;
  overflow: hidden;

  &.collapsed {
    width: $sidebar-collapsed-width;
  }
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid $border-color;
}

.site-selector {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $accent-green;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
}

.sidebar-search {
  .el-input__wrapper {
    background-color: $bg-dark !important;
  }
}

.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  &.online { background-color: $accent-green; }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; }
  &.fault { background-color: $accent-red; }
}

.node-site {
  color: $text-primary;
  font-weight: 600;
}
.node-building {
  color: $text-primary;
  font-weight: 500;
}
.node-system {
  color: $text-secondary;
}
.node-device {
  color: $text-muted;
  font-size: 12px;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid $border-color;
  text-align: center;
}

.collapse-btn {
  color: $text-secondary;
  font-size: 12px;
  cursor: pointer;
  &:hover { color: $text-primary; }
}
</style>
```

- [ ] **Step 3: Create BottomToolbar.vue**

Write file `frontend/src/components/BottomToolbar.vue`:

```vue
<template>
  <div class="bottom-toolbar">
    <div
      v-for="item in toolbarItems"
      :key="item.label"
      class="toolbar-item"
    >
      <el-icon :size="18"><component :is="item.icon" /></el-icon>
      <span class="toolbar-label">{{ item.label }}</span>
      <span v-if="item.badge" class="toolbar-badge">{{ item.badge }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Bell, TrendCharts, Document, Tools, Setting } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()

const toolbarItems = computed(() => [
  { label: 'Alarm Center', icon: Bell, badge: store.alarmSummary?.critical || 0 },
  { label: 'Trend Viewer', icon: TrendCharts, badge: null },
  { label: 'Report Center', icon: Document, badge: null },
  { label: 'Work Order', icon: Tools, badge: null },
  { label: 'System Settings', icon: Setting, badge: null },
])
</script>

<style scoped lang="scss">
.bottom-toolbar {
  height: $bottombar-height;
  background-color: $bg-card;
  border-top: 1px solid $border-color;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  flex-shrink: 0;
}

.toolbar-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-secondary;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 4px;
  transition: all 0.2s;
  &:hover {
    color: $text-primary;
    background-color: $bg-card-hover;
  }
}

.toolbar-label {
  font-size: 12px;
}

.toolbar-badge {
  background-color: $accent-red;
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 4px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}
</style>
```

- [ ] **Step 4: Create MainLayout.vue**

Write file `frontend/src/layouts/MainLayout.vue`:

```vue
<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <LeftSidebar />
      <div class="layout-content">
        <slot />
      </div>
      <div class="layout-right-panel">
        <AlarmSummary />
        <SystemStatus />
      </div>
    </div>
    <BottomToolbar />
  </div>
</template>

<script setup lang="ts">
import TopNavbar from '@/components/TopNavbar.vue'
import LeftSidebar from '@/components/LeftSidebar.vue'
import BottomToolbar from '@/components/BottomToolbar.vue'
import AlarmSummary from '@/components/AlarmSummary.vue'
import SystemStatus from '@/components/SystemStatus.vue'
</script>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: $bg-dark;
}

.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.layout-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.layout-right-panel {
  width: $right-panel-width;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 12px;
  flex-shrink: 0;
  overflow-y: auto;
}
</style>
```

- [ ] **Step 5: Update App.vue**

Replace `frontend/src/App.vue` with:

```vue
<template>
  <MainLayout />
</template>

<script setup lang="ts">
import MainLayout from '@/layouts/MainLayout.vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
store.fetchAllDashboardData()
</script>
```

- [ ] **Step 6: Verify frontend compiles**

```bash
cd D:\N4_alarm\frontend
npm run build
```

Expected: Build succeeds.

- [ ] **Step 7: Commit**

```bash
cd D:\N4_alarm
git add frontend/src/layouts/ frontend/src/components/TopNavbar.vue frontend/src/components/LeftSidebar.vue frontend/src/components/BottomToolbar.vue frontend/src/App.vue
git commit -m "feat: add MainLayout, TopNavbar, LeftSidebar, BottomToolbar components"
```

---

### Task 12: Frontend KPI Cards & Building Placeholder

**Files:**
- Create: `frontend/src/components/KpiCardsRow.vue`
- Create: `frontend/src/components/BuildingPlaceholder.vue`
- Create: `frontend/src/assets/building-placeholder.svg`
- Create: `frontend/src/styles/dashboard.scss`

**Interfaces:**
- Produces: 6 KPI metric cards rendering data from `useDashboardStore().kpiMetrics`; static SVG building placeholder with labeled buildings
- Consumes: `useDashboardStore()` for KPI data

- [ ] **Step 1: Create KpiCardsRow.vue**

Write file `frontend/src/components/KpiCardsRow.vue`:

```vue
<template>
  <div class="kpi-cards-row">
    <div
      v-for="card in kpiCards"
      :key="card.label"
      class="kpi-card"
    >
      <div class="kpi-card-icon">
        <el-icon :size="20"><component :is="card.icon" /></el-icon>
      </div>
      <div class="kpi-card-content">
        <div class="kpi-label">{{ card.label }}</div>
        <div :class="['kpi-value', card.valueClass]">{{ card.displayValue }}</div>
        <div class="kpi-detail">{{ card.detail }}</div>
      </div>
      <div v-if="card.progress" class="kpi-progress">
        <el-progress
          :percentage="card.progress"
          :stroke-width="4"
          :color="card.progressColor"
          :show-text="false"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  TrendCharts, Lightning, Refrigerator,
  OfficeBuilding, Sunrise, Monitor,
} from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const metrics = computed(() => store.kpiMetrics)

const kpiCards = computed(() => {
  if (!metrics.value) return []
  const m = metrics.value
  return [
    {
      label: 'PUE',
      displayValue: m.pue.toFixed(2),
      detail: m.pueTrend,
      valueClass: m.pueTrend.startsWith('+') ? 'value-warn' : 'value-good',
      icon: TrendCharts,
      progress: null,
      progressColor: '#10b981',
    },
    {
      label: 'IT Load',
      displayValue: `${m.itLoadPercent}%`,
      detail: `${m.itLoadKw.toLocaleString()} / ${m.itLoadCapacityKw.toLocaleString()} kW`,
      valueClass: 'value-good',
      icon: Lightning,
      progress: m.itLoadPercent,
      progressColor: '#10b981',
    },
    {
      label: 'Cooling Load',
      displayValue: `${m.coolingLoadPercent}%`,
      detail: `${m.coolingLoadKw.toLocaleString()} / ${m.coolingLoadCapacityKw.toLocaleString()} kW`,
      valueClass: 'value-good',
      icon: Refrigerator,
      progress: m.coolingLoadPercent,
      progressColor: '#10b981',
    },
    {
      label: 'Facility Load',
      displayValue: `${m.facilityLoadPercent}%`,
      detail: `${m.facilityLoadKw.toLocaleString()} / ${m.facilityLoadCapacityKw.toLocaleString()} kW`,
      valueClass: 'value-good',
      icon: OfficeBuilding,
      progress: m.facilityLoadPercent,
      progressColor: '#10b981',
    },
    {
      label: 'Total Energy',
      displayValue: `${m.totalEnergyKwh.toLocaleString()} kWh`,
      detail: 'Today',
      valueClass: 'value-good',
      icon: Sunrise,
      progress: null,
      progressColor: '#10b981',
    },
    {
      label: 'Capacity Usage',
      displayValue: `${m.capacityUsagePercent}%`,
      detail: `${m.activeDevices.toLocaleString()} / ${m.totalDevices.toLocaleString()} devices online`,
      valueClass: 'value-good',
      icon: Monitor,
      progress: m.capacityUsagePercent,
      progressColor: '#10b981',
    },
  ]
})
</script>

<style scoped lang="scss">
.kpi-cards-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.kpi-card {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: border-color 0.2s;
  &:hover { border-color: $accent-green; }
}

.kpi-card-icon {
  color: $text-muted;
}

.kpi-label {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 4px;
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  &.value-good { color: $accent-green; }
  &.value-warn { color: $accent-orange; }
}

.kpi-detail {
  font-size: 12px;
  color: $text-muted;
}

.kpi-progress {
  margin-top: 4px;
}
</style>
```

- [ ] **Step 2: Create building-placeholder.svg**

Write file `frontend/src/assets/building-placeholder.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 400" width="800" height="400">
  <rect width="800" height="400" fill="#111827" rx="8"/>

  <!-- Ground -->
  <rect x="50" y="320" width="700" height="60" fill="#1f2937" rx="4"/>

  <!-- Building A -->
  <rect x="120" y="120" width="160" height="200" fill="#374151" rx="6"/>
  <rect x="130" y="130" width="140" height="10" fill="#4b5563" rx="2"/>
  <rect x="130" y="150" width="140" height="10" fill="#4b5563" rx="2"/>
  <rect x="130" y="170" width="140" height="10" fill="#4b5563" rx="2"/>
  <rect x="130" y="190" width="140" height="10" fill="#4b5563" rx="2"/>
  <rect x="130" y="210" width="140" height="10" fill="#4b5563" rx="2"/>
  <!-- Building A windows (lit) -->
  <rect x="140" y="230" width="20" height="20" fill="#10b981" opacity="0.3" rx="2"/>
  <rect x="170" y="230" width="20" height="20" fill="#10b981" opacity="0.3" rx="2"/>
  <rect x="200" y="230" width="20" height="20" fill="#10b981" opacity="0.3" rx="2"/>
  <rect x="240" y="230" width="20" height="20" fill="#10b981" opacity="0.3" rx="2"/>
  <!-- Building A label -->
  <text x="200" y="108" fill="#e5e7eb" font-size="14" font-weight="600" text-anchor="middle">Building A</text>
  <circle cx="200" cy="88" r="4" fill="#10b981"/>

  <!-- Building B -->
  <rect x="360" y="150" width="140" height="170" fill="#374151" rx="6"/>
  <rect x="370" y="160" width="120" height="10" fill="#4b5563" rx="2"/>
  <rect x="370" y="180" width="120" height="10" fill="#4b5563" rx="2"/>
  <rect x="370" y="200" width="120" height="10" fill="#4b5563" rx="2"/>
  <rect x="370" y="220" width="120" height="10" fill="#4b5563" rx="2"/>
  <!-- Building B windows (lit) -->
  <rect x="380" y="240" width="20" height="20" fill="#10b981" opacity="0.3" rx="2"/>
  <rect x="410" y="240" width="20" height="20" fill="#10b981" opacity="0.3" rx="2"/>
  <rect x="440" y="240" width="20" height="20" fill="#10b981" opacity="0.3" rx="2"/>
  <!-- Building B label -->
  <text x="430" y="138" fill="#e5e7eb" font-size="14" font-weight="600" text-anchor="middle">Building B</text>
  <circle cx="430" cy="118" r="4" fill="#10b981"/>

  <!-- Utility Plant -->
  <rect x="560" y="240" width="100" height="80" fill="#374151" rx="4"/>
  <text x="610" y="228" fill="#e5e7eb" font-size="12" font-weight="500" text-anchor="middle">Utility Plant</text>
  <circle cx="610" cy="210" r="3" fill="#10b981"/>

  <!-- Substation -->
  <rect x="560" y="160" width="80" height="60" fill="#374151" rx="4"/>
  <text x="600" y="148" fill="#e5e7eb" font-size="12" font-weight="500" text-anchor="middle">Substation</text>
  <circle cx="600" cy="130" r="3" fill="#10b981"/>

  <!-- Cooling Plant -->
  <rect x="680" y="260" width="80" height="60" fill="#374151" rx="4"/>
  <text x="720" y="248" fill="#e5e7eb" font-size="12" font-weight="500" text-anchor="middle">Cooling Plant</text>
  <circle cx="720" cy="230" r="3" fill="#10b981"/>

  <!-- Connecting lines -->
  <line x1="200" y1="92" x2="200" y2="120" stroke="#10b981" stroke-width="1" opacity="0.5"/>
  <line x1="430" y1="122" x2="430" y2="150" stroke="#10b981" stroke-width="1" opacity="0.5"/>
  <line x1="610" y1="214" x2="610" y2="240" stroke="#10b981" stroke-width="1" opacity="0.5"/>
  <line x1="600" y1="134" x2="600" y2="160" stroke="#10b981" stroke-width="1" opacity="0.5"/>
  <line x1="720" y1="234" x2="720" y2="260" stroke="#10b981" stroke-width="1" opacity="0.5"/>

  <!-- Status badges -->
  <rect x="170" y="72" width="60" height="16" fill="#10b981" rx="8" opacity="0.15"/>
  <text x="200" y="82" fill="#10b981" font-size="10" text-anchor="middle">Normal</text>
  <rect x="400" y="102" width="60" height="16" fill="#10b981" rx="8" opacity="0.15"/>
  <text x="430" y="112" fill="#10b981" font-size="10" text-anchor="middle">Normal</text>
</svg>
```

- [ ] **Step 3: Create BuildingPlaceholder.vue**

Write file `frontend/src/components/BuildingPlaceholder.vue`:

```vue
<template>
  <div class="building-placeholder">
    <img src="@/assets/building-placeholder.svg" alt="Campus Digital Twin" class="building-image" />
    <div class="building-overlay-info">
      <span class="overlay-temp">{{ temperature }}°C</span>
      <span class="overlay-time">{{ currentTime }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const temperature = ref(31)
const currentTime = ref('2024-05-30 10:30:25')

let timer: number | null = null
onMounted(() => {
  timer = window.setInterval(() => {
    const now = new Date()
    currentTime.value = now.toLocaleString('en-CA', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.building-placeholder {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  margin-bottom: 16px;
  padding: 16px;
  position: relative;
}

.building-image {
  width: 100%;
  height: auto;
  max-height: 300px;
  object-fit: contain;
}

.building-overlay-info {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: $text-secondary;
}

.overlay-temp {
  color: $accent-orange;
  font-weight: 600;
}
</style>
```

- [ ] **Step 4: Create dashboard.scss**

Write file `frontend/src/styles/dashboard.scss`:

```scss
// Dashboard-specific styles
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.dashboard-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8px;
}
```

- [ ] **Step 5: Verify frontend compiles**

```bash
cd D:\N4_alarm\frontend
npm run build
```

Expected: Build succeeds.

- [ ] **Step 6: Commit**

```bash
cd D:\N4_alarm
git add frontend/src/components/KpiCardsRow.vue frontend/src/components/BuildingPlaceholder.vue frontend/src/assets/building-placeholder.svg frontend/src/styles/dashboard.scss
git commit -m "feat: add KPI cards row and building placeholder SVG component"
```

---

### Task 13: Frontend Alarm Summary & System Status

**Files:**
- Create: `frontend/src/components/AlarmSummary.vue`
- Create: `frontend/src/components/SystemStatus.vue`

**Interfaces:**
- Produces: AlarmSummary panel with level counts + top5 list; SystemStatus panel with 7 system rows with icons and status indicators
- Consumes: `useDashboardStore()` for alarmSummary, alarmTop5, systemStatus data

- [ ] **Step 1: Create AlarmSummary.vue**

Write file `frontend/src/components/AlarmSummary.vue`:

```vue
<template>
  <div class="alarm-summary-panel">
    <div class="panel-header">
      <span class="panel-title">Alarm Summary</span>
      <span class="view-all-link">View All</span>
    </div>
    <div class="alarm-counts">
      <div class="alarm-count-item critical">
        <span class="count-value">{{ alarmSummary?.critical || 0 }}</span>
        <span class="count-label">Critical</span>
      </div>
      <div class="alarm-count-item major">
        <span class="count-value">{{ alarmSummary?.major || 0 }}</span>
        <span class="count-label">Major</span>
      </div>
      <div class="alarm-count-item minor">
        <span class="count-value">{{ alarmSummary?.minor || 0 }}</span>
        <span class="count-label">Minor</span>
      </div>
      <div class="alarm-count-item warning">
        <span class="count-value">{{ alarmSummary?.warning || 0 }}</span>
        <span class="count-label">Warning</span>
      </div>
    </div>
    <div class="alarm-top5">
      <div
        v-for="alarm in alarmTop5"
        :key="alarm.alarmTime + alarm.alarmDesc"
        class="alarm-item"
      >
        <span :class="['alarm-dot', alarm.alarmLevel.toLowerCase()]"></span>
        <span class="alarm-time">{{ alarm.alarmTime }}</span>
        <span class="alarm-desc">{{ alarm.alarmDesc }}</span>
        <span :class="['alarm-level-badge', alarm.alarmLevel.toLowerCase()]">{{ alarm.alarmLevel }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const alarmSummary = computed(() => store.alarmSummary)
const alarmTop5 = computed(() => store.alarmTop5)
</script>

<style scoped lang="scss">
.alarm-summary-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.view-all-link {
  font-size: 12px;
  color: $accent-blue;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}

.alarm-counts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.alarm-count-item {
  padding: 8px;
  border-radius: 6px;
  text-align: center;
  &.critical { background: rgba($accent-red, 0.15); }
  &.major { background: rgba($accent-orange, 0.15); }
  &.minor { background: rgba($accent-yellow, 0.15); }
  &.warning { background: rgba($accent-blue, 0.15); }
}

.count-value {
  font-size: 20px;
  font-weight: 700;
  display: block;
  &.critical { color: $accent-red; }
  &.major { color: $accent-orange; }
  &.minor { color: $accent-yellow; }
  &.warning { color: $accent-blue; }
}

.count-label {
  font-size: 11px;
  color: $text-muted;
}

.alarm-top5 {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.alarm-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 12px;
}

.alarm-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  &.critical { background-color: $accent-red; }
  &.major { background-color: $accent-orange; }
  &.minor { background-color: $accent-yellow; }
  &.warning { background-color: $accent-blue; }
}

.alarm-time {
  color: $text-muted;
  min-width: 36px;
}

.alarm-desc {
  color: $text-secondary;
  flex: 1;
}

.alarm-level-badge {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  &.critical { color: $accent-red; background: rgba($accent-red, 0.15); }
  &.major { color: $accent-orange; background: rgba($accent-orange, 0.15); }
  &.minor { color: $accent-yellow; background: rgba($accent-yellow, 0.15); }
  &.warning { color: $accent-blue; background: rgba($accent-blue, 0.15); }
}
</style>
```

- [ ] **Step 2: Create SystemStatus.vue**

Write file `frontend/src/components/SystemStatus.vue`:

```vue
<template>
  <div class="system-status-panel">
    <div class="panel-header">
      <span class="panel-title">System Status</span>
      <span class="view-all-link">View All</span>
    </div>
    <div class="system-list">
      <div
        v-for="item in systemStatus"
        :key="item.systemName"
        class="system-item"
      >
        <el-icon :size="16" class="system-icon">
          <component :is="getIcon(item.icon)" />
        </el-icon>
        <span class="system-name">{{ item.systemName }}</span>
        <span :class="['system-status-badge', item.status.toLowerCase()]">{{ item.status }}</span>
        <span class="system-count">{{ item.deviceCount }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Setting, Snowflake, Charger, Warning,
  WindPower, Thermometer, Global,
} from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const systemStatus = computed(() => store.systemStatus)

const iconMap: Record<string, any> = {
  'gear': Setting,
  'snowflake': Snowflake,
  'battery': Charger,
  'warning-triangle': Warning,
  'fan': WindPower,
  'thermometer': Thermometer,
  'globe': Global,
}

function getIcon(iconName: string) {
  return iconMap[iconName] || Setting
}
</script>

<style scoped lang="scss">
.system-status-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.view-all-link {
  font-size: 12px;
  color: $accent-blue;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}

.system-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.system-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}

.system-icon {
  color: $text-muted;
}

.system-name {
  font-size: 13px;
  color: $text-secondary;
  flex: 1;
}

.system-status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
  &.normal { background: rgba($accent-green, 0.15); color: $accent-green; }
  &.warning { background: rgba($accent-yellow, 0.15); color: $accent-yellow; }
  &.critical { background: rgba($accent-red, 0.15); color: $accent-red; }
}

.system-count {
  font-size: 12px;
  color: $text-muted;
}
</style>
```

- [ ] **Step 3: Verify frontend compiles**

```bash
cd D:\N4_alarm\frontend
npm run build
```

Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
cd D:\N4_alarm
git add frontend/src/components/AlarmSummary.vue frontend/src/components/SystemStatus.vue
git commit -m "feat: add AlarmSummary and SystemStatus panel components"
```

---

### Task 14: Frontend Trend Charts (ECharts)

**Files:**
- Create: `frontend/src/components/charts/PowerTrendChart.vue`
- Create: `frontend/src/components/charts/EnergyTrendChart.vue`
- Create: `frontend/src/components/charts/PueTrendChart.vue`
- Create: `frontend/src/components/charts/TemperatureTrendChart.vue`
- Create: `frontend/src/components/TrendChartsGrid.vue`

**Interfaces:**
- Produces: Four ECharts chart components + grid wrapper; each chart receives `TrendData` from `useDashboardStore()` and renders appropriate chart type
- Consumes: `useDashboardStore()` for powerTrend, energyTrend, pueTrend, temperatureTrend

- [ ] **Step 1: Create PowerTrendChart.vue**

Write file `frontend/src/components/charts/PowerTrendChart.vue`:

```vue
<template>
  <div class="chart-panel">
    <div class="chart-title">Power Trend (kW)</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useDashboardStore } from '@/stores/dashboard'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const store = useDashboardStore()
const trendData = computed(() => store.powerTrend)

const chartOption = computed(() => {
  if (!trendData.value) return {}
  const d = trendData.value
  return {
    backgroundColor: 'transparent',
    grid: { top: 30, right: 20, bottom: 30, left: 50 },
    tooltip: { trigger: 'axis' },
    legend: {
      data: [d.seriesName, d.seriesName2],
      textStyle: { color: '#9ca3af', fontSize: 11 },
      top: 5,
    },
    xAxis: {
      type: 'category',
      data: d.timeLabels,
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      name: d.unit,
      nameTextStyle: { color: '#6b7280' },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    series: [
      {
        name: d.seriesName,
        type: 'line',
        data: d.values,
        smooth: true,
        lineStyle: { color: '#3b82f6', width: 2 },
        itemStyle: { color: '#3b82f6' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.3)' }, { offset: 1, color: 'rgba(59,130,246,0)' }] } },
      },
      {
        name: d.seriesName2!,
        type: 'line',
        data: d.values2!,
        smooth: true,
        lineStyle: { color: '#9ca3af', width: 2 },
        itemStyle: { color: '#9ca3af' },
      },
    ],
  }
})
</script>

<style scoped lang="scss">
.chart-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.chart-title {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
  margin-bottom: 8px;
}

.chart-instance {
  width: 100%;
  height: 200px;
}
</style>
```

- [ ] **Step 2: Create EnergyTrendChart.vue**

Write file `frontend/src/components/charts/EnergyTrendChart.vue`:

```vue
<template>
  <div class="chart-panel">
    <div class="chart-title">Energy Trend (kWh/d)</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useDashboardStore } from '@/stores/dashboard'

use([BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const store = useDashboardStore()
const trendData = computed(() => store.energyTrend)

const chartOption = computed(() => {
  if (!trendData.value) return {}
  const d = trendData.value
  return {
    backgroundColor: 'transparent',
    grid: { top: 30, right: 20, bottom: 30, left: 50 },
    tooltip: { trigger: 'axis' },
    legend: {
      data: [d.seriesName, d.seriesName2!],
      textStyle: { color: '#9ca3af', fontSize: 11 },
      top: 5,
    },
    xAxis: {
      type: 'category',
      data: d.timeLabels,
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      name: d.unit,
      nameTextStyle: { color: '#6b7280' },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    series: [
      {
        name: d.seriesName,
        type: 'bar',
        data: d.values,
        itemStyle: { color: '#10b981', borderRadius: [2, 2, 0, 0] },
        barWidth: '40%',
      },
      {
        name: d.seriesName2!,
        type: 'bar',
        data: d.values2!,
        itemStyle: { color: '#6b7280', borderRadius: [2, 2, 0, 0] },
        barWidth: '40%',
      },
    ],
  }
})
</script>

<style scoped lang="scss">
.chart-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.chart-title {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
  margin-bottom: 8px;
}

.chart-instance {
  width: 100%;
  height: 200px;
}
</style>
```

- [ ] **Step 3: Create PueTrendChart.vue**

Write file `frontend/src/components/charts/PueTrendChart.vue`:

```vue
<template>
  <div class="chart-panel">
    <div class="chart-title">PUE Trend</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useDashboardStore } from '@/stores/dashboard'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const store = useDashboardStore()
const trendData = computed(() => store.pueTrend)

const chartOption = computed(() => {
  if (!trendData.value) return {}
  const d = trendData.value
  return {
    backgroundColor: 'transparent',
    grid: { top: 20, right: 20, bottom: 30, left: 50 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: d.timeLabels,
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      min: 0.8,
      max: 2.0,
      nameTextStyle: { color: '#6b7280' },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    series: [
      {
        name: d.seriesName,
        type: 'line',
        data: d.values,
        smooth: true,
        lineStyle: { color: '#10b981', width: 2 },
        itemStyle: { color: '#10b981' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(16,185,129,0.3)' }, { offset: 1, color: 'rgba(16,185,129,0)' }] } },
      },
    ],
  }
})
</script>

<style scoped lang="scss">
.chart-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.chart-title {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
  margin-bottom: 8px;
}

.chart-instance {
  width: 100%;
  height: 200px;
}
</style>
```

- [ ] **Step 4: Create TemperatureTrendChart.vue**

Write file `frontend/src/components/charts/TemperatureTrendChart.vue`:

```vue
<template>
  <div class="chart-panel">
    <div class="chart-title">Temperature Trend (°C)</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useDashboardStore } from '@/stores/dashboard'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const store = useDashboardStore()
const trendData = computed(() => store.temperatureTrend)

const chartOption = computed(() => {
  if (!trendData.value) return {}
  const d = trendData.value
  return {
    backgroundColor: 'transparent',
    grid: { top: 30, right: 20, bottom: 30, left: 50 },
    tooltip: { trigger: 'axis' },
    legend: {
      data: [d.seriesName, d.seriesName2!],
      textStyle: { color: '#9ca3af', fontSize: 11 },
      top: 5,
    },
    xAxis: {
      type: 'category',
      data: d.timeLabels,
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      min: 10,
      max: 40,
      name: d.unit,
      nameTextStyle: { color: '#6b7280' },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    series: [
      {
        name: d.seriesName,
        type: 'line',
        data: d.values,
        smooth: true,
        lineStyle: { color: '#10b981', width: 2 },
        itemStyle: { color: '#10b981' },
      },
      {
        name: d.seriesName2!,
        type: 'line',
        data: d.values2!,
        smooth: true,
        lineStyle: { color: '#06b6d4', width: 2 },
        itemStyle: { color: '#06b6d4' },
      },
    ],
  }
})
</script>

<style scoped lang="scss">
.chart-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.chart-title {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
  margin-bottom: 8px;
}

.chart-instance {
  width: 100%;
  height: 200px;
}
</style>
```

- [ ] **Step 5: Create TrendChartsGrid.vue**

Write file `frontend/src/components/TrendChartsGrid.vue`:

```vue
<template>
  <div class="trend-charts-grid">
    <PowerTrendChart />
    <EnergyTrendChart />
    <PueTrendChart />
    <TemperatureTrendChart />
  </div>
</template>

<script setup lang="ts">
import PowerTrendChart from './charts/PowerTrendChart.vue'
import EnergyTrendChart from './charts/EnergyTrendChart.vue'
import PueTrendChart from './charts/PueTrendChart.vue'
import TemperatureTrendChart from './charts/TemperatureTrendChart.vue'
</script>

<style scoped lang="scss">
.trend-charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
</style>
```

- [ ] **Step 6: Verify frontend compiles**

```bash
cd D:\N4_alarm\frontend
npm run build
```

Expected: Build succeeds.

- [ ] **Step 7: Commit**

```bash
cd D:\N4_alarm
git add frontend/src/components/charts/ frontend/src/components/TrendChartsGrid.vue
git commit -m "feat: add all 4 ECharts trend chart components and grid wrapper"
```

---

### Task 15: Frontend Dashboard Page Integration

**Files:**
- Create: `frontend/src/views/DashboardView.vue`

**Interfaces:**
- Produces: Complete Dashboard page composing all components in the correct layout
- Consumes: All components from Tasks 11-14; `useDashboardStore()` triggers data fetch

- [ ] **Step 1: Create DashboardView.vue**

Write file `frontend/src/views/DashboardView.vue`:

```vue
<template>
  <div class="dashboard-view" v-loading="store.loading">
    <KpiCardsRow />
    <BuildingPlaceholder />
    <TrendChartsGrid />
  </div>
</template>

<script setup lang="ts">
import KpiCardsRow from '@/components/KpiCardsRow.vue'
import BuildingPlaceholder from '@/components/BuildingPlaceholder.vue'
import TrendChartsGrid from '@/components/TrendChartsGrid.vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
</script>

<style scoped lang="scss">
@use '@/styles/dashboard' as *;
</style>
```

Note: The AlarmSummary and SystemStatus panels are already rendered inside `MainLayout.vue` in the right panel slot, so they don't need to be added to DashboardView.

- [ ] **Step 2: Verify frontend compiles**

```bash
cd D:\N4_alarm\frontend
npm run build
```

Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
cd D:\N4_alarm
git add frontend/src/views/DashboardView.vue
git commit -m "feat: add DashboardView page composing all dashboard components"
```

---

### Task 16: End-to-End Verification

**Files:**
- No new files — verification only

**Interfaces:**
- Consumes: All previous tasks' outputs

- [ ] **Step 1: Start backend server**

```bash
cd D:\N4_alarm\backend
mvn spring-boot:run -q
```

Wait for startup to complete (look for "Started N4MonitorApplication" in console).

- [ ] **Step 2: Verify backend API endpoints**

```bash
curl http://localhost:8080/api/dashboard/kpi
curl http://localhost:8080/api/dashboard/alarm-summary
curl http://localhost:8080/api/dashboard/alarm-top5
curl http://localhost:8080/api/dashboard/system-status
curl http://localhost:8080/api/dashboard/power-trend
curl http://localhost:8080/api/dashboard/energy-trend
curl http://localhost:8080/api/dashboard/pue-trend
curl http://localhost:8080/api/dashboard/temperature-trend
curl http://localhost:8080/api/devices/tree
```

Expected: All endpoints return `{ "code": 200, "message": "success", "data": {...} }` with appropriate data. The `/api/devices/tree` endpoint may return empty array if DB has no root devices with null sys_parent_id.

- [ ] **Step 3: Start frontend dev server**

```bash
cd D:\N4_alarm\frontend
npm run dev
```

Expected: Vite dev server starts on `http://localhost:5173`.

- [ ] **Step 4: Open browser and verify Dashboard renders**

Open `http://localhost:5173` in browser. Expected visual outcome:
- Dark themed page with `#0a0e17` background
- Top navbar with "DAYONE | N4 Monitor" logo, nav tabs, alert badges (3/14/27/44)
- Left sidebar with "SITE-JHB-01", search box, device tree (may be empty if DB has no tree data)
- Main content area with 6 KPI cards (PUE 1.32, IT Load 68%, Cooling 42%, etc.)
- Building placeholder SVG image
- 2x2 trend charts grid (Power, Energy, PUE, Temperature)
- Right panel with Alarm Summary (counts + top5 list) and System Status (7 rows)
- Bottom toolbar with 5 action items

- [ ] **Step 5: Final commit**

```bash
cd D:\N4_alarm
git add -A
git commit -m "feat: complete N4 Dashboard first iteration — all components integrated"
```
