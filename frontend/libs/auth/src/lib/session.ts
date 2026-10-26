import { computed, inject, Service, signal } from '@angular/core';
import { Api, mesEcoles, moi, MoiDto } from '@ecole/api-client';
import { EcoleChoix } from '@ecole/ui';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { EcoleCourante } from './ecole-courante';

/**
 * - `inconnu` : rien n'a encore été chargé ;
 * - `choix-requis` : l'utilisateur a plusieurs écoles et n'en a pas choisi ;
 * - `aucune-ecole` : compte sans rattachement ;
 * - `pret` : école choisie et profil `/moi` chargé.
 */
export type EtatSession =
  | 'inconnu'
  | 'chargement'
  | 'pret'
  | 'choix-requis'
  | 'aucune-ecole'
  | 'erreur';

/**
 * Écoles de l'utilisateur et profil dans l'école choisie. Les rôles viennent TOUJOURS de
 * `GET /api/v1/moi` (jamais du jeton) et ne servent qu'à l'affichage : le serveur décide.
 */
@Service()
export class Session {
  private readonly api = inject(Api);
  private readonly courante = inject(EcoleCourante);
  private readonly oidc = inject(OidcSecurityService);

  readonly etat = signal<EtatSession>('inconnu');
  readonly ecoles = signal<EcoleChoix[]>([]);
  readonly profil = signal<MoiDto | null>(null);

  readonly roles = computed(() => this.profil()?.roles ?? []);
  readonly plusieursEcoles = computed(() => this.ecoles().length > 1);
  readonly ecoleActive = computed(
    () => this.ecoles().find((e) => e.id === this.profil()?.ecoleId) ?? null,
  );
  readonly utilisateur = computed(() => {
    const donnees = this.oidc.userData().userData as {
      preferred_username?: string;
    } | null;
    return donnees?.preferred_username ?? null;
  });

  private enCours: Promise<void> | null = null;

  /** Charge les écoles puis le profil. Idempotent ; les appels concurrents partagent le travail. */
  charger(): Promise<void> {
    if (this.etat() !== 'inconnu') {
      return this.enCours ?? Promise.resolve();
    }
    this.enCours = this.demarrer().finally(() => (this.enCours = null));
    return this.enCours;
  }

  /** Repart de zéro (après une erreur, par exemple). */
  recharger(): Promise<void> {
    this.profil.set(null);
    this.etat.set('inconnu');
    return this.charger();
  }

  async choisirEcole(id: string): Promise<void> {
    if (!this.ecoles().some((e) => e.id === id)) {
      return;
    }
    this.courante.choisir(id);
    await this.recharger();
  }

  /** Retour au choix d'école (menu « Changer d'école »). */
  changerEcole(): void {
    this.courante.oublier();
    this.profil.set(null);
    this.etat.set(this.ecoles().length > 0 ? 'choix-requis' : 'inconnu');
  }

  /** Le serveur a refusé l'école demandée : on l'oublie et on rechargera la liste. */
  surEcoleRefusee(): void {
    this.courante.oublier();
    this.profil.set(null);
    this.etat.set('inconnu');
  }

  /** Vrai si aucun rôle n'est exigé, ou si l'utilisateur en a au moins un parmi ceux exigés. */
  aUnRole(requis: readonly string[]): boolean {
    return requis.length === 0 || requis.some((r) => this.roles().includes(r));
  }

  deconnecter(): void {
    this.courante.oublier();
    this.profil.set(null);
    this.ecoles.set([]);
    this.etat.set('inconnu');
    this.oidc.logoff().subscribe();
  }

  private async demarrer(): Promise<void> {
    this.etat.set('chargement');
    try {
      const reponse = await this.api.invoke(mesEcoles);
      const ecoles = reponse.flatMap((e) =>
        e.id && e.nom ? [{ id: e.id, code: e.code ?? '', nom: e.nom }] : [],
      );
      this.ecoles.set(ecoles);
      if (ecoles.length === 0) {
        this.courante.oublier();
        this.etat.set('aucune-ecole');
        return;
      }
      let id = this.courante.id();
      if (id && !ecoles.some((e) => e.id === id)) {
        id = null; // école mémorisée mais plus accessible
      }
      if (!id && ecoles.length === 1) {
        id = ecoles[0].id;
      }
      if (!id) {
        this.courante.oublier();
        this.etat.set('choix-requis');
        return;
      }
      this.courante.choisir(id);
      this.profil.set(await this.api.invoke(moi));
      this.etat.set('pret');
    } catch {
      this.etat.set('erreur');
    }
  }
}
