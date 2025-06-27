declare global {
  interface Window {
    env: {
      production?: boolean;
      projectManagementUrl?: string;
      sdlcUrl?: string;
      meetingNotesUrl?: string;
      communicationUrl?: string;
      genAiUrl?: string;
      keycloakUrl?: string;
      keycloakRealm?: string;
      keycloakClient?: string;
      redirectUrl?: string;
      debug?: boolean;
    };
  }
}

export const environment = {
  production: window.env?.production ?? true,
  projectManagementUrl:
    window.env?.projectManagementUrl ??
    `${window.location.origin}/api/project-management`,
  sdlcUrl: window.env?.sdlcUrl ?? `${window.location.origin}/api/sdlc`,
  meetingNotesUrl:
    window.env?.meetingNotesUrl ??
    `${window.location.origin}/api/meeting-notes`,
  communicationUrl:
    window.env?.communicationUrl ??
    `${window.location.origin}/api/communication`,
  genAiUrl: window.env?.genAiUrl ?? `${window.location.origin}/api/gen-ai`,
  keycloakUrl: window.env?.keycloakUrl ?? `${window.location.origin}/keycloak`,
  keycloakRealm: window.env?.keycloakRealm ?? 'trace',
  keycloakClient: window.env?.keycloakClient ?? 'trace-api',
  redirectUrl: window.env?.redirectUrl ?? window.location.origin,
  debug: window.env?.debug ?? false,
};
