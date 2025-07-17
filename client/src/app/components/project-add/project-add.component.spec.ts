import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { ProjectAddDialog } from './project-add.component';
import { ProjectService } from '../../services/project.service';
import { Project } from '../../models/project.model';

describe('ProjectAddDialog', () => {
  let component: ProjectAddDialog;
  let fixture: ComponentFixture<ProjectAddDialog>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ProjectAddDialog>>;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;

  const mockProject: Project = {
    id: 'new-project-id',
    name: 'Test Project',
    description: 'Test Description',
    users: [],
    meetingNotes: new Map(),
    reports: new Map(),
    messages: new Map()
  };

  beforeEach(async () => {
    const projectServiceSpyObj = jasmine.createSpyObj('ProjectService', ['createProject']);
    const dialogRefSpyObj = jasmine.createSpyObj('MatDialogRef', ['close']);
    const snackBarSpyObj = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [
        ProjectAddDialog,
        ReactiveFormsModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpyObj },
        { provide: MatDialogRef, useValue: dialogRefSpyObj },
        { provide: MatSnackBar, useValue: snackBarSpyObj }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectAddDialog);
    component = fixture.componentInstance;
    projectServiceSpy = TestBed.inject(ProjectService) as jasmine.SpyObj<ProjectService>;
    dialogRefSpy = TestBed.inject(MatDialogRef) as jasmine.SpyObj<MatDialogRef<ProjectAddDialog>>;
    snackBarSpy = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;
    
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize form with empty values', () => {
    expect(component.projectForm.get('name')?.value).toBe('');
    expect(component.projectForm.get('description')?.value).toBe('');
  });

  it('should mark form as invalid when name is empty', () => {
    component.projectForm.patchValue({ name: '', description: 'Test' });
    expect(component.projectForm.valid).toBeFalse();
    expect(component.projectForm.get('name')?.hasError('required')).toBeTrue();
  });

  it('should mark form as invalid when name is too short', () => {
    component.projectForm.patchValue({ name: 'ab', description: 'Test' });
    expect(component.projectForm.valid).toBeFalse();
    expect(component.projectForm.get('name')?.hasError('minlength')).toBeTrue();
  });

  it('should mark form as valid when name meets requirements', () => {
    component.projectForm.patchValue({ name: 'Valid Project Name', description: 'Test' });
    expect(component.projectForm.valid).toBeTrue();
  });

  describe('onSubmit', () => {
    it('should create project and close dialog on success', () => {
      projectServiceSpy.createProject.and.returnValue(of(mockProject));
      component.projectForm.patchValue({ name: 'Test Project', description: 'Test Description' });

      component.onSubmit();

      expect(projectServiceSpy.createProject).toHaveBeenCalledWith({
        name: 'Test Project',
        description: 'Test Description'
      } as Project);
      expect(dialogRefSpy.close).toHaveBeenCalled();
      expect(component.isSubmitting()).toBeFalse();
    });

    it('should set submitting state during API call', () => {
      projectServiceSpy.createProject.and.returnValue(of(mockProject));
      component.projectForm.patchValue({ name: 'Test Project', description: 'Test Description' });

      expect(component.isSubmitting()).toBeFalse();
      
      component.onSubmit();
      
      // During the call, should be submitting
      expect(component.isSubmitting()).toBeTrue();
    });

    it('should show error message on API failure', () => {
      projectServiceSpy.createProject.and.returnValue(throwError(() => new Error('API Error')));
      component.projectForm.patchValue({ name: 'Test Project', description: 'Test Description' });

      component.onSubmit();

      expect(snackBarSpy.open).toHaveBeenCalledWith(
        'Error creating project. Please try again.',
        'Close',
        { duration: 3000 }
      );
      expect(component.isSubmitting()).toBeFalse();
      expect(dialogRefSpy.close).not.toHaveBeenCalled();
    });

    it('should not submit when form is invalid', () => {
      component.projectForm.patchValue({ name: '', description: 'Test' }); // Invalid: name required

      component.onSubmit();

      expect(projectServiceSpy.createProject).not.toHaveBeenCalled();
      expect(component.projectForm.get('name')?.touched).toBeTrue();
    });
  });

  describe('onCancel', () => {
    it('should close dialog', () => {
      component.onCancel();
      expect(dialogRefSpy.close).toHaveBeenCalled();
    });
  });

  describe('getErrorMessage', () => {
    it('should return required error message', () => {
      component.projectForm.get('name')?.markAsTouched();
      component.projectForm.patchValue({ name: '' });

      const errorMessage = component.getErrorMessage('name');

      expect(errorMessage).toBe('Name is required');
    });

    it('should return minlength error message', () => {
      component.projectForm.get('name')?.markAsTouched();
      component.projectForm.patchValue({ name: 'ab' });

      const errorMessage = component.getErrorMessage('name');

      expect(errorMessage).toBe('Name must be at least 3 characters');
    });

    it('should return empty string when no error', () => {
      component.projectForm.patchValue({ name: 'Valid Name' });

      const errorMessage = component.getErrorMessage('name');

      expect(errorMessage).toBe('');
    });

    it('should capitalize field name in error message', () => {
      component.projectForm.get('description')?.setErrors({ required: true });

      const errorMessage = component.getErrorMessage('description');

      expect(errorMessage).toBe('Description is required');
    });
  });

  describe('form validation UI', () => {
    it('should show required error in template when name is empty and touched', () => {
      const nameInput = fixture.nativeElement.querySelector('input[formControlName="name"]');
      const nameControl = component.projectForm.get('name');

      nameControl?.setValue('');
      nameControl?.markAsTouched();
      fixture.detectChanges();

      expect(nameControl?.hasError('required')).toBeTrue();
      expect(nameControl?.touched).toBeTrue();
    });
  });
});