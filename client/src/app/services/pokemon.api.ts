import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, forkJoin, throwError } from 'rxjs';
import { map, catchError, switchMap } from 'rxjs/operators';
import { Pokemon } from '../models/pokemon.model';
import { PokeApiListResponse, PokeApiPokemonResponse } from '../models/pokeapi.model';

@Injectable({
  providedIn: 'root',
})
export class PokemonApi {
  private readonly baseUrl = 'https://pokeapi.co/api/v2';
  private http = inject(HttpClient);

  getPokemonList(): Observable<Pokemon[]> {
    return this.http.get<PokeApiListResponse>(`${this.baseUrl}/pokemon?limit=50`)
      .pipe(
        switchMap(listResponse => {
          // Create observables for each Pokemon detail request
          const pokemonDetailRequests = listResponse.results.map(pokemon => 
            this.http.get<PokeApiPokemonResponse>(pokemon.url)
              .pipe(
                map(detailData => this.mapToPokemon(detailData)),
                catchError(error => {
                  console.error(`Error fetching details for ${pokemon.name}:`, error);
                  return throwError(() => error);
                })
              )
          );
          
          // Execute all requests in parallel
          return forkJoin(pokemonDetailRequests);
        }),
        catchError(this.handleError('Error fetching Pokémon list'))
      );
  }

  getPokemonByName(name: string): Observable<Pokemon> {
    return this.http.get<PokeApiPokemonResponse>(`${this.baseUrl}/pokemon/${name}`)
      .pipe(
        map(data => this.mapToPokemon(data)),
        catchError(this.handleError(`Error fetching Pokémon by name (${name})`))
      );
  }

  getPokemonById(id: number): Observable<Pokemon> {
    return this.http.get<PokeApiPokemonResponse>(`${this.baseUrl}/pokemon/${id}`)
      .pipe(
        map(data => this.mapToPokemon(data)),
        catchError(this.handleError(`Error fetching Pokémon by ID (${id})`))
      );
  }

  private mapToPokemon(data: PokeApiPokemonResponse): Pokemon {
    return {
      id: data.id,
      name: data.name,
      sprite: data.sprites.front_default || ''
    };
  }

  private handleError(operation: string) {
    return (error: HttpErrorResponse): Observable<never> => {
      console.error(`${operation}:`, error);
      
      let errorMessage = 'An unknown error occurred';
      if (error.error instanceof ErrorEvent) {
        // Client-side error
        errorMessage = `Client error: ${error.error.message}`;
      } else {
        // Server-side error
        errorMessage = `Server error: ${error.status} ${error.statusText}`;
      }
      
      return throwError(() => new Error(errorMessage));
    };
  }
}
