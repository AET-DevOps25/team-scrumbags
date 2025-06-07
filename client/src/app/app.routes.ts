import { Routes } from '@angular/router';
import { ListView } from './views/list-view/list.view';
import { PokemonDetailView } from './views/pokemon-detail.view/pokemon-detail.view';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/pokemon',
    pathMatch: 'full',
  },
  {
    path: 'pokemon',
    component: ListView,
  },
  {
    path: 'pokemon/:id',
    component: PokemonDetailView,
  },
];
