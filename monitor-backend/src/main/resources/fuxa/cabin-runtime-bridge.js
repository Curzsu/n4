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

function rememberCabinParameters(mqttDeviceName, cabinViewName, n4AccessToken) {
  runtime.mqttDeviceName = mqttDeviceName;
  runtime.cabinViewName = cabinViewName;
  runtime.n4AccessToken = n4AccessToken || '';

  // The iframe is preloaded without a cabin-specific URL so it can be reused.
  // Keep the active cabin in the URL without reloading; existing slot-pool
  // scripts already use location.search as their fallback parameter source.
  try {
    const url = new URL(globalThis.location.href);
    url.searchParams.set('mqttDeviceName', mqttDeviceName);
    url.searchParams.set('cabinViewName', cabinViewName);
    globalThis.history.replaceState(globalThis.history.state, '', url.toString());
  } catch (error) {
    console.warn('Unable to retain active cabin parameters in the FUXA URL.', error);
  }
}

function getMainViewContainer() {
  const containers = Array.from(document.querySelectorAll('app-fuxa-view > .view-container'));
  return containers.find(element => !element.closest('.fab-card')) || containers[0] || null;
}

function waitForViewRender(previousMarkup, timeoutMs = 15000) {
  const startedAt = Date.now();
  return new Promise((resolve, reject) => {
    let viewChanged = false;
    const observedContainer = getMainViewContainer();
    const observer = new MutationObserver(() => {
      viewChanged = true;
    });
    if (observedContainer) {
      observer.observe(observedContainer, { childList: true, subtree: true });
    }

    const check = () => {
      const container = getMainViewContainer();
      const markup = container?.innerHTML || '';
      const visible = !!container && getComputedStyle(container).visibility !== 'hidden';
      if (visible && markup && (viewChanged || markup !== previousMarkup)) {
        observer.disconnect();
        resolve();
        return;
      }
      if (Date.now() - startedAt >= timeoutMs) {
        observer.disconnect();
        reject(new Error('Timed out while waiting for the cabin view to render.'));
        return;
      }
      setTimeout(check, 100);
    };
    setTimeout(check, 0);
  });
}

runtime.openCabin ??= async (mqttDeviceName, cabinViewName, n4AccessToken) => {
  if (!mqttDeviceName || !cabinViewName) return;

  // The first URL-driven open happens before N4's postMessage handshake.
  // Keep a later token-bearing request even when it targets the same view.
  if (n4AccessToken) runtime.n4AccessToken = n4AccessToken;

  const runtimeKey = `${mqttDeviceName}::${cabinViewName}`;
  if (runtime.key === runtimeKey && runtime.state === 'opening') {
    return;
  }
  if (runtime.key === runtimeKey && runtime.state === 'open') {
    notifyParent('n4-cabin-runtime:opened', { mqttDeviceName, cabinViewName, reused: true });
    return;
  }

  runtime.key = runtimeKey;
  runtime.state = 'opening';
  rememberCabinParameters(mqttDeviceName, cabinViewName, n4AccessToken);
  document.documentElement.classList.add('n4-cabin-runtime-mode');
  try {
    const previousMarkup = getMainViewContainer()?.innerHTML || '';
    await $setAdapterToDevice('A1_MVP_A1_CABIN_ADAPTER', mqttDeviceName);
    $setView(cabinViewName, true);
    await waitForViewRender(previousMarkup);
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
    void runtime.openCabin(request.mqttDeviceName, request.cabinViewName, request.n4AccessToken);
  });
}

const activeViewName = pageParameters.get('viewName');
const initialMqttDeviceName = pageParameters.get('mqttDeviceName');
const initialCabinViewName = pageParameters.get('cabinViewName');
if (activeViewName === hostViewName && initialMqttDeviceName && initialCabinViewName) {
  void runtime.openCabin(initialMqttDeviceName, initialCabinViewName);
}
