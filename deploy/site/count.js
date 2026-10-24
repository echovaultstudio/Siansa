// Shows how many beta testers are in. The page works without it.
fetch('/signup/count', { cache: 'no-store' })
  .then(function (r) { return r.json(); })
  .then(function (d) {
    var el = document.getElementById('beta-count');
    if (!el) return;
    var text = d.approved + ' of ' + d.limit + ' testers are in';
    if (d.waiting > 0) text += ', ' + d.waiting + ' waiting';
    text += '.';
    if (d.taken >= d.limit) {
      text += ' The beta is full. You can still sign up: you\'ll be put on the waiting list and approved automatically when a place is free.';
    }
    el.textContent = text;
  })
  .catch(function () {});
