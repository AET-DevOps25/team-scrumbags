(function(window) {
  window.env = window.env || {};

  // Environment variables
  window["env"]["production"] = false;
  window["env"]["apiUrl"] = "http://localhost:8080";
  window["env"]["keycloakUrl"] = "http://localhost:8081";
  window["env"]["keycloakRealm"] = "trace";
  window["env"]["keycloakUrl"] = "trace-api";
  window["env"]["debug"] = true;
})(this);