import {
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { PieceDto } from '@ecole/api-client';
import { Pastille } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import {
  LIBELLES_ANALYSE,
  LIBELLES_PIECE,
  libellePiece,
} from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceDossier, TypePiece } from '../services/service-dossier';

/** Taille maximale d'une pièce (octets), comme côté serveur. */
export const TAILLE_PIECE_MAX = 5_000_000;
const TYPES_ACCEPTES = ['application/pdf', 'image/jpeg', 'image/png'];

/** Délais entre deux relectures tant qu'une pièce est en analyse (ms), puis arrêt. */
export const DELAIS_SONDAGE = [2000, 3000, 5000, 8000, 12000, 15000];
const SONDAGES_MAX = 20;

/** Contrôles de confort avant envoi ; le serveur refait les siens sur les octets. */
export function controlerFichier(fichier: File): string | null {
  if (fichier.size === 0) {
    return 'Le fichier est vide.';
  }
  if (fichier.size > TAILLE_PIECE_MAX) {
    return `Fichier trop volumineux (${TAILLE_PIECE_MAX / 1_000_000} Mo au plus).`;
  }
  if (!TYPES_ACCEPTES.includes(fichier.type)) {
    return 'Formats acceptés : PDF, JPEG ou PNG.';
  }
  return null;
}

/**
 * Pièces du dossier : dépôt avec contrôles côté client, statut d'analyse rafraîchi jusqu'à
 * « vérifiée » ou « refusée » (sondage borné, arrêté à la fermeture de l'écran), ouverture seulement
 * d'une pièce vérifiée. Le certificat médical est réservé : seuls le directeur et le censeur
 * l'ouvrent.
 */
@Component({
  selector: 'ecole-onglet-pieces',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    Pastille,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-pieces">
        {{ message }}
      </p>
    }
    <p class="sr-only" role="status" aria-live="polite">{{ annonce() }}</p>

    <h2>Pièces du dossier</h2>
    <table data-testid="table-pieces">
      <caption class="sr-only">
        Pièces du dossier
      </caption>
      <thead>
        <tr>
          <th scope="col">Pièce</th>
          <th scope="col">Analyse</th>
          <th scope="col">Déposée le</th>
          <th scope="col"><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        @for (p of pieces(); track p.id) {
          <tr [attr.data-testid]="'piece-' + p.type">
            <td>{{ libelle(p.type) }}</td>
            <td>
              @if (analyse(p); as a) {
                <ecole-pastille [ton]="a.ton">{{ a.libelle }}</ecole-pastille>
              }
            </td>
            <td>{{ p.creeLe?.slice(0, 10) }}</td>
            <td>
              @if (p.statut === 'SAIN') {
                @if (peutOuvrir(p)) {
                  <button
                    matButton
                    type="button"
                    (click)="ouvrir(p)"
                    [attr.data-testid]="'ouvrir-' + p.type"
                  >
                    Ouvrir<span class="sr-only"> : {{ libelle(p.type) }}</span>
                  </button>
                } @else {
                  <span class="aide">Réservé</span>
                }
              } @else if (p.statut === 'EN_ANALYSE') {
                <span class="aide">Ouverture possible après l'analyse.</span>
              } @else {
                <span class="aide">Bloquée : ouverture impossible.</span>
              }
              @if (droits.peutEcrire()) {
                <button
                  matButton
                  type="button"
                  (click)="supprimer(p)"
                  [attr.data-testid]="'supprimer-' + p.type"
                >
                  Supprimer<span class="sr-only"> : {{ libelle(p.type) }}</span>
                </button>
              }
            </td>
          </tr>
        } @empty {
          <tr>
            <td colspan="4" class="aide">Aucune pièce déposée.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (droits.peutEcrire()) {
      <h2>Déposer une pièce</h2>
      <div class="grille" data-testid="depot-piece">
        <mat-form-field appearance="outline">
          <mat-label>Type de pièce</mat-label>
          <mat-select
            [ngModel]="type()"
            (ngModelChange)="type.set($event)"
            name="type"
            data-testid="champ-type-piece"
          >
            @for (t of types; track t.valeur) {
              <mat-option [value]="t.valeur">{{ t.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <div class="champ-fichier">
          <label for="fichier-piece">Fichier (PDF, JPEG ou PNG, 5 Mo)</label>
          <input
            id="fichier-piece"
            type="file"
            accept="application/pdf,image/jpeg,image/png"
            (change)="choisir($event)"
            data-testid="champ-fichier-piece"
          />
        </div>
      </div>
      <div class="actions">
        <button
          matButton="filled"
          type="button"
          [disabled]="occupe() || !fichier()"
          (click)="deposer()"
          data-testid="deposer-piece"
        >
          Déposer
        </button>
      </div>
    }
  `,
  styles: [
    STYLES_COMMUNS,
    `
      .champ-fichier {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
    `,
  ],
})
export class OngletPieces {
  readonly eleveId = input.required<string>();

  private readonly service = inject(ServiceDossier);
  protected readonly droits = inject(Droits);

  protected readonly types = (Object.keys(LIBELLES_PIECE) as TypePiece[]).map(
    (valeur) => ({ valeur, libelle: LIBELLES_PIECE[valeur] }),
  );
  protected readonly libelle = libellePiece;
  protected readonly pieces = signal<PieceDto[]>([]);
  protected readonly type = signal<TypePiece>('ACTE_NAISSANCE');
  protected readonly fichier = signal<File | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly annonce = signal('');
  protected readonly occupe = signal(false);
  protected readonly enAnalyse = computed(() =>
    this.pieces().some((p) => p.statut === 'EN_ANALYSE'),
  );

  private minuterie: ReturnType<typeof setTimeout> | undefined;
  private sondages = 0;
  private detruit = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.detruit = true;
      clearTimeout(this.minuterie);
    });
    queueMicrotask(() => void this.charger());
  }

  protected analyse(p: PieceDto) {
    return p.statut ? LIBELLES_ANALYSE[p.statut] : null;
  }

  /** Certificat médical : directeur et censeur ; autres pièces : direction et secrétariat. */
  protected peutOuvrir(p: PieceDto): boolean {
    return p.type === 'CERTIFICAT_MEDICAL'
      ? this.droits.peutOuvrirCertificat()
      : this.droits.peutEcrire();
  }

  private async charger(): Promise<void> {
    if (this.detruit) {
      return;
    }
    try {
      const avant = this.pieces();
      const apres = await this.service.pieces(this.eleveId());
      this.pieces.set(apres);
      this.annoncerFins(avant, apres);
      this.planifier();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  private annoncerFins(avant: PieceDto[], apres: PieceDto[]): void {
    for (const p of apres) {
      const ancienne = avant.find((a) => a.id === p.id);
      if (ancienne?.statut === 'EN_ANALYSE' && p.statut !== 'EN_ANALYSE') {
        this.annonce.set(
          `${libellePiece(p.type)} : ${p.statut === 'SAIN' ? 'vérifiée' : 'refusée'}.`,
        );
      }
    }
  }

  /** Relit la liste tant qu'une pièce est en analyse, avec des délais croissants. */
  private planifier(): void {
    clearTimeout(this.minuterie);
    if (!this.enAnalyse() || this.sondages >= SONDAGES_MAX || this.detruit) {
      return;
    }
    const delai =
      DELAIS_SONDAGE[Math.min(this.sondages, DELAIS_SONDAGE.length - 1)];
    this.sondages++;
    this.minuterie = setTimeout(() => void this.charger(), delai);
  }

  protected choisir(evenement: Event): void {
    const fichier = (evenement.target as HTMLInputElement).files?.[0] ?? null;
    this.erreur.set(null);
    if (fichier) {
      const refus = controlerFichier(fichier);
      if (refus) {
        this.erreur.set(refus);
        this.fichier.set(null);
        (evenement.target as HTMLInputElement).value = '';
        return;
      }
    }
    this.fichier.set(fichier);
  }

  protected async deposer(): Promise<void> {
    const fichier = this.fichier();
    if (!fichier) {
      return;
    }
    const refus = controlerFichier(fichier);
    if (refus) {
      this.erreur.set(refus);
      return;
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      await this.service.deposer(this.eleveId(), this.type(), fichier);
      this.fichier.set(null);
      this.annonce.set('Pièce déposée : analyse en cours.');
      this.sondages = 0;
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async ouvrir(p: PieceDto): Promise<void> {
    this.erreur.set(null);
    try {
      const url = await this.service.url(this.eleveId(), p.id ?? '');
      // Défense en profondeur : seule une adresse http(s) est ouverte (jamais javascript: ou data:).
      if (!/^https?:\/\//i.test(url)) {
        throw new Error('Adresse de pièce invalide.');
      }
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected async supprimer(p: PieceDto): Promise<void> {
    this.erreur.set(null);
    try {
      await this.service.supprimer(this.eleveId(), p.id ?? '');
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
