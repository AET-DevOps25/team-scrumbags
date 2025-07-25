import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import {
  provideKeycloak,
  includeBearerTokenInterceptor,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  createInterceptorCondition,
  IncludeBearerTokenCondition,
} from 'keycloak-angular';
import { MARKED_OPTIONS, provideMarkdown } from 'ngx-markdown';

import { routes } from './app.routes';
import { environment } from './environment';

// Configure which URLs should include the Bearer token
const urlCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: new RegExp(
    `^(${environment.projectManagementUrl}|${environment.sdlcUrl}|${environment.meetingNotesUrl}|${environment.communicationUrl}|${environment.genAiUrl})(/.*)?$`,
    'i'
  ),
  bearerPrefix: 'Bearer',
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideAnimationsAsync(),
    provideKeycloak({
      config: {
        url: window.env?.keycloakUrl || 'http://localhost:8081',
        realm: window.env?.keycloakRealm || 'trace',
        clientId: window.env?.keycloakClient || 'trace-api',
      },
      initOptions: {
        onLoad: 'login-required',
        checkLoginIframe: false,
        flow: 'standard',
      },
    }),
    {
      provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
      useValue: [urlCondition],
    },
    provideRouter(routes),
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
    provideMarkdown({
      markedOptions: {
        provide: MARKED_OPTIONS,
        useValue: {
          gfm: true,
          breaks: true,
          pedantic: false,
        },
      },
    }),
  ],
};
