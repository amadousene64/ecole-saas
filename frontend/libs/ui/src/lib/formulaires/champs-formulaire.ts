import { Provider } from '@angular/core';
import {
  MAT_FORM_FIELD_DEFAULT_OPTIONS,
  MatFormFieldDefaultOptions,
} from '@angular/material/form-field';

/**
 * Champs Material des formulaires : contour, et aucune place réservée sous le champ. L'écart entre
 * champs vient du jeton --ecole-espace-champs ; un message d'aide ou d'erreur agrandit le champ
 * quand il s'affiche.
 *
 * À fournir sur chaque PAGE à formulaires (`providers: [CHAMPS_FORMULAIRE]`), chargée à la
 * demande : ses sections et onglets en héritent. Jamais dans `app.config` : Material form-field
 * entrerait dans le lot initial (+110 Ko mesurés sur le back-office).
 */
export const CHAMPS_FORMULAIRE: Provider = {
  provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
  useValue: {
    appearance: 'outline',
    subscriptSizing: 'dynamic',
  } satisfies MatFormFieldDefaultOptions,
};
