import { Injectable, signal } from '@angular/core';
import { Pokemon } from '../models/pokemon.model';

@Injectable({
  providedIn: 'root',
})
export class PokemonState {
  private readonly _pokemonList = signal<Pokemon[]>([]);

  readonly pokemonList = this._pokemonList.asReadonly();

  setPokemonList(pokemonList: Pokemon[]): void {
    this._pokemonList.set(pokemonList);
  }

  findPokemonById(id: number): Pokemon | null {
    return this._pokemonList().find(pokemon => pokemon.id === id) || null;
  }
}
