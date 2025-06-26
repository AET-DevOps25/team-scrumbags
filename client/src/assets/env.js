(function(window) {
  window.env = window.env || {};

  // Environment variables
  window["env"]["production"] = false;
  window["env"]["apiUrl"] = "http://localhost:8080";
  window["env"]["keycloakUrl"] = "http://localhost:8081";
  window["env"]["keycloakRealm"] = "trace";
  window["env"]["keycloakClient"] = "trace-api";
  window["env"]["redirectUrl"] = window.location.origin;
  window["env"]["debug"] = true;
})(this);