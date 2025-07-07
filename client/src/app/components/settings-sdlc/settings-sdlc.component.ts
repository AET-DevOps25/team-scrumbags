import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProjectService } from '../../services/project.service';
import { User } from '../../models/user.model';
import { FormsModule } from '@angular/forms';
import { SdlcApi } from '../../services/sdlc.api';

@Component({
  selector: 'app-settings-sdlc',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './settings-sdlc.component.html',
  styleUrl: './settings-sdlc.component.scss',
})
export class SdlcSettings {
  projectService = inject(ProjectService);
  sdlcApi = inject(SdlcApi);

  showSecret = signal(false);
  initialSecret = signal('');
  secretInput = signal('');

  isLoading = signal(false);

  projectsUser = computed<User[]>(() => {
    return this.projectService.selectedProject()?.users ?? [];
  });

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProject()?.id;
      if (projectId) {
        this.isLoading.set(true);
        this.sdlcApi.getTokens(projectId).subscribe({
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
    this.sdlcApi.saveToken(projectId, this.secretInput()).subscribe({
      next: (token) => {
        if (token) {
          this.initialSecret.set(token.token);
          this.secretInput.set(token.token);
        }
      },
      complete: () => {
        this.isLoading.set(false);
      },
    });
  }
}
