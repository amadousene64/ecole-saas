/**
 * Réglages de l'application. En production, ce fichier est remplacé au déploiement (adresse du
 * realm Keycloak du domaine).
 */
export const environnement = {
  authority: 'http://localhost:8180/realms/ecole',
  clientId: 'vie-scolaire',
  /** Adresses des trois applications : à remplacer au déploiement (https obligatoire hors localhost). */
  apps: {
    backoffice: 'http://localhost:4200',
    'vie-scolaire': 'http://localhost:4202',
    parent: 'http://localhost:4201',
  },
};
