// Interfaces for PokeAPI responses
export interface PokeApiListResponse {
  count: number;
  next: string | null;
  previous: string | null;
  results: PokeApiListItem[];
}

export interface PokeApiListItem {
  name: string;
  url: string;
}

export interface PokeApiPokemonResponse {
  id: number;
  name: string;
  sprites: PokeApiSprites;
  height: number;
  weight: number;
  types: PokeApiType[];
  abilities: PokeApiAbility[];
}

export interface PokeApiSprites {
  front_default: string | null;
  front_shiny: string | null;
  back_default: string | null;
  back_shiny: string | null;
}

export interface PokeApiType {
  slot: number;
  type: {
    name: string;
    url: string;
  };
}

export interface PokeApiAbility {
  ability: {
    name: string;
    url: string;
  };
  is_hidden: boolean;
  slot: number;
}
