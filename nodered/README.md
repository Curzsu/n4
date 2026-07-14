# Dynamic FUXA device binding

This flow subscribes to one MQTT wildcard:

```text
n4/Drivers/ModbusTcpNetwork/LVP_A4/points/+/+
```

The penultimate topic segment is treated as `mqttDeviceId`. On first sighting, Node-RED calls:

```text
POST http://127.0.0.1:8089/api/fuxa/discover
```

The Spring backend clones the configured FUXA MQTT template device, replaces only the device segment in every tag topic, and stores the app-device-to-FUXA-device mapping. Telemetry is not relayed through Node-RED; each generated FUXA device subscribes to MQTT directly.

## Setup

1. Import `fuxa-device-discovery-flow.json` in Node-RED.
2. Open the `N4 MQTT` broker configuration and enter the real username, password, TLS mode, and port.
3. Keep the wildcard in sync with the real topic root.
4. If an MQTT id differs from MySQL `device.device_code`, edit `deviceMap` in the `Extract and de-duplicate device` Function node:

```javascript
const deviceMap = {
  EMSB_03_EM32: 'H1_EMSB_A4_03_EM'
}
```

5. Deploy the flow after the Spring backend and FUXA are running.

The flow de-duplicates discovery calls for ten minutes. Repeated calls are safe because FUXA device and tag ids are deterministic.
