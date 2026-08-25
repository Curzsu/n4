/*
 * Shared visual helper for FUXA detail-card pool scripts.
 * FUXA runs each client script in its own function scope, so the pool script
 * calls this helper after it has found the newly created card.
 */
(function installN4CabinDevicePanelBridge() {
  function buildDeviceListUrl(panel) {
    const pageParameters = new URLSearchParams(globalThis.location.search);
    const deviceListUrl = pageParameters.get('n4DeviceListUrl') || '';
    if (!deviceListUrl) return '';

    try {
      const url = new URL(deviceListUrl);
      url.searchParams.set('panelKey', panel.panelKey || '');
      url.searchParams.set('templateType', panel.templateType || '');
      url.searchParams.set('deviceRole', panel.deviceRole || '');
      url.searchParams.set('mqttDeviceName', panel.mqttDeviceName || '');
      url.searchParams.set('fields', JSON.stringify(panel.fields || []));
      return url.toString();
    } catch (error) {
      console.warn('[N4] Cannot build the detail-card list URL.', error);
      return '';
    }
  }

  globalThis.n4AttachDevicePointList = function n4AttachDevicePointList(card, panel) {
    if (!card || card.querySelector('[data-n4-device-points]')) return false;
    const src = buildDeviceListUrl(panel || {});
    if (!src) return false;

    const listHeight = 220;
    const nextHeight = Math.ceil(card.getBoundingClientRect().height) + listHeight;
    card.dataset.n4DevicePoints = 'true';
    card.style.position = card.style.position || 'relative';
    card.style.overflow = 'visible';
    card.style.height = `${nextHeight}px`;
    card.style.minHeight = `${nextHeight}px`;

    const container = document.createElement('section');
    container.dataset.n4DevicePoints = 'true';
    Object.assign(container.style, {
      position: 'absolute', left: '12px', right: '12px', bottom: '12px',
      height: `${listHeight - 24}px`, overflow: 'hidden',
      border: '1px solid #1d3445', background: '#090d12', zIndex: '10'
    });
    const frame = document.createElement('iframe');
    frame.src = src;
    frame.title = 'N4 device point list';
    frame.setAttribute('scrolling', 'auto');
    Object.assign(frame.style, {
      display: 'block', width: '100%', height: '100%', border: '0', background: '#090d12'
    });
    container.appendChild(frame);
    card.appendChild(container);
    return true;
  };
}());
