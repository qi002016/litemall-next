/* ============================================================================
 * 小满商城 · 原型 UI 工具（无依赖）
 * 约定：所有 HTML 片段都以字符串返回，页面用 innerHTML 挂载。
 * 导航统一用 data-go="#/c/xxx"（由 app.js 的事件委托处理），
 * 交互用 data-act + 页面自己的 click 委托。
 * ==========================================================================*/
(function () {
  'use strict';

  var esc = function (s) {
    if (s === null || s === undefined) return '';
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };

  function pad(n) { return n < 10 ? '0' + n : '' + n; }

  /* ------------------------------ 金额 / 时间 ------------------------------ */
  function yuan(cent) {
    var v = Math.round(cent || 0) / 100;
    var s = v.toFixed(2);
    return s.replace(/\.00$/, '');
  }
  function price(cent, cls) {
    return '<span class="price ' + (cls || '') + '"><span class="sym">¥</span>' + esc(yuan(cent)) + '</span>';
  }
  function time(ts, withTime) {
    if (!ts) return '—';
    var d = new Date(ts);
    var s = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    if (withTime !== false) s += ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    return s;
  }
  function clock(ts) {
    if (!ts) return '—';
    var d = new Date(ts);
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
  }
  function fromNow(ts) {
    if (!ts) return '';
    var d = Date.now() - ts;
    if (d < 0) d = 0;
    if (d < 60000) return '刚刚';
    if (d < 3600000) return Math.floor(d / 60000) + ' 分钟前';
    if (d < 86400000) return Math.floor(d / 3600000) + ' 小时前';
    return Math.floor(d / 86400000) + ' 天前';
  }
  function countdown(ms) {
    if (ms <= 0) return '00:00';
    var m = Math.floor(ms / 60000);
    var s = Math.floor((ms % 60000) / 1000);
    return pad(m) + ':' + pad(s);
  }

  /* ------------------------------ 图标 ------------------------------ */
  var ICONS = {
    home: '<path d="M3 10.5 12 3l9 7.5V21a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1V10.5Z"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
    cart: '<path d="M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.6L21 8H6"/><circle cx="10" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6.5 8-6.5S20 17 20 21"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.6-3.6"/>',
    back: '<path d="M15 5l-7 7 7 7"/>',
    right: '<path d="M9 5l7 7-7 7"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    minus: '<path d="M5 12h14"/>',
    check: '<path d="m4 12.5 5 5L20 6.5"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    truck: '<path d="M2 7h11v9H2z"/><path d="M13 11h4l4 3v2h-8z"/><circle cx="6" cy="18.5" r="1.6"/><circle cx="17.5" cy="18.5" r="1.6"/>',
    box: '<path d="m12 3 8.5 4.7v8.6L12 21l-8.5-4.7V7.7L12 3Z"/><path d="M3.5 7.7 12 12.4l8.5-4.7M12 12.4V21"/>',
    money: '<circle cx="12" cy="12" r="9"/><path d="M8.5 9.5h7M8.5 13h7M12 9.5V17"/>',
    star: '<path d="m12 3.5 2.7 5.6 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1L3.2 10l6.1-.9L12 3.5Z"/>',
    chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    tag: '<path d="M3 12.5V4a1 1 0 0 1 1-1h8.5L21 11.5 12.5 20 3 12.5Z"/><circle cx="7.5" cy="7.5" r="1.4"/>',
    comment: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5.2A8 8 0 1 1 21 12Z"/>',
    warn: '<path d="M12 3 2.5 20h19L12 3Z"/><path d="M12 9v5M12 17h.01"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5.2l3.4 2"/>',
    edit: '<path d="M4 20h4L20 8l-4-4L4 16v4Z"/>',
    refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
    filter: '<path d="M3 5h18l-7 8v6l-4 2v-8L3 5Z"/>',
    bell: '<path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
    setting: '<circle cx="12" cy="12" r="3.2"/><path d="M12 3v2.4M12 18.6V21M3 12h2.4M18.6 12H21M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M18.4 5.6l-1.7 1.7M7.3 16.7l-1.7 1.7"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
    shop: '<path d="M3 9h18l-1.5-5H4.5L3 9Z"/><path d="M4 9v11h16V9"/><path d="M9 20v-6h6v6"/>',
    logout: '<path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4"/><path d="M10 8l-4 4 4 4M6 12h9"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.6"/><path d="m4 18 5-5 4 4 3-3 4 4"/>',
    play: '<path d="M7 4l13 8-13 8V4Z"/>',
    location: '<path d="M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11Z"/><circle cx="12" cy="10" r="2.6"/>'
  };
  function icon(name, size) {
    var p = ICONS[name] || ICONS.box;
    var s = size || 18;
    return '<svg width="' + s + '" height="' + s + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="flex:none">' + p + '</svg>';
  }

  /* ------------------------------ 提示 / 弹层 ------------------------------ */
  function toast(msg, type) {
    var wrap = document.getElementById('toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.id = 'toast-wrap';
      document.body.appendChild(wrap);
    }
    var el = document.createElement('div');
    el.className = 'toast ' + (type || '');
    el.textContent = msg;
    wrap.appendChild(el);
    setTimeout(function () {
      el.style.transition = 'opacity .25s, transform .25s';
      el.style.opacity = '0';
      el.style.transform = 'translateY(6px)';
      setTimeout(function () { el.remove(); }, 260);
    }, type === 'err' ? 2600 : 1900);
  }

  function sheet(opt) {
    var ov = document.createElement('div');
    ov.className = 'overlay ' + (opt.mid === false ? '' : 'mid') + (opt.wide ? ' admin-modal' : '');
    var actions = (opt.actions || []).map(function (a, i) {
      return '<button class="btn ' + (a.type === 'primary' ? 'primary' : a.type === 'danger' ? 'danger' : '') + '" data-idx="' + i + '">' + esc(a.text) + '</button>';
    }).join('');
    ov.innerHTML =
      '<div class="sheet" role="dialog">' +
        '<div class="sheet-hd"><div class="t">' + esc(opt.title || '') + '</div><div class="x" data-close>✕</div></div>' +
        '<div class="sheet-bd">' + (opt.body || '') + '</div>' +
        (actions ? '<div class="sheet-ft">' + actions + '</div>' : '') +
      '</div>';
    document.body.appendChild(ov);
    function close() { ov.remove(); if (opt.onClose) opt.onClose(); }
    ov.addEventListener('click', function (e) {
      if (e.target === ov || e.target.hasAttribute('data-close')) { close(); return; }
      var btn = e.target.closest('[data-idx]');
      if (btn) {
        var a = opt.actions[Number(btn.getAttribute('data-idx'))];
        if (!a) return;
        if (a.onClick) {
          var r = a.onClick(ov, close);
          if (r !== false) close();
        } else close();
      }
    });
    if (opt.onMount) opt.onMount(ov, close);
    return { el: ov, close: close };
  }

  function confirm(opt) {
    opt = typeof opt === 'string' ? { title: opt } : (opt || {});
    return new Promise(function (resolve) {
      var done = false;
      sheet({
        title: opt.title || '确认操作',
        body: '<div style="font-size:13.5px;color:var(--text-2);line-height:1.7">' + (opt.body || '') + '</div>',
        actions: [
          { text: opt.cancelText || '取消', onClick: function () { done = true; resolve(false); } },
          { text: opt.okText || '确定', type: opt.danger ? 'danger' : 'primary', onClick: function () { done = true; resolve(true); } }
        ],
        onClose: function () { if (!done) resolve(false); }
      });
    });
  }

  /* ------------------------------ 片段工厂 ------------------------------ */
  function thumb(bg, emoji, cls) {
    return '<div class="thumb ' + (cls || '') + '" style="background:linear-gradient(135deg,' + (bg || '#ffd6c2') + ',#ffffff88)">' + esc(emoji || '🛍️') + '</div>';
  }
  function empty(o) {
    o = o || {};
    return '<div class="empty">' +
      '<div class="ico">' + esc(o.icon || '🗂️') + '</div>' +
      '<div class="t">' + esc(o.title || '暂无数据') + '</div>' +
      (o.hint ? '<div class="h">' + o.hint + '</div>' : '') +
      (o.action ? '<div style="margin-top:14px">' + o.action + '</div>' : '') +
      '</div>';
  }
  function statusTag(order) {
    var s = order.status;
    var cls = { unpaid: 'warn', wait_ship: '', shipped: 'info', received: 'ok', done: 'gray', closed: 'gray' }[s] || 'gray';
    var extra = order.fullyRefunded ? ' <span class="tag danger">已全额退款</span>' : '';
    return '<span class="tag ' + cls + '">' + esc(Store.ORDER_STATUS[s] || s) + '</span>' + extra;
  }
  function asTag(as) {
    var s = as.status;
    var cls = {
      apply: 'warn', wait_send: 'info', wait_receive: 'info', refunding: 'warn',
      done: 'ok', closed: 'gray', rejected: 'gray', canceled: 'gray'
    }[s] || 'gray';
    var extra = as.restockPending ? ' <span class="tag danger">库存待回补</span>' : '';
    return '<span class="tag ' + cls + '">' + esc(Store.AS_STATUS[s] || s) + '</span>' + extra;
  }
  function stepper(qty, min, max) {
    min = min || 1;
    max = max || 999;
    return '<div class="stepper">' +
      '<button data-step="-" ' + (qty <= min ? 'disabled' : '') + '>' + icon('minus', 14) + '</button>' +
      '<span class="n" data-qty>' + qty + '</span>' +
      '<button data-step="+" ' + (qty >= max ? 'disabled' : '') + '>' + icon('plus', 14) + '</button>' +
      '</div>';
  }
  function cHeader(o) {
    o = o || {};
    return '<div class="c-head">' +
      (o.noBack ? '<div style="width:30px"></div>' : '<div class="back" data-go="' + esc(o.back || 'back') + '">' + icon('back', 20) + '</div>') +
      '<div class="title">' + esc(o.title || '') + '</div>' +
      '<div class="right">' + (o.right || '') + '</div>' +
      '</div>';
  }
  function tabs(items, active, act) {
    return '<div class="tabs-scroll">' + items.map(function (t) {
      var n = t.count ? '<span class="count"> ' + t.count + '</span>' : '';
      return '<button class="' + (t.key === active ? 'on' : '') + '" data-act="' + esc(act || 'tab') + '" data-key="' + esc(t.key) + '">' + esc(t.label) + n + '</button>';
    }).join('') + '</div>';
  }
  function alertBox(text, type) {
    return '<div class="alert ' + (type || '') + '">' + icon(type === 'err' ? 'warn' : 'bell', 15) + '<div>' + text + '</div></div>';
  }
  function uploads(n, max) {
    max = max || 6;
    var s = '';
    for (var i = 0; i < (n || 0); i++) s += '<div class="upload-slot filled">🖼️</div>';
    if ((n || 0) < max) s += '<div class="upload-slot" data-act="upload">+<br>凭证</div>';
    return '<div class="uploads">' + s + '</div>';
  }
  function starRate(n, size) {
    var s = '';
    for (var i = 1; i <= 5; i++) {
      s += '<span style="color:' + (i <= n ? '#ffb400' : '#e3e6ec') + ';font-size:' + (size || 16) + 'px">★</span>';
    }
    return s;
  }

  /* ------------------------------ 路由参数 ------------------------------ */
  function qs(hash) {
    var out = {};
    var i = String(hash || '').indexOf('?');
    if (i < 0) return out;
    String(hash).slice(i + 1).split('&').forEach(function (kv) {
      if (!kv) return;
      var p = kv.split('=');
      out[decodeURIComponent(p[0])] = decodeURIComponent(p.slice(1).join('=') || '');
    });
    return out;
  }

  window.UI = {
    esc: esc,
    yuan: yuan,
    price: price,
    time: time,
    clock: clock,
    fromNow: fromNow,
    countdown: countdown,
    icon: icon,
    toast: toast,
    sheet: sheet,
    confirm: confirm,
    thumb: thumb,
    empty: empty,
    statusTag: statusTag,
    asTag: asTag,
    stepper: stepper,
    cHeader: cHeader,
    tabs: tabs,
    alert: alertBox,
    uploads: uploads,
    starRate: starRate,
    qs: qs
  };
})();