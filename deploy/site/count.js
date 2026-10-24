// Shows how many beta places are taken. The page works without it.
fetch('/signup/count', { cache: 'no-store' })
  .then(function (r) { return r.json(); })
  .then(function (d) {
    var el = document.getElementById('beta-count');
    if (!el) return;
    var full = d.taken >= d.limit;
    el.textContent = d.taken + ' of ' + d.limit + ' beta places taken' +
      (d.waiting > 0 ? ', ' + d.waiting + ' waiting' : '') + '.' +
      (full ? ' The beta is full. You can still sign up: you\'ll be put on the waiting list and approved automatically when a place is free.' : '');
  })
  .catch(function () {});
