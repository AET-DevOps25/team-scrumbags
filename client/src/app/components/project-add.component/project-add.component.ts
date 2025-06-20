import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ProjectApi } from '../../services/project.api';
import { Project } from '../../models/project.model';
import { ProjectState } from '../../states/project.state';

@Component({
  selector: 'project-add',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './project-add.component.html',
  styleUrl: './project-add.component.scss',
})
export class ProjectAddDialog {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<ProjectAddDialog>);
  private api = inject(ProjectApi);
  private state = inject(ProjectState);

  readonly isSubmitting = signal<boolean>(false);

  projectForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
    description: [''],
  });

  onSubmit(): void {
    if (this.projectForm.valid) {
      this.isSubmitting.set(true);
      const formValue = this.projectForm.value;

      this.api.createProject(formValue as Project).subscribe({
        next: (newProject) => {
          this.isSubmitting.set(false);
          this.state.setProjectById(newProject.id, newProject);
          this.dialogRef.close();
        },
        error: (error) => {
          this.isSubmitting.set(false);
          console.error('Error creating project:', error);
        },
      });
    } else {
      this.projectForm.markAllAsTouched();
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  getErrorMessage(fieldName: string): string {
    const field = this.projectForm.get(fieldName);
    if (field?.hasError('required')) {
      return `${
        fieldName.charAt(0).toUpperCase() + fieldName.slice(1)
      } is required`;
    }
    if (field?.hasError('minlength')) {
      const minLength = field.getError('minlength').requiredLength;
      return `${
        fieldName.charAt(0).toUpperCase() + fieldName.slice(1)
      } must be at least ${minLength} characters`;
    }
    return '';
  }
}
