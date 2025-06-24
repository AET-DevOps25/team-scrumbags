(function (window) {
  window.env = window.env || {};

  // Environment variables
  window["env"]["production"] = "${CLIENT_PRODUCTION}";
  window["env"]["apiUrl"] = "${API_URL}";
  window["env"]["keycloakUrl"] = "${KEYCLOAK_URL}";
  window["env"]["keycloakRealm"] = "${KEYCLOAK_REALM}";
  window["env"]["keycloakClient"] = "${KEYCLOAK_CLIENT_ID}";
  window["env"]["redirectUrl"] = "${KEYCLOAK_REDIRECT_URL}";
  window["env"]["debug"] = "${DEBUG}";
})(this);
