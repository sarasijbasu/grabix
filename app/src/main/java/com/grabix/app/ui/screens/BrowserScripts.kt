package com.grabix.app.ui.screens

/** JavaScript injected into pages before they run (document-start). */
object BrowserScripts {

    /**
     * YouTube: strips ad data out of player responses before the player sees it, hides ad
     * slots, and fast-forwards / skips any ad that still gets through.
     */
    val YOUTUBE_ADBLOCK = """
(function () {
  if (window.__gxAdblock) return;
  window.__gxAdblock = true;

  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots'];
  function clean(o) {
    try {
      if (!o || typeof o !== 'object') return o;
      for (var i = 0; i < AD_KEYS.length; i++) { if (o[AD_KEYS[i]] !== undefined) delete o[AD_KEYS[i]]; }
      if (o.playerResponse) clean(o.playerResponse);
      if (o.response && o.response.playerResponse) clean(o.response.playerResponse);
    } catch (e) {}
    return o;
  }

  var parse = JSON.parse;
  JSON.parse = function () { return clean(parse.apply(this, arguments)); };

  try {
    var json = Response.prototype.json;
    Response.prototype.json = function () { return json.apply(this, arguments).then(clean); };
  } catch (e) {}

  try {
    var ipr;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      get: function () { return ipr; },
      set: function (v) { ipr = clean(v); },
      configurable: true
    });
  } catch (e) {}

  function addStyle() {
    if (document.getElementById('gx-adblock-css')) return;
    var s = document.createElement('style');
    s.id = 'gx-adblock-css';
    s.textContent = [
      'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-slot',
      'ytm-ad-slot-renderer', 'ad-slot-renderer', 'ytm-statement-banner-renderer',
      'ytd-ad-slot-renderer', 'ytd-display-ad-renderer', 'ytd-promoted-sparkles-web-renderer',
      '#player-ads', '#masthead-ad', '.ytp-ad-overlay-container', '.ytp-ad-text-overlay',
      '.video-ads', '.ytp-ad-module', 'ytm-mealbar-promo-renderer', '.ad-container'
    ].join(',') + '{display:none !important}';
    (document.head || document.documentElement).appendChild(s);
  }
  addStyle();
  document.addEventListener('DOMContentLoaded', addStyle);

  var mutedByUs = false, prevMuted = false;
  setInterval(function () {
    try {
      var p = document.querySelector('.html5-video-player');
      var v = document.querySelector('video.html5-main-video') || document.querySelector('video');
      var ad = !!(p && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting')));
      if (ad && v) {
        if (!mutedByUs) { prevMuted = v.muted; mutedByUs = true; }
        v.muted = true;
        if (isFinite(v.duration) && v.duration > 0) v.currentTime = v.duration;
      } else if (mutedByUs && v) {
        v.muted = prevMuted;
        mutedByUs = false;
      }
      var sel = '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-container button, .ytp-ad-overlay-close-button';
      var btns = document.querySelectorAll(sel);
      for (var i = 0; i < btns.length; i++) { btns[i].click(); }
    } catch (e) {}
  }, 250);
})();
"""

    /**
     * Keeps pages believing they are still visible when the app is in the background (so web
     * players don't pause themselves) and reports whether any media is playing to the app.
     */
    val BACKGROUND_PLAY = """
(function () {
  if (window.__gxBg) return;
  window.__gxBg = true;
  function def(o, k, v) { try { Object.defineProperty(o, k, { get: function () { return v; }, configurable: true }); } catch (e) {} }
  def(document, 'hidden', false);
  def(document, 'webkitHidden', false);
  def(document, 'visibilityState', 'visible');
  def(document, 'webkitVisibilityState', 'visible');
  function stop(e) { e.stopImmediatePropagation(); }
  document.addEventListener('visibilitychange', stop, true);
  document.addEventListener('webkitvisibilitychange', stop, true);

  var last = null;
  function report() {
    try {
      var list = document.querySelectorAll('video,audio'), playing = false;
      for (var i = 0; i < list.length; i++) { if (!list[i].paused && !list[i].ended && list[i].readyState > 2) playing = true; }
      if (playing !== last) { last = playing; if (window.GrabixBridge) window.GrabixBridge.media(playing); }
    } catch (e) {}
  }
  ['play', 'playing', 'pause', 'ended', 'emptied', 'waiting'].forEach(function (ev) {
    document.addEventListener(ev, function () { setTimeout(report, 150); }, true);
  });
  setInterval(report, 2000);
})();
"""

    const val PAUSE_ALL = "(function(){var l=document.querySelectorAll('video,audio');for(var i=0;i<l.length;i++){try{l[i].pause()}catch(e){}}})();"
    const val PLAY_FIRST = "(function(){var l=document.querySelectorAll('video,audio');if(l.length){try{l[0].play()}catch(e){}}})();"
}
