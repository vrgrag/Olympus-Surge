(function(){
  if(window.__delphi_veil_tail) return;
  window.__delphi_veil_tail = true;
  var run = function(){
    if(typeof window.__delphi_veil_apply === 'function') window.__delphi_veil_apply();
  };
  ['pushState','replaceState'].forEach(function(name){
    var orig = history[name];
    history[name] = function(){
      var r = orig.apply(this, arguments);
      setTimeout(run, 80);
      setTimeout(run, 400);
      return r;
    };
  });
  window.addEventListener('popstate', function(){ setTimeout(run, 80); });
  setInterval(run, 2500);
})();
