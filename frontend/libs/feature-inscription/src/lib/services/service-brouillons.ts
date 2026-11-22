import { inject, Service } from '@angular/core';
import {
  AnneeScolaireDto,
  Api,
  ClasseDto,
  BrouillonFinaliseDto,
  BrouillonInscriptionDto,
  PageDtoBrouillonResumeDto,
  ContenuDto,
  FinalisationBrouillon,
  OptionEnseignementDto,
  PieceDto,
  RemplissageClasseDto,
  brouillonInscriptionAbandonner,
  brouillonInscriptionDeposer,
  brouillonInscriptionEnregistrer,
  brouillonInscriptionFinaliser,
  brouillonInscriptionLire,
  brouillonInscriptionLister,
  brouillonInscriptionOuvrir,
  brouillonInscriptionPieces,
  brouillonInscriptionRetirerPiece,
  anneeScolaireLire,
  classeLister,
  classeOptions,
  inscriptionRemplissage,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';
import { requis } from '../commun/requis';
import { TypePiece } from './service-dossier';

export type StatutFinal = FinalisationBrouillon['statut'];

/**
 * Assistant d'inscription : brouillon enregistré côté serveur (chiffré) à chaque étape, pièces
 * rattachées au brouillon, finalisation tout ou rien. Chaque écriture porte la version lue.
 */
@Service()
export class ServiceBrouillons {
  private readonly api = inject(Api);

  ouvrir(): Promise<BrouillonInscriptionDto> {
    return this.api.invoke(brouillonInscriptionOuvrir, { body: {} });
  }

  /** Une page des brouillons en cours, du plus récent au plus ancien ; `curseur` : page suivante. */
  lister(
    curseur?: string,
    taille?: number,
  ): Promise<PageDtoBrouillonResumeDto> {
    return this.api.invoke(brouillonInscriptionLister, { curseur, taille });
  }

  lire(id: string): Promise<BrouillonInscriptionDto> {
    return this.api.invoke(brouillonInscriptionLire, { id });
  }

  enregistrer(
    brouillon: BrouillonInscriptionDto,
    etape: number,
    contenu: ContenuDto,
  ): Promise<BrouillonInscriptionDto> {
    return this.api.invoke(brouillonInscriptionEnregistrer, {
      id: requis(brouillon.id),
      'If-Match': ifMatch(brouillon.version),
      body: { etape, contenu },
    });
  }

  abandonner(id: string, version: number | undefined): Promise<void> {
    return this.api.invoke(brouillonInscriptionAbandonner, {
      id,
      'If-Match': ifMatch(version),
    });
  }

  finaliser(
    brouillon: BrouillonInscriptionDto,
    statut: StatutFinal,
    codePour?: number,
  ): Promise<BrouillonFinaliseDto> {
    return this.api.invoke(brouillonInscriptionFinaliser, {
      id: requis(brouillon.id),
      'If-Match': ifMatch(brouillon.version),
      body: { statut, codePour },
    });
  }

  pieces(id: string): Promise<PieceDto[]> {
    return this.api.invoke(brouillonInscriptionPieces, { id });
  }

  deposer(id: string, type: TypePiece, fichier: File): Promise<PieceDto> {
    return this.api.invoke(brouillonInscriptionDeposer, {
      id,
      body: { fichier, type },
    });
  }

  retirerPiece(id: string, pieceId: string): Promise<void> {
    return this.api.invoke(brouillonInscriptionRetirerPiece, { id, pieceId });
  }

  /** Année du brouillon (libellé affiché dans l'en-tête). */
  annee(id: string): Promise<AnneeScolaireDto> {
    return this.api.invoke(anneeScolaireLire, { id });
  }

  /** Classes ouvertes à l'inscription pour l'année du brouillon. */
  classes(anneeId: string): Promise<ClasseDto[]> {
    return this.api.invoke(classeLister, { annee: anneeId });
  }

  /** Inscrits (pré-inscrits compris) et capacité de chaque classe de l'année. */
  remplissage(anneeId: string): Promise<RemplissageClasseDto[]> {
    return this.api.invoke(inscriptionRemplissage, { anneeId });
  }

  /** Options proposées par une classe (LV2…), regroupées par `groupe`. */
  options(classeId: string): Promise<OptionEnseignementDto[]> {
    return this.api.invoke(classeOptions, { id: classeId });
  }
}
