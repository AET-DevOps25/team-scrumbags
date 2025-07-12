(function(window) {
  window.env = window.env || {};

  // Environment variables
  window["env"]["production"] = true;
  window["env"]["projectManagementUrl"] = "http://localhost:8080";
  window["env"]["sdlcUrl"] = "http://localhost:8081";
  window["env"]["meetingNotesUrl"] = "http://localhost:8083";
  window["env"]["communicationUrl"] = "http://localhost:8082";
  window["env"]["genAiUrl"] = "http://localhost:4242";
  window["env"]["keycloakUrl"] = "http://localhost:7999";
  window["env"]["keycloakRealm"] = "trace";
  window["env"]["keycloakClient"] = "trace-api";
  window["env"]["debug"] = true;
})(this);