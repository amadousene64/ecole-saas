import { inject, Service } from '@angular/core';
import {
  Api,
  DonneesSensiblesDto,
  DonneesSensiblesRequete,
  EtatDonneesSensiblesDto,
  PieceDto,
  donneesSensiblesEtat,
  donneesSensiblesLire,
  donneesSensiblesModifier,
  pieceDeposer,
  pieceLister,
  pieceSupprimer,
  pieceUrl,
} from '@ecole/api-client';

export type TypePiece = NonNullable<PieceDto['type']>;

/** Données sensibles (lecture journalisée côté serveur) et pièces du dossier. */
@Service()
export class ServiceDossier {
  private readonly api = inject(Api);

  /** Lecture du contenu : DIRECTEUR et CENSEUR ; le serveur journalise chaque appel. */
  async lireSensibles(
    eleveId: string,
  ): Promise<{ donnees: DonneesSensiblesDto; version: string | null }> {
    const reponse = await this.api.invoke$Response(donneesSensiblesLire, {
      eleveId,
    });
    return { donnees: reponse.body, version: reponse.headers.get('ETag') };
  }

  /** Champs renseignés, sans leur contenu (secrétariat). */
  async etatSensibles(
    eleveId: string,
  ): Promise<{ etat: EtatDonneesSensiblesDto; version: string | null }> {
    const reponse = await this.api.invoke$Response(donneesSensiblesEtat, {
      eleveId,
    });
    return { etat: reponse.body, version: reponse.headers.get('ETag') };
  }

  /** Champ absent : inchangé ; chaîne vide : effacé. Renvoie l'état et la nouvelle version. */
  async modifierSensibles(
    eleveId: string,
    version: string | null,
    corps: DonneesSensiblesRequete,
  ): Promise<{ etat: EtatDonneesSensiblesDto; version: string | null }> {
    const reponse = await this.api.invoke$Response(donneesSensiblesModifier, {
      eleveId,
      'If-Match': version ?? '"0"',
      body: corps,
    });
    return { etat: reponse.body, version: reponse.headers.get('ETag') };
  }

  pieces(eleveId: string): Promise<PieceDto[]> {
    return this.api.invoke(pieceLister, { eleveId });
  }

  deposer(eleveId: string, type: TypePiece, fichier: File): Promise<PieceDto> {
    return this.api.invoke(pieceDeposer, {
      eleveId,
      body: { fichier, type },
    });
  }

  /** URL signée (5 minutes) d'une pièce saine. */
  async url(eleveId: string, pieceId: string): Promise<string> {
    const reponse = await this.api.invoke(pieceUrl, { eleveId, pieceId });
    return reponse.url ?? '';
  }

  supprimer(eleveId: string, pieceId: string): Promise<void> {
    return this.api.invoke(pieceSupprimer, { eleveId, pieceId });
  }
}
