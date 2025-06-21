import {
  Component,
  computed,
  inject,
  input,
  OnInit,
  Signal,
  signal,
} from '@angular/core';
import { Router, ROUTER_OUTLET_DATA } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ProjectState } from '../../states/project.state';
import { ProjectAddDialog } from '../../components/project-add/project-add.component';
import { ProjectService } from '../../services/project.service';

@Component({
  selector: 'project-overview',
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatGridListModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './project-overview.view.html',
  styleUrl: './project-overview.view.scss',
})
export class ProjectOverviewView{
  protected state = inject(ProjectState);
  private service = inject(ProjectService);
  private router = inject(Router);
  private dialog = inject(MatDialog);

  loading = computed(() => this.service.isLoadingProjectList());

  navigateToProject(projectId: string): void {
    this.router.navigate(['/projects', projectId]);
  }

  openAddProjectDialog(): void {
    this.dialog.open(ProjectAddDialog, {
      maxWidth: '80rem',
      minWidth: '50rem',
    });
  }
}
