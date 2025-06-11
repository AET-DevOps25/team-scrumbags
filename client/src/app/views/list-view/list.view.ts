import { Component, inject, OnInit, signal } from '@angular/core';
import { PokemonState } from '../../states/pokemon.state';
import { PokemonCardComponent } from '../../components/pokemon-card.component/pokemon-card.component';
import { CommonModule } from '@angular/common';
import { PokemonApi } from '../../services/pokemon.api';

@Component({
  selector: 'app-list-view',
  imports: [PokemonCardComponent, CommonModule],
  templateUrl: './list.view.html',
  styleUrl: './list.view.scss',
})
export class ListView implements OnInit {
  protected state = inject(PokemonState);
  private api = inject(PokemonApi);
  
  readonly loading = signal<boolean>(false);

  ngOnInit(): void {
    // load pokeomon list from API
    this.loading.set(true);
    this.api.getPokemonList().subscribe({
      next: (pokemonList) => {
        this.loading.set(false);
        this.state.setPokemonList(pokemonList);
      },
      error: (error) => {
        this.loading.set(false);
        console.error('Error loading Pokemon list:', error);
      },
    });
  }
}
