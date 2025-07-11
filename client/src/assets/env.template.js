(function (window) {
  window.env = window.env || {};

  // Environment variables
  window["env"]["production"] = "${CLIENT_PRODUCTION}";
  window["env"]["projectManagementUrl"] = "${PROJECT_MANAGEMENT_URL}";
  window["env"]["sdlcUrl"] = "${SDLC_URL}";
  window["env"]["meetingNotesUrl"] = "${MEETING_NOTES_URL}";
  window["env"]["communicationUrl"] = "${COMMUNICATION_URL}";
  window["env"]["genAiUrl"] = "${GEN_AI_URL}";
  window["env"]["keycloakUrl"] = "${KEYCLOAK_URL}";
  window["env"]["keycloakRealm"] = "${KEYCLOAK_REALM}";
  window["env"]["keycloakClient"] = "${KEYCLOAK_CLIENT}";
  window["env"]["debug"] = "${DEBUG}";
})(this);
