import { Routes } from '@angular/router';
import { canActivateAuth, canActivateProject } from './guards/auth.guard';
import { ProjectOverviewView } from './views/project-overview/project-overview.view';
import { ProjectDetailView } from './views/project-detail/project-detail.view';
import { SidebarComponent } from './components/sidebar/sidebar.component';

export const routes: Routes = [
  {
    path: '',
    component: SidebarComponent,
    children: [
      {
        path: '',
        redirectTo: 'projects',
        pathMatch: 'full',
      },
      {
        path: 'projects',
        component: ProjectOverviewView,
        canActivate: [canActivateAuth],
      },
      {
        path: 'projects/:id',
        component: ProjectDetailView,
        canActivate: [canActivateProject],
      },
    ],
  },
];
