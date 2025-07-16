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
        for (const message of messages) {
          // trigger polling for each message that is still loading
          if (message.loading) {
            this.pollMessage(projectId, message.id, user.id);
          }
        }

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

    return this.api.sendMessage(projectId, message, user.id).pipe(
      tap((messages) => {
        for (const msg of messages) {
          if (msg.loading) {
            this.pollMessage(projectId, msg.id, user.id);
          }
        }

        this.projectState.updateMessages(projectId, messages);
      })
    );
  }

  private async pollMessage(
    projectId: string,
    messageId: string,
    userId: string,
    count = 0
  ) {
    if (count >= 10) {
      console.warn(
        `Polling stopped for message ${messageId} after 10 attempts.`
      );
      return;
    }

    // Poll the note every 5 seconds until it is no longer loading
    await new Promise((resolve) => setTimeout(resolve, 5000));

    this.api
      .getChatMessageById(projectId, messageId, userId)
      .subscribe((message) => {
        if (message.loading) {
          this.pollMessage(projectId, messageId, userId, count + 1);
          return;
        }

        this.projectState.updateMessages(projectId, [message]);
      });
  }
}
