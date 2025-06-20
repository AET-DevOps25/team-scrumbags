import { Routes } from '@angular/router';
import { ListView } from './views/list-view/list.view';
import { PokemonDetailView } from './views/pokemon-detail.view/pokemon-detail.view';
import { canActivateAuth, canActivateProject } from './guards/auth.guard';
import { ProjectOverviewView } from './views/project-overview.view/project-overview.view';
import { ProjectDetailView } from './views/project-detail.view/project-detail.view';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/projects',
    pathMatch: 'full',
  },
  {
    path: 'pokemon',
    component: ListView,
    canActivate: [canActivateAuth], // Just check if user is signed in
  },
  {
    path: 'pokemon/:id',
    component: PokemonDetailView,
    canActivate: [canActivateAuth], // Just check if user is signed in
  },
  {
    path: 'projects',
    component: ProjectOverviewView,
    canActivate: [canActivateAuth], // Just check if user is signed in
  },
  {
    path: 'projects/:id',
    component: ProjectDetailView,
    canActivate: [canActivateProject], // Just check if user is signed in
  },
];
