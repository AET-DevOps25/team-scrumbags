import { Component, effect, inject, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProjectService } from '../../../services/project.service';
import { FormsModule } from '@angular/forms';
import { SdlcApi } from '../../../services/sdlc.api';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, finalize } from 'rxjs/operators';
import { EMPTY, pipe } from 'rxjs';

@Component({
  selector: 'app-settings-sdlc-token',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './settings-sdlc-token.component.html',
  styleUrl: './settings-sdlc-token.component.scss',
})
export class SettingsSdlcTokenComponent {
  private projectService = inject(ProjectService);
  private sdlcApi = inject(SdlcApi);
  private snackBar;

  showSecret = signal(false);
  initialSecret = signal('');
  secretInput = signal('');

  isLoading = signal(false);
  confirmation = signal(false);

  constructor() {
    this.snackBar = inject(MatSnackBar);
    effect(() => {
      const projectId = this.projectService.selectedProject()?.id;
      if (projectId) {
        this.isLoading.set(true);
        this.sdlcApi
          .getTokens(projectId)
          .pipe(
            catchError((error) => {
              this.snackBar.open(
                `Error fetching tokens: ${error.message}`,
                'Close',
                {
                  duration: 3000,
                }
              );
              return EMPTY;
            })
          )
          .subscribe({
            next: (tokens) => {
              if (tokens.length > 0) {
                this.initialSecret.set(tokens[0].token);
                this.secretInput.set(tokens[0].token);
              } else {
                this.initialSecret.set('');
                this.secretInput.set('');
              }
            },
            complete: () => {
              this.isLoading.set(false);
            },
          });
      }
    });
  }

  onSubmit() {
    const projectId = this.projectService.selectedProject()?.id;
    if (!projectId) {
      console.error('No project selected');
      return;
    }

    this.isLoading.set(true);
    this.sdlcApi
      .saveToken(projectId, this.secretInput())
      .pipe(
        catchError((error) => {
          this.snackBar.open(`Error saving token: ${error.message}`, 'Close', {
            duration: 3000,
          });
          return EMPTY;
        }),
        finalize(() => {
          this.isLoading.set(false);
        })
      )
      .subscribe({
        next: (token) => {
          if (token) {
            this.initialSecret.set(token.token);
            this.secretInput.set(token.token);
            this.confirmation.set(true);
          }
        },
      });
  }
}
