import { Routes } from '@angular/router';
import { ListView } from './views/list-view/list.view';
import { PokemonDetailView } from './views/pokemon-detail.view/pokemon-detail.view';
import { canActivateAuth, canActivatePokemon } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/pokemon',
    pathMatch: 'full',
  },
  {
    path: 'pokemon',
    component: ListView,
    canActivate: [canActivateAuth] // Just check if user is signed in
  },
  {
    path: 'pokemon/:id',
    component: PokemonDetailView,
    canActivate: [canActivatePokemon] // Check authentication + Pokemon-specific role
  },
];
