import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ProjectState } from '../states/project.state';
import { ChatApi } from './chat.api';
import { Message } from '../models/message.model';
import { UserService } from './user.service';

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private api = inject(ChatApi);
  private projectState = inject(ProjectState);
  private userService = inject(UserService);

  public loadAllMessages(projectId: string): Observable<Message[]> {
    const user = this.userService.getSignedInUser();
    if (!user) {
      throw new Error('User not signed in');
    }
    return this.api.getChatMessages(projectId, user.id).pipe(
      tap((messages) => {
        this.projectState.setMessages(projectId, messages);
      })
    );
  }

  public sendMessage(
    projectId: string,
    message: string
  ): Observable<Message[]> {
    const user = this.userService.getSignedInUser();
    if (!user) {
      throw new Error('User not signed in');
    }
    return this.api.sendMessage(projectId, user.id, message).pipe(
      tap((messages) => {
        this.projectState.addMessages(projectId, messages);
      })
    );
  }
}
