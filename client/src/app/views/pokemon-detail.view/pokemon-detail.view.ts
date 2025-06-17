import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TitleCasePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { PokemonState } from '../../states/pokemon.state';
import { Pokemon } from '../../models/pokemon.model';
import { PokemonApi } from '../../services/pokemon.api';

@Component({
  selector: 'app-pokemon-detail-view',
  imports: [MatCardModule, MatButtonModule, MatProgressSpinnerModule, TitleCasePipe],
  templateUrl: './pokemon-detail.view.html',
  styleUrl: './pokemon-detail.view.scss'
})
export class PokemonDetailView implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  protected state = inject(PokemonState);
  protected pokemonApi = inject(PokemonApi);

  private readonly pokemonId = signal<number | null>(null);

  readonly pokemon = signal<Pokemon | null>(null);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  private routeSubscription?: Subscription;

  ngOnInit(): void {
    this.routeSubscription = this.route.params.subscribe(params => {
      const id = parseInt(params['id'], 10);
      if (isNaN(id)) {
        this.error.set('Invalid Pokemon ID');
        return;
      }
      
      this.pokemonId.set(id);
      this.loadPokemon(id);
    });
  }

  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
  }

  private loadPokemon(id: number): void {
    this.loading.set(true);
    this.pokemon.set(null);
    this.error.set(null);

    // Check if the Pokemon is already in the state
    const cachedPokemon = this.state.findPokemonById(id);
    if (cachedPokemon) {
      this.loading.set(false);
      this.pokemon.set(cachedPokemon);
      console.log('Pokemon loaded from cache:', cachedPokemon.name);
      return;
    }

    // If not cached, load from API
    this.pokemonApi.getPokemonById(id)
      .subscribe({
        next: (pokemon) => {
          this.error.set(null);
          this.pokemon.set(pokemon);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Failed to load Pokemon details');
          this.loading.set(false);
        }
      });
  }

  onGoBack(): void {
    this.router.navigate(['/pokemon']);
  }
}
