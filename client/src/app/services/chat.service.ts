import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { MeetingNote } from '../models/meeting-note.model';
import { ProjectState } from '../states/project.state';
import { ReportApi } from './report.api';
import { ChatApi } from './chat.api';
import { Message } from '../models/message.model';

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private api = inject(ChatApi);
  private projectState = inject(ProjectState);

  public loadAllMessages(projectId: string): Observable<Message[]> {
    return this.api.getChatMessages(projectId).pipe(
      tap((messages) => {
        this.projectState.setMessages(projectId, messages);
      })
    );
  }

  public sendMessage(
    projectId: string,
    message: string
  ): Observable<Message[]> {
    return this.api.sendMessage(projectId, message)
      .pipe(
        tap((messages) => {
          this.projectState.addMessages(projectId, messages);
        })
      );
  }
}
