import { inject, Service } from '@angular/core';
import {
  Api,
  ClasseDto,
  EleveDto,
  EleveRequete,
  InscriptionDto,
  InscriptionRequete,
  ModificationInscriptionRequete,
  PageDtoEleveListeDto,
  SortieRequete,
  classeLister,
  eleveCreer,
  eleveLire,
  eleveModifier,
  eleveRechercher,
  inscriptionHistorique,
  inscriptionInscrire,
  inscriptionModifier,
  inscriptionSortir,
  inscriptionValider,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';
import { requis } from '../commun/requis';

export type StatutInscription = NonNullable<InscriptionDto['statut']>;

export interface CritereListe {
  recherche?: string;
  classe?: string;
  statut?: StatutInscription;
  curseur?: string;
  taille?: number;
}

/** Élèves, leur scolarité (inscriptions) et les classes proposées aux filtres. */
@Service()
export class ServiceEleves {
  private readonly api = inject(Api);

  /**
   * Toujours en POST : le nom cherché et le curseur voyagent dans le corps, jamais dans l'URL (les
   * noms de mineurs n'ont rien à faire dans un journal d'accès ou un historique).
   */
  rechercher(critere: CritereListe): Promise<PageDtoEleveListeDto> {
    return this.api.invoke(eleveRechercher, { body: { ...critere } });
  }

  lire(eleveId: string): Promise<EleveDto> {
    return this.api.invoke(eleveLire, { eleveId });
  }

  creer(corps: EleveRequete): Promise<EleveDto> {
    return this.api.invoke(eleveCreer, { body: corps });
  }

  modifier(eleve: EleveDto, corps: EleveRequete): Promise<EleveDto> {
    return this.api.invoke(eleveModifier, {
      eleveId: requis(eleve.id),
      'If-Match': ifMatch(eleve.version),
      body: corps,
    });
  }

  /** Classes de l'année en cours (filtre de la liste, choix de la classe d'inscription). */
  classes(): Promise<ClasseDto[]> {
    return this.api.invoke(classeLister, {});
  }

  historique(eleveId: string): Promise<InscriptionDto[]> {
    return this.api.invoke(inscriptionHistorique, { eleveId });
  }

  inscrire(corps: InscriptionRequete): Promise<InscriptionDto> {
    return this.api.invoke(inscriptionInscrire, { body: corps });
  }

  /** Validation d'une pré-inscription. */
  valider(inscription: InscriptionDto): Promise<InscriptionDto> {
    return this.api.invoke(inscriptionValider, {
      id: requis(inscription.id),
      'If-Match': ifMatch(inscription.version),
    });
  }

  /** Changement de classe, redoublement, options. */
  modifierInscription(
    inscription: InscriptionDto,
    corps: ModificationInscriptionRequete,
  ): Promise<InscriptionDto> {
    return this.api.invoke(inscriptionModifier, {
      id: requis(inscription.id),
      'If-Match': ifMatch(inscription.version),
      body: corps,
    });
  }

  /** Transfert, abandon ou radiation (le serveur réserve la radiation au directeur). */
  sortir(
    inscription: InscriptionDto,
    corps: SortieRequete,
  ): Promise<InscriptionDto> {
    return this.api.invoke(inscriptionSortir, {
      id: requis(inscription.id),
      'If-Match': ifMatch(inscription.version),
      body: corps,
    });
  }
}
