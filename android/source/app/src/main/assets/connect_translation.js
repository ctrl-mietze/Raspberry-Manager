/* Raspberry Manager Connect Translation v1.
 * Uses only the current first-party document. Never reads cookies, input values,
 * storage, authentication tokens, terminal text or framebuffer pixels.
 * Allowed native actions are derived from existing provider controls, never
 * invented endpoints. Authentication and provider permission checks stay intact.
 */
(() => {
  'use strict';
  if (window !== window.top || location.origin !== 'https://connect.raspberrypi.com') return;
  if (window.__rmConnectTranslation?.version === 1) return;
  const epoch = __EPOCH__;
  const elements = new Map();
  const handles = new WeakMap();
  let next = 1, focus = null, dirty = true, cached = null, lastRead = 0;
  const MAX_DEVICES = 100;
  const text = (value, limit = 180) => String(value || '').replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, limit);
  const nodeText = n => text(n?.textContent);
  const all = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const urlOf = n => { try { return new URL(n.getAttribute('href') || n.getAttribute('action') || '', location.href); } catch (_) { return null; } };
  const internal = u => u && u.origin === location.origin && !u.username && !u.password;
  const devicePath = u => internal(u) && /^\/devices\/[^/?#]+\/?$/.test(u.pathname);
  const token = n => {
    if (!handles.has(n)) { const id = 'a_' + next++; handles.set(n, id); elements.set(id, n); }
    const id = handles.get(n); elements.set(id, n); return id;
  };
  const kindOf = n => {
    const label = nodeText(n).toLowerCase();
    if (/^(remote shell|open remote shell|terminal|shell öffnen|remote-shell)$/.test(label)) return 'shell';
    if (/^(screen sharing|share screen|remote desktop|bildschirmübertragung|bildschirmfreigabe)$/.test(label)) return 'screen';
    return '';
  };
  const disabled = n => {
    const cls = typeof n.className === 'string' ? n.className : '';
    return n.disabled === true || n.hasAttribute('disabled') || n.getAttribute('aria-disabled') === 'true' ||
      /(^|\s)(disabled|is-disabled|pointer-events-none|cursor-not-allowed)(\s|$)/.test(cls) ||
      !!n.closest('[aria-disabled="true"],fieldset[disabled]');
  };
  const actionable = n => {
    if (!n || !n.isConnected || disabled(n)) return false;
    if (n.tagName === 'A') return internal(urlOf(n)) && !!n.getAttribute('href') && !/^javascript:/i.test(n.getAttribute('href'));
    if (n.tagName === 'BUTTON' || n.getAttribute('role') === 'button' || n.getAttribute('role') === 'menuitem') {
      const form = n.closest('form');
      return !form || internal(urlOf(form));
    }
    return false;
  };
  const safeForKind = (n, kind) => {
    if (!actionable(n)) return false;
    const owner = n.tagName === 'A' ? n : n.closest('form');
    const u = owner ? urlOf(owner) : null;
    if (kind === 'signin') return owner && internal(u) && u.pathname === '/auth/raspberry_pi';
    if (kind === 'detail') return n.tagName === 'A' && devicePath(u);
    if (owner) {
      if (!internal(u) || !/^\/(devices|sessions?|connections?|shell|vnc|screen|remote-shell|screen-sharing)(\/|$)/i.test(u.pathname)) return false;
      if (/\/(delete|destroy|remove|reboot|shutdown|reset|unlink|sign-out)(?:\/|$)/i.test(u.pathname)) return false;
    }
    return true;
  };
  const control = (n, kind, fallback) => {
    if (!n) return null;
    const enabled = safeForKind(n, kind);
    return { handle: token(n), kind, label: nodeText(n) || fallback, enabled,
      reason: enabled ? '' : (text(n.getAttribute('title') || n.getAttribute('data-tooltip-content')) ||
        'Raspberry Pi Connect is not offering this action for this device right now.') };
  };
  const findService = (root, kind) => {
    const hits = all('a,button,[role="menuitem"],[role="button"],span[aria-disabled],span.disabled', root)
      .filter(n => kindOf(n) === kind);
    return hits.find(actionable) || hits[0] ||
      all('span', root).find(n => n.children.length === 0 && kindOf(n) === kind) || null;
  };
  const nameLinks = root => all('a[href]', root).filter(a => devicePath(urlOf(a)) && nodeText(a));
  const containerFor = anchor => {
    const explicit = anchor.closest('[id^="device_"],[data-device-id],tr,[data-controller~="device"]');
    if (explicit && nameLinks(explicit).length <= 3) return explicit;
    let n = anchor.parentElement, best = n;
    for (let depth = 0; n && n !== document.body && depth < 7; depth++, n = n.parentElement) {
      const keys = new Set(nameLinks(n).map(a => urlOf(a).pathname));
      if (keys.size > 1) break;
      best = n;
      if (findService(n, 'shell') || findService(n, 'screen')) return n;
    }
    return best || anchor;
  };
  const onlineOf = root => {
    for (const el of [root, ...all('[title],[aria-label],[data-status],[data-state],.status,svg,span', root)]) {
      const metadata = text([el.getAttribute('title'),el.getAttribute('aria-label'),el.getAttribute('data-status'),el.getAttribute('data-state'),el.querySelector('title')?.textContent].filter(Boolean).join(' ')).toLowerCase();
      if (/\boffline\b|\bdisconnected\b/.test(metadata)) return 'offline';
      if (/\bonline\b/.test(metadata)) return 'online';
    }
    // Provider status-dot classes are a secondary hint, not inferred from a
    // successful login or from the mere presence of a device card.
    const classes = [root, ...all('span,svg,i,div[class*="status"]', root)]
      .map(n => typeof n.className === 'string' ? n.className : n.getAttribute('class') || '').join(' ');
    if (/(^|\s)(?:status--offline|status-offline)(\s|$)/.test(classes)) return 'offline';
    if (/(^|\s)(?:status--online|status-online|bg-green-\d+|text-green-\d+|fill-green-\d+|bg-success|text-success)(\s|$)/.test(classes)) return 'online';
    return 'unknown';
  };
  const notice = () => all('[role="alert"],.flash--alert,.flash--error,[data-connection-error],.connection-error')
    .filter(n => !n.closest('form') && n.getAttribute('aria-hidden') !== 'true')
    .map(n => text(n.textContent, 260)).filter(Boolean).slice(0, 3).join('\n');
  const targetViewport = () => {
    const terminal = document.querySelector('.xterm');
    if (terminal && terminal.querySelector('.xterm-screen,.xterm-rows,canvas')) return {node:terminal,kind:'terminal'};
    const canvas = all('canvas').find(n => n.width >= 240 && n.height >= 120 &&
      !n.closest('form,header,nav,footer,[data-controller~="id"]'));
    if (canvas) return {node:canvas.parentElement || canvas,kind:'screen'};
    const video = all('video').find(n => !n.closest('header,footer,nav') && (n.srcObject || n.videoWidth > 0));
    return video ? {node:video.parentElement || video,kind:'screen'} : null;
  };
  const removeFocus = () => {
    if (!focus) return;
    all('.rm-connect-hidden').forEach(n => n.classList.remove('rm-connect-hidden'));
    all('.rm-connect-path,.rm-connect-focus').forEach(n => n.classList.remove('rm-connect-path','rm-connect-focus'));
    document.documentElement.classList.remove('rm-connect-session');
    focus = null;
  };
  const focusViewport = () => {
    const target = targetViewport();
    if (!target) { removeFocus(); return ''; }
    if (focus !== target.node) {
      removeFocus();
      let style = document.getElementById('rm-connect-viewport-style');
      if (!style) {
        style = document.createElement('style'); style.id = 'rm-connect-viewport-style';
        style.textContent = `
          html.rm-connect-session,html.rm-connect-session body{margin:0!important;padding:0!important;width:100%!important;height:100%!important;overflow:hidden!important;background:#08090b!important}
          .rm-connect-hidden{display:none!important}
          .rm-connect-path{margin:0!important;padding:0!important;max-width:none!important;border:0!important;transform:none!important;overflow:visible!important}
          .rm-connect-focus{position:fixed!important;inset:0!important;margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:999!important;background:#08090b!important;overflow:hidden!important}
          .rm-connect-focus.xterm{padding:6px!important;box-sizing:border-box!important}
          .rm-connect-focus>canvas,.rm-connect-focus>video{max-width:100%!important;max-height:100%!important;object-fit:contain}
        `;
        document.head.appendChild(style);
      }
      focus = target.node;
      let cursor = focus;
      while (cursor && cursor !== document.body) {
        const parent = cursor.parentElement;
        if (!parent) break;
        for (const sibling of parent.children) {
          if (sibling !== cursor && !['SCRIPT','STYLE','LINK'].includes(sibling.tagName)) sibling.classList.add('rm-connect-hidden');
        }
        parent.classList.add('rm-connect-path'); cursor = parent;
      }
      focus.classList.add('rm-connect-focus');
      document.documentElement.classList.add('rm-connect-session');
      window.dispatchEvent(new Event('resize'));
    }
    return target.kind;
  };
  const snapshot = () => {
    if (location.origin !== 'https://connect.raspberrypi.com') return null;
    // No cache across Turbo route changes. Limit the cost of repeated native polls.
    if (!dirty && cached && Date.now() - lastRead < 800) return cached;
    dirty = false; lastRead = Date.now();
    for (const [id,n] of elements) if (!n.isConnected) elements.delete(id);
    const signin = all('form').find(f => {
      const u = urlOf(f);
      return internal(u) && u.pathname === '/auth/raspberry_pi' &&
        !f.querySelector('input[name="prompt"][value="create"]');
    });
    const authPage = /^\/(?:sign-in|auth)(?:\/|$)/.test(location.pathname);
    const seen = new Set(), devices = [];
    for (const a of nameLinks(document)) {
      const path = urlOf(a).pathname;
      if (seen.has(path)) continue;
      seen.add(path);
      if (devices.length >= MAX_DEVICES) break;
      const card = containerFor(a);
      const shell = findService(card,'shell'), screen = findService(card,'screen');
      devices.push({key:token(a),name:nodeText(a),status:onlineOf(card),
        detail:control(a,'detail','Device details'), shell:control(shell,'shell','Remote shell'),screen:control(screen,'screen','Screen sharing')});
    }
    const globalActions = [];
    if (signin) {
      const b = signin.querySelector('button[type="submit"],button:not([type]),input[type="submit"]');
      if (b) globalActions.push(control(b,'signin','Sign in with Raspberry Pi ID'));
    }
    const isSessionPath = /\/(?:sessions?|connections?|shell|vnc|screen)(?:\/|$)/i.test(location.pathname);
    const currentDevice = devicePath(new URL(location.href));
    if (isSessionPath || (devices.length === 0 && currentDevice)) {
      for (const kind of ['shell','screen']) {
        const n=findService(document,kind); if(n) globalActions.push(control(n,kind,kind));
      }
      // Explicit provider pre-connect/retry controls are translated, not auto-submitted.
      const safeLabels = /^(connect|start session|start remote shell|start screen sharing|retry|try again|reconnect|connect again)$/i;
      for (const n of all('button,a[href],[role="button"]')) {
        if (safeLabels.test(nodeText(n)) && !n.closest('header,nav,footer,form:has(input[type="password"])'))
          globalActions.push(control(n,'session',nodeText(n)));
      }
    }
    let viewport = '';
    if (!authPage && !signin && (isSessionPath || devices.length === 0 || document.querySelector('.xterm'))) viewport = focusViewport();
    else removeFocus();
    const headings=all('h1,h2,[role="heading"]').map(nodeText);
    const listPage = /^\/devices\/?$/.test(location.pathname) ||
      (location.pathname==='/' && headings.some(t=>/^devices$/i.test(t)));
    let page = authPage || (signin && devices.length===0) ? 'signin' :
      viewport || isSessionPath ? 'session' : devices.length || listPage ? 'devices' :
      currentDevice ? 'detail' : 'unsupported';
    const title = text(headings.find(s=>!/^devices$/i.test(s)) || '',100);
    const firstCard = devices[0] ? containerFor(elements.get(devices[0].key)) : null;
    const schema = firstCard ? {
      root: firstCard.tagName + ' ' + text(firstCard.getAttribute('class'),120),
      services: all('a,button,span',firstCard).filter(n=>kindOf(n)).slice(0,8).map(n=>({
        tag:n.tagName,label:nodeText(n),kind:kindOf(n),href:n.hasAttribute('href'),
        route:n.hasAttribute('href') ? (urlOf(n)?.pathname || '').replace(/\/devices\/[^/]+/g,'/devices/{id}').slice(0,150) : '',
        disabled:disabled(n),classes:text(n.getAttribute('class'),160),title:text(n.getAttribute('title'),180)
      })),
      indicators: all('span,svg',firstCard).slice(0,10).map(n=>({tag:n.tagName,
        classes:text(n.getAttribute('class'),160),title:text(n.getAttribute('title'),80),aria:text(n.getAttribute('aria-label'),80),svgTitle:text(n.querySelector('title')?.textContent,80)}))
    } : {};
    const out = {version:1,epoch,page,devices,actions:globalActions.filter(Boolean).slice(0,10),viewport,
      notice:notice(),title,hasMore:seen.size>MAX_DEVICES,
      structure:{schema,deviceLinks:nameLinks(document).length,forms:all('form').length,terminals:all('.xterm').length,canvases:all('canvas').length}};
    cached=out; return out;
  };
  const dispatch = (requestedEpoch, id) => {
    if (requestedEpoch !== epoch || !/^a_\d{1,8}$/.test(String(id))) return {ok:false,reason:'stale'};
    const n=elements.get(id);
    if (!n || !n.isConnected) return {ok:false,reason:'stale'};
    // Regenerate the allowlist from this document immediately before acting.
    dirty=true;
    const s=snapshot();
    const allowed=[...s.actions,...s.devices.flatMap(d=>[d.detail,d.shell,d.screen].filter(Boolean))];
    const action=allowed.find(a=>a.handle===id);
    if (!action) return {ok:false,reason:'not-allowed'};
    if (!action.enabled || !safeForKind(n,action.kind)) return {ok:false,reason:'unavailable'};
    if (action.kind!=='signin' && n.tagName==='A') {
      const u=urlOf(n);
      if(!internal(u))return {ok:false,reason:'origin'};
    }
    // Expanding an existing menu changes presentation only. The original control
    // performs its own form/CSRF/permission handling when clicked.
    for(const d of [n.closest('details')].filter(Boolean)) d.open=true;
    n.click(); dirty=true;
    return {ok:true,kind:action.kind};
  };
  const observer=new MutationObserver(records=>{
    if(records.some(r=>r.type!=='attributes'||!['style','class'].includes(r.attributeName))) dirty=true;
  });
  observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,characterData:true});
  window.__rmConnectTranslation={version:1,epoch,snapshot,dispatch,stop:()=>{observer.disconnect();removeFocus();delete window.__rmConnectTranslation;}};
})();
