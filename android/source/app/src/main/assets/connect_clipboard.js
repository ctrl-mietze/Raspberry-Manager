/* Executed only on Connect's HTTPS main frame. No code runs on the ID/login origin. */
(() => {
  if (window !== window.top || location.origin !== 'https://connect.raspberrypi.com') return false;
  if (window.__raspberryConnectClipboard) window.__raspberryConnectClipboard.stop();
  let port = null, nextId = 0;
  const waiting = new Map();
  const handshake = event => {
    if (event.data !== '__CHANNEL_NONCE__' || !event.ports || event.ports.length !== 1) return;
    window.removeEventListener('message', handshake);
    port = event.ports[0];
    port.onmessage = response => {
      let data;
      try { data = JSON.parse(response.data); } catch (_) { return; }
      const request = waiting.get(data.id);
      if (!request) return;
      clearTimeout(request.timer); waiting.delete(data.id);
      if (data.ok) request.resolve(data.text || '');
      else request.reject(new DOMException('Clipboard access cancelled', 'NotAllowedError'));
    };
    port.start();
  };
  window.addEventListener('message', handshake);
  function request(kind, text = '') {
    if (!port) return Promise.reject(new DOMException('Connect clipboard not ready', 'NotAllowedError'));
    if (navigator.userActivation && !navigator.userActivation.isActive)
      return Promise.reject(new DOMException('Tap a clipboard button first', 'NotAllowedError'));
    if (text.length > 65536) return Promise.reject(new DOMException('Text is too long', 'DataError'));
    return new Promise((resolve, reject) => {
      const id = ++nextId;
      const timer = setTimeout(() => { waiting.delete(id); reject(new DOMException('Clipboard confirmation timed out', 'NotAllowedError')); }, 60000);
      waiting.set(id, { resolve, reject, timer });
      port.postMessage(JSON.stringify({id, kind, text}));
    });
  }
  // Connect uses the standard readText / writeText clipboard API.
  // Preserve any other browser methods; replace only these two integrations.
  const existing = navigator.clipboard || {};
  const replacement = Object.create(existing);
  Object.defineProperties(replacement, {
    readText: {value: () => request('read')},
    writeText: {value: value => request('write', String(value)).then(() => undefined)}
  });
  try { Object.defineProperty(navigator, 'clipboard', {value: replacement, configurable: true}); }
  catch (_) { return false; }
  window.__raspberryConnectClipboard = {
    stop() {
      window.removeEventListener('message', handshake);
      if (port) port.close();
      waiting.forEach(p => { clearTimeout(p.timer); p.reject(new DOMException('Page changed', 'AbortError')); });
      waiting.clear();
    }
  };
  return true;
})();
