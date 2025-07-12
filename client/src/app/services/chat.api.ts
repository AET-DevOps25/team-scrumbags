import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../environment';
import { Message } from '../models/message.model';
import { handleError } from './api-utils';

@Injectable({
  providedIn: 'root',
})
export class ChatApi {
  private http = inject(HttpClient);

  getChatMessages(projectId: string): Observable<Message[]> {
    return this.http
      .get<Message[]>(`${environment.genAiUrl}/projects/${projectId}/chat`)
      .pipe(
        map((msg) => {
          for (const m of msg) {
            m.timestamp = new Date(m.timestamp);
          }
          return msg;
        }),
        catchError(handleError('Error fetching chat messages'))
      );
  }

  sendMessage(projectId: string, message: string): Observable<Message[]> {
    return this.http
      .post<Message[]>(`${environment.genAiUrl}/projects/${projectId}/chat`, {
        message: message,
      })
      .pipe(
        map((msg) => {
          for (const m of msg) {
            m.timestamp = new Date(m.timestamp);
          }
          return msg;
        }),
        catchError(handleError('Error sending message'))
      );
  }
}
