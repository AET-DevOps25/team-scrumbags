(function(window) {
  window.env = window.env || {};

  // Environment variables
  window["env"]["production"] = false;
  window["env"]["apiUrl"] = "http://localhost:8080";
  window["env"]["debug"] = true;
})(this);