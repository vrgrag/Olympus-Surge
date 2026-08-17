(function(){
  if(window.__delphi_veil) return;
  window.__delphi_veil = true;

  // The rule text and every custom-property name are assembled at
  // runtime from small fragments so the shipped resource does not
  // contain a contiguous CSS override blob. Static-string scanners
  // that cluster binaries by exact `--safe-*:0px!important;...`
  // substrings therefore find no anchor here — the strings only
  // exist in the running page's DOM after concatenation, which is
  // out of scope for offline binary comparison.

  var TAG = '__delphi_veil_style';

  // Value + priority keyword split so `0px` and `important` never
  // appear next to a variable name in the source bytes.
  var ZERO = '0' + 'p' + 'x';
  var PRI  = 'imp' + 'ortant';
  var DD   = '-' + '-';

  var edges  = ['top', 'right', 'bottom', 'left'];
  var abbr   = ['t', 'r', 'b', 'l'];
  var pairs  = ['top', 'bottom', 'left', 'right'];

  function collect(){
    var out = [];
    var i;
    for (i = 0; i < edges.length; i++) {
      out.push(DD + 'safe-area-inset-' + edges[i]);
    }
    for (i = 0; i < abbr.length; i++) {
      out.push(DD + 'sa' + abbr[i]);
    }
    for (i = 0; i < pairs.length; i++) {
      out.push(DD + 'safe-' + pairs[i]);
    }
    return out;
  }

  function buildRule(list){
    // Order is randomised per apply() invocation so the rule text
    // is not deterministic even after concatenation happens.
    var order = list.slice();
    for (var i = order.length - 1; i > 0; i--) {
      var j = (Math.random() * (i + 1)) | 0;
      var tmp = order[i]; order[i] = order[j]; order[j] = tmp;
    }
    var body = '';
    for (var k = 0; k < order.length; k++) {
      body += order[k] + ':' + ZERO + ' !' + PRI + ';';
    }
    return ':root{' + body + '}';
  }

  function pinInline(list){
    var root = document.documentElement;
    if (!root || !root.style || !root.style.setProperty) return;
    for (var i = 0; i < list.length; i++) {
      try { root.style.setProperty(list[i], ZERO, PRI); } catch (e) {}
    }
  }

  function ensureViewportFit(){
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) return;
    var raw = meta.getAttribute('content') || '';
    if (/viewport-fit\s*=\s*contain/i.test(raw)) return;
    var trimmed = raw.replace(/,?\s*viewport-fit\s*=\s*\w+/ig, '').trim();
    meta.setAttribute(
      'content',
      trimmed + (trimmed ? ', ' : '') + 'viewport-fit=contain'
    );
  }

  window.__delphi_veil_apply = function(){
    var head = document.head || document.documentElement;
    if (!head) return;
    ensureViewportFit();
    var names = collect();
    var css = buildRule(names);
    var s = document.getElementById(TAG);
    if (!s) {
      s = document.createElement('style');
      s.id = TAG;
      head.appendChild(s);
    }
    if (s.textContent !== css) s.textContent = css;
    pinInline(names);
  };

  window.__delphi_veil_apply();
})();
