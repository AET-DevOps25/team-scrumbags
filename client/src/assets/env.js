(function(window) {
  window.env = window.env || {};

  // Environment variables
  window["env"]["production"] = true;
  window["env"]["projectManagementUrl"] = "http://localhost:8080";
  window["env"]["sdlcUrl"] = "http://localhost:8081";
  window["env"]["meetingNotesUrl"] = "http://localhost:3030";
  window["env"]["communicationUrl"] = "http://localhost/api/communication";
  window["env"]["genAiUrl"] = "http://localhost:3031";
  window["env"]["keycloakUrl"] = "http://localhost:7999";
  window["env"]["keycloakRealm"] = "trace";
  window["env"]["keycloakClient"] = "trace-api";
  window["env"]["debug"] = true;
})(this);