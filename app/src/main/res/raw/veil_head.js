(function(){
  if(window.__delphi_veil) return;
  window.__delphi_veil = true;
  var ID = '__delphi_veil_style';
  var CSS = ':root{'+
    '--safe-area-inset-top:0px!important;'+
    '--safe-area-inset-right:0px!important;'+
    '--safe-area-inset-bottom:0px!important;'+
    '--safe-area-inset-left:0px!important;'+
    '--sat:0px!important;--sar:0px!important;--sab:0px!important;--sal:0px!important;'+
    '--safe-top:0px!important;--safe-bottom:0px!important;'+
    '--safe-left:0px!important;--safe-right:0px!important;'+
  '}';
  window.__delphi_veil_apply = function(){
    var head = document.head || document.documentElement;
    if(!head) return;
    var meta = document.querySelector('meta[name="viewport"]');
    if(meta && !/viewport-fit\s*=\s*contain/i.test(meta.getAttribute('content')||'')){
      var c = (meta.getAttribute('content')||'')
        .replace(/,?\s*viewport-fit\s*=\s*\w+/ig,'')
        .trim();
      meta.setAttribute('content', c + (c ? ', ' : '') + 'viewport-fit=contain');
    }
    var s = document.getElementById(ID);
    if(!s){ s = document.createElement('style'); s.id = ID; head.appendChild(s); }
    if(s.textContent !== CSS) s.textContent = CSS;
  };
  window.__delphi_veil_apply();
})();
