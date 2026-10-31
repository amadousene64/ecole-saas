import { Component, computed, inject, input, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ClasseDto, InscriptionDto } from '@ecole/api-client';
import { Pastille } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { libelleStatut, tonStatut } from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceEleves, StatutInscription } from '../services/service-eleves';

type Action = 'inscrire' | 'classe' | 'sortie' | null;

/**
 * Scolarité : historique des inscriptions, inscription, validation d'une pré-inscription, changement
 * de classe et changement de statut. La radiation n'est proposée qu'au directeur (le serveur la
 * refuse à tout autre rôle). Les avertissements de capacité renvoyés par le serveur sont affichés
 * tels quels après l'opération.
 */
@Component({
  selector: 'ecole-onglet-scolarite',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    Pastille,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-scolarite">
        {{ message }}
      </p>
    }
    <div role="status" data-testid="avertissements">
      @for (a of avertissements(); track a) {
        <p class="message avertissement">{{ a }}</p>
      }
      @if (confirmation(); as texte) {
        <p class="message ok">{{ texte }}</p>
      }
    </div>

    <h2>Scolarité</h2>
    <table data-testid="table-inscriptions">
      <caption class="sr-only">
        Inscriptions de l'élève
      </caption>
      <thead>
        <tr>
          <th scope="col">Classe</th>
          <th scope="col">Statut</th>
          <th scope="col">Entrée</th>
          <th scope="col">Sortie</th>
          <th scope="col">Redoublant</th>
        </tr>
      </thead>
      <tbody>
        @for (i of historique(); track i.id) {
          <tr>
            <td>{{ i.classe }}</td>
            <td>
              <ecole-pastille [ton]="ton(i.statut)">{{
                libelle(i.statut)
              }}</ecole-pastille>
            </td>
            <td>{{ i.dateEntree }}</td>
            <td>
              {{ i.dateSortie || '—' }}
              @if (i.motifSortie) {
                <div class="aide">{{ i.motifSortie }}</div>
              }
            </td>
            <td>{{ i.redoublant ? 'Oui' : 'Non' }}</td>
          </tr>
        } @empty {
          <tr>
            <td colspan="5" class="aide">Aucune inscription.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (droits.peutEcrire()) {
      <div class="actions" role="group" aria-label="Actions sur l'inscription">
        @if (!active()) {
          <button
            matButton="filled"
            type="button"
            (click)="ouvrir('inscrire')"
            data-testid="action-inscrire"
          >
            Inscrire dans une classe
          </button>
        } @else {
          @if (active()?.statut === 'PRE_INSCRIT') {
            <button
              matButton="filled"
              type="button"
              (click)="valider()"
              [disabled]="occupe()"
              data-testid="action-valider"
            >
              Valider la pré-inscription
            </button>
          }
          <button
            matButton
            type="button"
            (click)="ouvrir('classe')"
            data-testid="action-classe"
          >
            Changer de classe
          </button>
          <button
            matButton
            type="button"
            (click)="ouvrir('sortie')"
            data-testid="action-sortie"
          >
            Changer le statut
          </button>
        }
      </div>
    }

    @if (action() === 'inscrire') {
      <h3>Inscrire dans une classe</h3>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="inscrire()"
        class="grille"
        data-testid="form-inscrire"
      >
        <mat-form-field appearance="outline">
          <mat-label>Classe</mat-label>
          <mat-select formControlName="classeId" data-testid="champ-classe">
            @for (c of classes(); track c.id) {
              <mat-option [value]="c.id">{{ c.nom }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Statut</mat-label>
          <mat-select formControlName="statut" data-testid="champ-statut">
            <mat-option value="INSCRIT">Inscrit</mat-option>
            <mat-option value="PRE_INSCRIT">Pré-inscrit</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-checkbox formControlName="redoublant">Redoublant</mat-checkbox>
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="confirmer-inscription"
          >
            Inscrire
          </button>
          <button matButton type="button" (click)="ouvrir(null)">
            Annuler
          </button>
        </div>
      </form>
    }

    @if (action() === 'classe') {
      <h3>Changer de classe</h3>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="changerClasse()"
        class="grille"
        data-testid="form-classe"
      >
        <mat-form-field appearance="outline">
          <mat-label>Nouvelle classe</mat-label>
          <mat-select formControlName="classeId" data-testid="champ-classe">
            @for (c of classes(); track c.id) {
              <mat-option [value]="c.id">{{ c.nom }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-checkbox formControlName="redoublant">Redoublant</mat-checkbox>
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="confirmer-classe"
          >
            Changer de classe
          </button>
          <button matButton type="button" (click)="ouvrir(null)">
            Annuler
          </button>
        </div>
      </form>
    }

    @if (action() === 'sortie') {
      <h3>Changer le statut</h3>
      <form
        [formGroup]="sortie"
        (ngSubmit)="sortir()"
        class="grille"
        data-testid="form-sortie"
      >
        <mat-form-field appearance="outline">
          <mat-label>Nouveau statut</mat-label>
          <mat-select formControlName="statut" data-testid="champ-sortie">
            <mat-option value="TRANSFERE">Transféré</mat-option>
            <mat-option value="ABANDON">Abandon</mat-option>
            @if (droits.estDirecteur()) {
              <mat-option value="RADIE">Radié</mat-option>
            }
          </mat-select>
          @if (!droits.estDirecteur()) {
            <mat-hint>La radiation est réservée au directeur.</mat-hint>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Date</mat-label>
          <input matInput type="date" formControlName="date" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Motif</mat-label>
          <input matInput formControlName="motif" maxlength="200" />
        </mat-form-field>
        <p class="aide">Cette décision est définitive.</p>
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="confirmer-sortie"
          >
            Confirmer
          </button>
          <button matButton type="button" (click)="ouvrir(null)">
            Annuler
          </button>
        </div>
      </form>
    }
  `,
  styles: [STYLES_COMMUNS],
})
export class OngletScolarite {
  readonly eleveId = input.required<string>();

  private readonly service = inject(ServiceEleves);
  protected readonly droits = inject(Droits);

  protected readonly historique = signal<InscriptionDto[]>([]);
  protected readonly classes = signal<ClasseDto[]>([]);
  protected readonly action = signal<Action>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly avertissements = signal<string[]>([]);
  protected readonly confirmation = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly libelle = libelleStatut;
  protected readonly ton = tonStatut;

  /** Inscription en cours (pré-inscrite ou inscrite), s'il y en a une. */
  protected readonly active = computed(
    () =>
      this.historique().find(
        (i) => i.statut === 'INSCRIT' || i.statut === 'PRE_INSCRIT',
      ) ?? null,
  );

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    classeId: ['', Validators.required],
    statut: ['INSCRIT' as StatutInscription, Validators.required],
    redoublant: [false],
  });

  protected readonly sortie = inject(NonNullableFormBuilder).group({
    statut: ['TRANSFERE' as StatutInscription, Validators.required],
    date: [new Date().toISOString().slice(0, 10), Validators.required],
    motif: [''],
  });

  constructor() {
    queueMicrotask(() => void this.charger());
  }

  private async charger(): Promise<void> {
    try {
      const [historique, classes] = await Promise.all([
        this.service.historique(this.eleveId()),
        this.droits.peutEcrire() ? this.service.classes() : Promise.resolve([]),
      ]);
      this.historique.set(historique);
      this.classes.set(classes);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected ouvrir(action: Action): void {
    this.action.set(action);
    this.erreur.set(null);
    this.formulaire.reset({
      classeId: '',
      statut: 'INSCRIT',
      redoublant: this.active()?.redoublant ?? false,
    });
  }

  /** Exécute l'opération, affiche les avertissements du serveur, recharge l'historique. */
  private async executer(
    operation: () => Promise<InscriptionDto>,
    message: string,
  ): Promise<void> {
    this.erreur.set(null);
    this.confirmation.set(null);
    this.avertissements.set([]);
    this.occupe.set(true);
    try {
      const resultat = await operation();
      this.avertissements.set(resultat.avertissements ?? []);
      this.confirmation.set(message);
      this.action.set(null);
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async inscrire(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    await this.executer(
      () =>
        this.service.inscrire({
          eleveId: this.eleveId(),
          classeId: v.classeId,
          statut: v.statut,
          redoublant: v.redoublant,
          optionIds: [],
        }),
      'Élève inscrit.',
    );
  }

  protected async valider(): Promise<void> {
    const courante = this.active();
    if (courante) {
      await this.executer(
        () => this.service.valider(courante),
        'Pré-inscription validée.',
      );
    }
  }

  protected async changerClasse(): Promise<void> {
    const courante = this.active();
    if (!courante || this.formulaire.controls.classeId.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    await this.executer(
      () =>
        this.service.modifierInscription(courante, {
          classeId: v.classeId,
          redoublant: v.redoublant,
        }),
      'Classe modifiée.',
    );
  }

  protected async sortir(): Promise<void> {
    const courante = this.active();
    if (!courante || this.sortie.invalid) {
      this.sortie.markAllAsTouched();
      return;
    }
    const v = this.sortie.getRawValue();
    await this.executer(
      () =>
        this.service.sortir(courante, {
          statut: v.statut,
          date: v.date,
          motif: v.motif || undefined,
        }),
      'Statut modifié.',
    );
  }
}
