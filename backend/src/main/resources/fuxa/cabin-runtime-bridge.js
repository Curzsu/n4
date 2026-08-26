/*
 * The N4 parent keeps one FUXA iframe alive. This client script receives a
 * cabin-switch request from that parent and changes only FUXA runtime state.
 */
const hostViewName = 'N4_CABIN_RUNTIME_HOST';
const pageParameters = new URLSearchParams(globalThis.location.search);
const initialParentOrigin = pageParameters.get('n4ParentOrigin') || '';

globalThis.__n4CabinRuntime ??= {
  key: null,
  state: 'idle',
  parentOrigin: initialParentOrigin,
  bridgeInstalled: false,
};

const runtime = globalThis.__n4CabinRuntime;
if (initialParentOrigin) runtime.parentOrigin = initialParentOrigin;

function notifyParent(type, detail = {}) {
  if (globalThis.parent === globalThis) return;
  globalThis.parent.postMessage({ type, ...detail }, runtime.parentOrigin || '*');
}

runtime.openCabin ??= async (mqttDeviceName, cabinViewName) => {
  if (!mqttDeviceName || !cabinViewName) return;

  const runtimeKey = `${mqttDeviceName}::${cabinViewName}`;
  if (runtime.key === runtimeKey && (runtime.state === 'opening' || runtime.state === 'open')) {
    notifyParent('n4-cabin-runtime:opened', { mqttDeviceName, cabinViewName, reused: true });
    return;
  }

  runtime.key = runtimeKey;
  runtime.state = 'opening';
  document.documentElement.classList.add('n4-cabin-runtime-mode');
  try {
    await $setAdapterToDevice('A1_MVP_A1_CABIN_ADAPTER', mqttDeviceName);
    $setView(cabinViewName, true);
    runtime.state = 'open';
    notifyParent('n4-cabin-runtime:opened', { mqttDeviceName, cabinViewName });
    console.log('Loaded reusable cabin view with device adapter:', cabinViewName, mqttDeviceName);
  } catch (error) {
    runtime.state = 'error';
    notifyParent('n4-cabin-runtime:error', {
      mqttDeviceName,
      cabinViewName,
      message: error instanceof Error ? error.message : String(error),
    });
    console.error('Unable to map and open reusable cabin view:', error);
  }
};

if (!runtime.bridgeInstalled) {
  runtime.bridgeInstalled = true;
  globalThis.addEventListener('message', event => {
    if (event.source !== globalThis.parent) return;
    if (runtime.parentOrigin && event.origin !== runtime.parentOrigin) return;
    if (!runtime.parentOrigin) runtime.parentOrigin = event.origin;

    const request = event.data;
    if (!request || request.type !== 'n4-cabin-runtime:open') return;
    void runtime.openCabin(request.mqttDeviceName, request.cabinViewName);
  });
}

const activeViewName = pageParameters.get('viewName');
const initialMqttDeviceName = pageParameters.get('mqttDeviceName');
const initialCabinViewName = pageParameters.get('cabinViewName');
if (activeViewName === hostViewName && initialMqttDeviceName && initialCabinViewName) {
  void runtime.openCabin(initialMqttDeviceName, initialCabinViewName);
}
