import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  RouterStateSnapshot,
} from '@angular/router';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';

// Basic authentication guard - just checks if user is signed in
const isAuthenticated = async (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  authData: AuthGuardData
): Promise<boolean> => {
  return authData.authenticated;
};

// Project-specific role guard - checks authentication and Project-specific role
const hasProjectAccess = async (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  authData: AuthGuardData
): Promise<boolean> => {
  const { authenticated, grantedRoles } = authData;

  if (!authenticated) {
    return false;
  }

  // Get the Project ID from the route parameters
  const projectId = route.params['projectId'];
  if (!projectId) {
    return false;
  }

  // Check if user has access to this specific Project
  // You can customize this logic based on your role naming convention
  const requiredRole = `project-${projectId}`;

  // Check in resource roles (assuming your Keycloak client has resource roles)
  const hasResourceRole = Object.values(grantedRoles.resourceRoles).some(
    (roles) => roles.includes(requiredRole)
  );

  // Check in realm roles as fallback
  const hasRealmRole = grantedRoles.realmRoles.includes(requiredRole);

  // Also allow if user has admin role
  const isAdmin =
    grantedRoles.realmRoles.includes('admin') ||
    Object.values(grantedRoles.resourceRoles).some((roles) =>
      roles.includes('admin')
    );

  const hasAccess = hasResourceRole || hasRealmRole || isAdmin;

  // If user doesn't have access, show snackbar and redirect
  if (!hasAccess) {
    const snackBar = inject(MatSnackBar);

    snackBar.open(
      `Access denied: You don't have permission to view Project #${projectId}`,
      '',
      {
        duration: 5000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom',
      }
    );
  }

  return hasAccess;
};

export const canActivateAuth = createAuthGuard<CanActivateFn>(isAuthenticated);
export const canActivateProject =
  createAuthGuard<CanActivateFn>(hasProjectAccess);
