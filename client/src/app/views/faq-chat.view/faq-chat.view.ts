import {
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { Message } from '../../models/message.model';
import { ProjectService } from '../../services/project.service';
import { ChatService } from '../../services/chat.service';
import { finalize } from 'rxjs';

import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'app-faq-chat',
  imports: [
    FormsModule,
    MatInputModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIcon,
    MatProgressSpinnerModule,
    DatePipe,
  ],
  templateUrl: './faq-chat.view.html',
  styleUrl: './faq-chat.view.scss',
})
export class FaqChatView {
  private projectService = inject(ProjectService);
  private chatService = inject(ChatService);

  messages = computed<Message[]>(() => {
    const project = this.projectService.selectedProject();
    return project && project.messages
      ? Array.from(project?.messages.values()).sort(
          (a, b) => a.timestamp.getTime() - b.timestamp.getTime()
        )
      : [];
  });

  inputMessage = signal('');
  isLoading = signal(false);

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.isLoading.set(true);
        this.chatService
          .loadAllMessages(projectId)
          .pipe(
            finalize(() => {
              this.isLoading.set(false);
            })
          )
          .subscribe();
        return;
      }

      const messages = this.messages();
      if (messages.length > 0) {
        this.isLoading.set(messages.at(messages.length - 1)?.loading ?? false);
      }
    });
  }

  sendMessage(content: string) {
    const projectId = this.projectService.selectedProjectId();
    if (!projectId || content.trim() === '') {
      return;
    }

    this.isLoading.set(true);
    this.chatService
      .sendMessage(projectId, content)
      .pipe(
        finalize(() => {
          this.inputMessage.set('');
          this.isLoading.set(false);
        })
      )
      .subscribe();
  }
}
