const sourceDeviceId = new URLSearchParams(window.location.search).get('sourceDeviceId');
document.documentElement.classList.toggle('n4-source-device-mode', Boolean(sourceDeviceId));
if (sourceDeviceId && window.__n4FuxaSourceDeviceId !== sourceDeviceId) {
  window.__n4FuxaSourceDeviceId = sourceDeviceId;
  $openCard('%s', {
    hideClose: true,
    left: 0,
    top: 54,
    relativeFrom: 'window',
    singleCard: true,
    sourceDeviceId
  });
}
