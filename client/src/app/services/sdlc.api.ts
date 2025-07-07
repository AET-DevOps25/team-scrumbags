import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../environment';
import { Message } from '../models/message.model';
import { SdlcToken } from '../models/sdlc-token.model';
import { handleError } from './api-utils';
import { SdlcUserMapping } from '../models/sdlc-users.model';

@Injectable({
  providedIn: 'root',
})
export class SdlcApi {
  private http = inject(HttpClient);

  getTokens(projectId: string): Observable<SdlcToken[]> {
    return this.http
      .get<SdlcToken[]>(`${environment.sdlcUrl}/projects/${projectId}/token`)
      .pipe(catchError(handleError('Error fetching chat messages')));
  }

  saveToken(projectId: string, token: string): Observable<SdlcToken> {
    return this.http
      .post<SdlcToken>(
        `${environment.sdlcUrl}/projects/${projectId}/token`,
        token
      )
      .pipe(catchError(handleError('Error sending message')));
  }

  getUserMappings(projectId: string): Observable<SdlcUserMapping[]> {
    return this.http
      .get<SdlcUserMapping[]>(
        `${environment.sdlcUrl}/projects/${projectId}/users`
      )
      .pipe(catchError(handleError('Error fetching chat messages')));
  }

  saveUserMapping(
    projectId: string,
    userMapping: SdlcUserMapping
  ): Observable<SdlcUserMapping> {
    return this.http
      .post<SdlcUserMapping>(
        `${environment.sdlcUrl}/projects/${projectId}/users`,
        userMapping
      )
      .pipe(catchError(handleError('Error sending message')));
  }
}
