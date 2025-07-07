import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ProjectService } from '../../services/project.service';
import { User } from '../../models/user.model';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-settings-sdlc',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './settings-sdlc.component.html',
  styleUrl: './settings-sdlc.component.scss',
})
export class SdlcSettings {
  showSecret = signal(false);
  initialSecret = signal('');
  secretInput = signal('');
  projectService = inject(ProjectService);

  projectsUser = computed<User[]>(() => {
    return this.projectService.selectedProject()?.users ?? [];
  });

  constructor() {
    effect(() => {
      console.log('', this.secretInput());
    });
  }

  onSubmit() {
    console.log('Secret submitted:', this.secretInput());
    // TODO: Implement actual submission logic
  }
}
