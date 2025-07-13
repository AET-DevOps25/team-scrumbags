import { Component, computed, effect, inject, signal } from '@angular/core';
import { User } from '../../../models/user.model';
import { ProjectService } from '../../../services/project.service';
import { TranscriptionApi } from '../../../services/transcription.api';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, finalize, tap } from 'rxjs';
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
    const speakerMap = new Map(
      this.speakers().map((s) => [s.userId, s.sampleExtension])
    );
    return users.map((user) => ({
      user,
      fileName: speakerMap.has(user.id)
        ? `sample.${speakerMap.get(user.id)}`
        : null,
    }));
  });

  isLoading = signal(false);
  isUploading = signal<string | null>(null); // store userId of user being uploaded for

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
          return [];
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
    this.transcriptionApi
      .assignVoiceSample(projectId, user.id, user.username, file)
      .pipe(
        finalize(() => this.isUploading.set(null)),
        tap((savedSpeaker) => {
          this.speakers.update((currentSpeakers) => {
            const index = currentSpeakers.findIndex(
              (s) => s.userId === savedSpeaker.userId
            );
            if (index > -1) {
              const updatedSpeakers = [...currentSpeakers];
              updatedSpeakers[index] = savedSpeaker;
              return updatedSpeakers;
            }
            return [...currentSpeakers, savedSpeaker];
          });
          this.snackBar.open(`Voice sample for user updated.`, 'Close', {
            duration: 3000,
          });
        }),
        catchError((error) => {
          this.snackBar.open(
            `Error uploading file: ${error.message}`,
            'Close',
            { duration: 3000 }
          );
          return [];
        })
      )
      .subscribe();
  }
}
