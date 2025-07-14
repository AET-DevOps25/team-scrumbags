import { Component, computed, effect, inject, signal } from '@angular/core';
import { User } from '../../../models/user.model';
import { ProjectService } from '../../../services/project.service';
import { TranscriptionApi } from '../../../services/transcription.api';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, EMPTY, finalize, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranscriptionUserMapping } from '../../../models/transcription-users.model';

interface UserVoiceSample {
  user: User;
  fileName: string | null;
}

@Component({
  selector: 'app-settings-transcription-users',
  standalone: true,
  imports: [
    CommonModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './settings-transcription-users.component.html',
  styleUrl: './settings-transcription-users.component.scss',
})
export class SettingsTranscriptionUsersComponent {
  private projectService = inject(ProjectService);
  private transcriptionApi = inject(TranscriptionApi);
  private snackBar = inject(MatSnackBar);

  projectsUser = computed<User[]>(
    () => this.projectService.selectedProject()?.users ?? []
  );
  private speakers = signal<TranscriptionUserMapping[]>([]);

  userVoiceSamples = computed<UserVoiceSample[]>(() => {
    const users = this.projectsUser();
    const speakers = this.speakers() ?? [];
    const speakerMap = new Map(
      speakers.map((s) => [s.userId, s.originalFileName || `sample.${s.sampleExtension}`])
    );
    return users.map((user) => ({
      user,
      fileName: speakerMap.has(user.id)
        ? speakerMap.get(user.id)!
        : null,
    }));
  });

  isLoading = signal(false);
  isUploading = signal<string | null>(null);

  constructor() {
    effect(
      () => {
        const projectId = this.projectService.selectedProjectId();
        if (projectId) {
          this.loadVoiceSamples(projectId);
        } else {
          this.speakers.set([]);
        }
      },
      { allowSignalWrites: true }
    );
  }

  private loadVoiceSamples(projectId: string) {
    this.isLoading.set(true);
    this.transcriptionApi
      .getSpeakers(projectId)
      .pipe(
        finalize(() => this.isLoading.set(false)),
        catchError((error) => {
          this.snackBar.open(
            `Error loading voice samples: ${error.message}`,
            'Close',
            { duration: 3000 }
          );
          return EMPTY;
        })
      )
      .subscribe((speakers) => {
        this.speakers.set(speakers);
      });
  }

  onFileSelected(event: Event, user: User) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      this.uploadVoiceSample(user, file);
    }
  }

  private uploadVoiceSample(user: User, file: File) {
    const projectId = this.projectService.selectedProjectId();
    if (!projectId) {
      this.snackBar.open('No project selected.', 'Close', { duration: 3000 });
      return;
    }

    this.isUploading.set(user.id);

    const currentSpeakers = this.speakers();
    const speakerExists = currentSpeakers && currentSpeakers.some((s) => s.userId === user.id);

    const upload$ = speakerExists
      ? this.transcriptionApi.updateSpeaker(projectId, user.id, user.username, file)
      : this.transcriptionApi.createSpeaker(projectId, user.id, user.username, file);

    upload$
      .pipe(
        finalize(() => this.isUploading.set(null)),
        tap((savedSpeaker) => {
          this.speakers.update((currentSpeakers) => {
            if (!currentSpeakers) {
              return [savedSpeaker];
            }
            const index = currentSpeakers.findIndex(
              (s) => s.userId === savedSpeaker.userId
            );
            if (index > -1) {
              const updatedSpeakers = [...currentSpeakers];
              updatedSpeakers[index] = savedSpeaker;
              return updatedSpeakers;
            } else {
              return [...currentSpeakers, savedSpeaker];
            }
          });
          this.snackBar.open('Voice sample uploaded successfully.', 'Close', {
            duration: 3000,
          });
        }),
        catchError((error: Error) => {
          this.snackBar.open(`Error uploading file: ${error.message}`, 'Close', {
            duration: 5000,
          });
          return EMPTY;
        })
      )
      .subscribe();
  }
}
