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
import { Project } from '../../models/project.model';
import { ProjectService } from '../../services/project.service';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-project-add',
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
  private service = inject(ProjectService);
  private snackBar = inject(MatSnackBar);

  readonly isSubmitting = signal<boolean>(false);

  projectForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
    description: [''],
  });

  onSubmit(): void {
    if (this.projectForm.valid) {
      this.isSubmitting.set(true);
      const formValue = this.projectForm.value;

      this.service.createProject(formValue as Project).subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.dialogRef.close();
        },
        error: () => {
          this.isSubmitting.set(false);
          this.snackBar.open('Error creating project. Please try again.', 'Close', {
            duration: 3000,
          });
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
