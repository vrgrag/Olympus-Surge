(function(){
  if(window.__lyre_dock) return;
  window.__lyre_dock = true;
  window.__lyre_editable = function(el){
    if(!el) return false;
    if(el.tagName === 'INPUT'){
      var t = (el.type || 'text').toLowerCase();
      var skip = ['checkbox','radio','button','submit','reset','file','range','image','color'];
      return skip.indexOf(t) < 0;
    }
    return el.tagName === 'TEXTAREA' || el.isContentEditable === true;
  };
  window.__lyre_box = function(el){
    if(el.isContentEditable){
      try{
        var sel = window.getSelection();
        if(sel && sel.rangeCount){
          var rect = sel.getRangeAt(0).getBoundingClientRect();
          if(rect && rect.height > 0) return rect;
        }
      }catch(e){}
    }
    return el.getBoundingClientRect();
  };
  window.__lyre_ping = function(){
    var el = document.activeElement;
    if(!window.__lyre_editable(el)) return;
    var r = window.__lyre_box(el);
    var vv = window.visualViewport;
    var lift = vv ? vv.offsetTop : 0;
    var px = (window.devicePixelRatio || 1) * ((vv && vv.scale) ? vv.scale : 1);
    try{
      if(window.NectarInput && typeof window.NectarInput.focus === 'function'){
        window.NectarInput.focus((r.top - lift) * px, (r.bottom - lift + 10) * px);
      }
    }catch(e){}
  };
})();
