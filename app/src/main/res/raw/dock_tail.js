(function(){
  if(window.__lyre_dock_tail) return;
  window.__lyre_dock_tail = true;
  var pending = false;
  var report = function(){
    if(typeof window.__lyre_ping === 'function') window.__lyre_ping();
  };
  var soon = function(){
    if(pending) return;
    pending = true;
    var run = function(){ pending = false; report(); };
    if(window.requestAnimationFrame){ requestAnimationFrame(run); }
    else { setTimeout(run, 16); }
  };
  var kick = function(){
    report();
    setTimeout(report, 200);
  };
  if(window.visualViewport){
    window.visualViewport.addEventListener('resize', soon);
    window.visualViewport.addEventListener('scroll', soon);
  }
  document.addEventListener('focusin', kick, true);
})();
