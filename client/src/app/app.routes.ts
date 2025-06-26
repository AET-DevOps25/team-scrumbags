import { Routes } from '@angular/router';
import { canActivateAuth, canActivateProject } from './guards/auth.guard';
import { ProjectOverviewView } from './views/project-overview/project-overview.view';
import { ProjectDetailView } from './views/project-detail/project-detail.view';
import { SidebarComponent } from './views/sidebar/sidebar.component';
import { ProjectSettingsView } from './views/project-settings/project-settings.view';

export const routes: Routes = [
  // Default path
  {
    path: '',
    redirectTo: 'projects',
    pathMatch: 'full',
  },
  // Paths with sidebar
  {
    path: '',
    component: SidebarComponent,
    children: [
      {
        path: 'projects',
        loadComponent: () => ProjectOverviewView,
        canActivate: [canActivateAuth],
      },
      {
        path: 'projects/:id',
        loadComponent: () => ProjectDetailView,
        canActivate: [canActivateProject],
      },
      {
        path: 'projects/:id/settings',
        loadComponent: () => ProjectSettingsView,
        canActivate: [canActivateProject],
      },
    ],
  },
  // Fallback route
  {
    path: '**',
    redirectTo: 'projects',
    pathMatch: 'full',
  },
];
