import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { Pokemon } from '../../models/pokemon.model';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { TitleCasePipe } from '@angular/common';

@Component({
  selector: 'app-pokemon-card',
  imports: [MatCardModule, MatButtonModule, TitleCasePipe],
  templateUrl: './pokemon-card.component.html',
  styleUrl: './pokemon-card.component.scss',
})
export class PokemonCardComponent {
  private router = inject(Router);

  pokemon = input.required<Pokemon>();

  onViewDetails(): void {
    const pokemonId = this.pokemon().id;
    this.router.navigate(['/pokemon', pokemonId]);
  }
}
