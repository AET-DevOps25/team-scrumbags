import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot } from '@angular/router';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';

// Basic authentication guard - just checks if user is signed in
const isAuthenticated = async (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  authData: AuthGuardData
): Promise<boolean> => {
  return authData.authenticated;
};

// Pokemon-specific role guard - checks authentication and Pokemon-specific role
const hasPokemonAccess = async (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  authData: AuthGuardData
): Promise<boolean> => {
  const { authenticated, grantedRoles } = authData;
  
  if (!authenticated) {
    return false;
  }
  
  // Get the Pokemon ID from the route parameters
  const pokemonId = route.params['id'];
  if (!pokemonId) {
    return false;
  }
  
  // Check if user has access to this specific Pokemon
  // You can customize this logic based on your role naming convention
  const requiredRole = `pokemon-${pokemonId}`;
  
  // Check in resource roles (assuming your Keycloak client has resource roles)
  const hasResourceRole = Object.values(grantedRoles.resourceRoles)
    .some((roles) => roles.includes(requiredRole));
  
  // Check in realm roles as fallback
  const hasRealmRole = grantedRoles.realmRoles.includes(requiredRole);
  
  // Also allow if user has a general 'pokemon-admin' role
  const isAdmin = grantedRoles.realmRoles.includes('pokemon-admin') ||
    Object.values(grantedRoles.resourceRoles)
      .some((roles) => roles.includes('pokemon-admin'));
  
  return hasResourceRole || hasRealmRole || isAdmin;
};

export const canActivateAuth = createAuthGuard<CanActivateFn>(isAuthenticated);
export const canActivatePokemon = createAuthGuard<CanActivateFn>(hasPokemonAccess);