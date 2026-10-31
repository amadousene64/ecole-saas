/**
 * Réglages de l'application. En production, ce fichier est remplacé au déploiement (adresse du
 * realm Keycloak du domaine).
 */
export const environnement = {
  authority: 'http://localhost:8180/realms/ecole',
  clientId: 'parent',
  /**
   * Vrai quand l'application est servie sur un hôte commun à toutes les écoles (ex.
   * parent.monapp.sn, déclaré dans ECOLE_HOTES_COMMUNS côté serveur) : l'école n'y vient pas du
   * sous-domaine, elle est désignée par l'application (X-Tenant-Id).
   */
  hoteCommun: false,
};
