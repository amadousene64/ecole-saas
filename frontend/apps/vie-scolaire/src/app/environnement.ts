/**
 * Réglages de l'application. En production, ce fichier est remplacé au déploiement (adresse du
 * realm Keycloak du domaine).
 */
export const environnement = {
  authority: 'http://localhost:8180/realms/ecole',
  clientId: 'vie-scolaire',
};
