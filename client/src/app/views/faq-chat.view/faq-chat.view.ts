import {
  Component,
  computed,
  effect,
  inject,
  signal,
  ViewChild,
  ElementRef,
  AfterViewInit,
} from '@angular/core';
import { Message } from '../../models/message.model';
import { ProjectService } from '../../services/project.service';
import { ChatService } from '../../services/chat.service';
import { catchError, EMPTY, finalize } from 'rxjs';

import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MarkdownComponent } from 'ngx-markdown';
import { MatSnackBar } from '@angular/material/snack-bar';

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
    MarkdownComponent,
  ],
  templateUrl: './faq-chat.view.html',
  styleUrl: './faq-chat.view.scss',
})
export class FaqChatView implements AfterViewInit {
  @ViewChild('scrollContainer') scrollContainer?: ElementRef<HTMLDivElement>;
  private projectService = inject(ProjectService);
  private chatService = inject(ChatService);
  private snackBar;

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
  isLastMessageLoading = computed(() => {
    const loadingMessages = this.messages().filter((msg) => msg.loading);
    return loadingMessages.length > 0;
  });

  constructor() {
    this.snackBar = inject(MatSnackBar);
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.isLoading.set(true);
        this.chatService
          .loadAllMessages(projectId)
          .pipe(
            catchError((error) => {
              this.snackBar.open(
                `Error loading messages: ${error.message}`,
                'Close',
                { duration: 3000 }
              );
              return EMPTY;
            }),
            finalize(() => {
              this.isLoading.set(false);
            })
          )
          .subscribe();
        return;
      }
    });

    // Auto-scroll effect
    effect(() => {
      this.messages();
      setTimeout(() => {
        this.scrollToBottom();
      });
    });
  }

  ngAfterViewInit() {
    this.scrollToBottom();
  }

  private scrollToBottom() {
    if (this.scrollContainer?.nativeElement) {
      this.scrollContainer.nativeElement.scrollTop =
        this.scrollContainer.nativeElement.scrollHeight;
    }
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
        catchError((error) => {
          this.snackBar.open(
            `Error sending message: ${error.message}`,
            'Close',
            { duration: 3000 }
          );
          return EMPTY;
        }),
        finalize(() => {
          this.inputMessage.set('');
          this.isLoading.set(false);
        })
      )
      .subscribe();
  }
}
