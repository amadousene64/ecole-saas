import {
  afterNextRender,
  Component,
  computed,
  effect,
  inject,
  Injector,
  input,
  signal,
  untracked,
} from '@angular/core';
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
import { Confirmation, Modale, Pastille } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { libelleStatut, tonStatut } from '../commun/etiquettes';
import { ServiceEleves, StatutInscription } from '../services/service-eleves';

type Action = 'inscrire' | 'classe' | 'sortie' | null;

/**
 * Scolarité : historique des inscriptions, inscription, validation d'une pré-inscription, changement
 * de classe et changement de statut, chacun dans une modale (créée à l'ouverture, détruite à la fermeture). La radiation n'est proposée qu'au
 * directeur (le serveur la refuse à tout autre rôle) et passe par une confirmation à motif
 * obligatoire, ouverte une fois la modale du statut fermée (jamais deux modales). Les
 * avertissements de capacité renvoyés par le serveur sont affichés tels quels après l'opération.
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
    Modale,
    Confirmation,
  ],
  template: `
    @if (!action() && erreur(); as message) {
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
    <table data-testid="table-inscriptions" class="cartes">
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
            <td data-label="Classe">{{ i.classe }}</td>
            <td data-label="Statut">
              <ecole-pastille [ton]="ton(i.statut)">{{
                libelle(i.statut)
              }}</ecole-pastille>
            </td>
            <td data-label="Entrée">{{ i.dateEntree }}</td>
            <td data-label="Sortie">
              {{ i.dateSortie || '—' }}
              @if (i.motifSortie) {
                <div class="aide">{{ i.motifSortie }}</div>
              }
            </td>
            <td data-label="Redoublant">{{ i.redoublant ? 'Oui' : 'Non' }}</td>
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
      <ecole-modale
        #modaleInscrire
        titre="Inscrire dans une classe"
        testId="modale-inscrire"
        [ouverte]="true"
        [modifiee]="formulaire.dirty"
        (fermee)="action.set(null)"
      >
        @if (erreur(); as message) {
          <p class="message erreur" role="alert" data-testid="erreur-scolarite">
            {{ message }}
          </p>
        }
        <form
          id="form-inscrire"
          [formGroup]="formulaire"
          (ngSubmit)="inscrire()"
          class="formulaire"
          data-testid="form-inscrire"
        >
          @if (erreurClasses(); as message) {
            <div
              class="message erreur"
              role="alert"
              data-testid="erreur-classes"
            >
              {{ message }}
              <button
                matButton
                type="button"
                (click)="chargerClasses()"
                data-testid="reessayer-classes"
              >
                Réessayer
              </button>
            </div>
          } @else if (chargementClasses()) {
            <p role="status" data-testid="chargement-classes">
              Chargement des classes…
            </p>
          }
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
        </form>
        <div pied>
          <button
            matButton
            type="button"
            (click)="modaleInscrire.demanderFermeture('fermer')"
          >
            Annuler
          </button>
          <button
            matButton="filled"
            type="submit"
            form="form-inscrire"
            [disabled]="occupe()"
            data-testid="confirmer-inscription"
          >
            Inscrire
          </button>
        </div>
      </ecole-modale>
    }

    @if (action() === 'classe') {
      <ecole-modale
        #modaleClasse
        titre="Changer de classe"
        testId="modale-classe"
        [ouverte]="true"
        [modifiee]="formulaire.dirty"
        (fermee)="action.set(null)"
      >
        @if (erreur(); as message) {
          <p class="message erreur" role="alert" data-testid="erreur-scolarite">
            {{ message }}
          </p>
        }
        <form
          id="form-classe"
          [formGroup]="formulaire"
          (ngSubmit)="changerClasse()"
          class="formulaire"
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
        </form>
        <div pied>
          <button
            matButton
            type="button"
            (click)="modaleClasse.demanderFermeture('fermer')"
          >
            Annuler
          </button>
          <button
            matButton="filled"
            type="submit"
            form="form-classe"
            [disabled]="occupe()"
            data-testid="confirmer-classe"
          >
            Changer de classe
          </button>
        </div>
      </ecole-modale>
    }

    @if (action() === 'sortie') {
      <ecole-modale
        #modaleSortie
        titre="Changer le statut"
        sousTitre="Transfert, abandon ou radiation : l'inscription prend fin."
        testId="modale-sortie"
        [ouverte]="true"
        [modifiee]="sortie.dirty"
        (fermee)="action.set(null)"
      >
        @if (erreur(); as message) {
          <p class="message erreur" role="alert" data-testid="erreur-scolarite">
            {{ message }}
          </p>
        }
        <form
          id="form-sortie"
          [formGroup]="sortie"
          (ngSubmit)="sortir()"
          class="formulaire"
          data-testid="form-sortie"
        >
          <mat-form-field appearance="outline">
            <mat-label>Nouveau statut</mat-label>
            <mat-select formControlName="statut" data-testid="champ-sortie">
              <mat-option value="TRANSFERE">Transféré</mat-option>
              <mat-option value="ABANDON">Abandon</mat-option>
              @if (droits.peutRadier()) {
                <mat-option value="RADIE">Radié</mat-option>
              }
            </mat-select>
            @if (!droits.peutRadier()) {
              <mat-hint>La radiation est réservée au directeur.</mat-hint>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="champ-court">
            <mat-label>Date</mat-label>
            <input matInput type="date" formControlName="date" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="champ-plein">
            <mat-label>Motif</mat-label>
            <input matInput formControlName="motif" maxlength="200" />
          </mat-form-field>
          <p class="aide">Cette décision est définitive.</p>
        </form>
        <div pied>
          <button
            matButton
            type="button"
            (click)="modaleSortie.demanderFermeture('fermer')"
          >
            Annuler
          </button>
          <button
            matButton="filled"
            type="submit"
            form="form-sortie"
            [disabled]="occupe()"
            data-testid="confirmer-sortie"
          >
            {{
              sortie.controls.statut.value === 'RADIE'
                ? 'Continuer'
                : 'Confirmer'
            }}
          </button>
        </div>
      </ecole-modale>
    }

    <ecole-confirmation
      titre="Radier l'élève"
      [consequence]="consequenceRadiation()"
      action="Radier l'élève"
      motif="obligatoire"
      libelleMotif="Motif de la radiation"
      [motifInitial]="motifRadiation()"
      testId="confirmer-radiation"
      [ouverte]="radiation()"
      [occupe]="occupe()"
      [erreur]="erreurRadiation()"
      (confirmee)="radier($event)"
      (annulee)="radiation.set(false)"
    />
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class OngletScolarite {
  readonly eleveId = input.required<string>();

  private readonly service = inject(ServiceEleves);
  protected readonly droits = inject(Droits);

  protected readonly historique = signal<InscriptionDto[]>([]);
  protected readonly classes = signal<ClasseDto[]>([]);
  /** Les classes proposées au formulaire : chargées dès que l'on peut écrire, et à la demande. */
  protected readonly classesChargees = signal(false);
  protected readonly chargementClasses = signal(false);
  /** Reste affichée tant que les classes manquent : une liste vide ne s'ouvre pas, sans dire pourquoi. */
  protected readonly erreurClasses = signal<string | null>(null);
  protected readonly action = signal<Action>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly avertissements = signal<string[]>([]);
  protected readonly confirmation = signal<string | null>(null);
  protected readonly occupe = signal(false);
  /** Confirmation de la radiation, ouverte après la fermeture de la modale du statut. */
  protected readonly radiation = signal(false);
  protected readonly erreurRadiation = signal<string | null>(null);
  protected readonly motifRadiation = signal('');
  private readonly dateRadiation = signal('');
  private readonly injecteur = inject(Injector);
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

  protected readonly consequenceRadiation = computed(() => {
    const date = this.dateRadiation();
    const jour = date
      ? new Date(`${date}T00:00:00`).toLocaleDateString('fr-FR', {
          dateStyle: 'long',
        })
      : '';
    return `L'inscription de l'élève prend fin le ${jour} : il est radié de l'école. Cette décision est définitive et le motif est conservé au dossier.`;
  });

  protected readonly sortie = inject(NonNullableFormBuilder).group({
    statut: ['TRANSFERE' as StatutInscription, Validators.required],
    date: [new Date().toISOString().slice(0, 10), Validators.required],
    motif: [''],
  });

  constructor() {
    queueMicrotask(() => void this.charger());
    // Les droits arrivent avec la session : on ne les lit pas une fois pour toutes à la création.
    effect(() => {
      if (this.droits.peutEcrire()) {
        untracked(() => void this.chargerClasses());
      }
    });
  }

  private async charger(): Promise<void> {
    try {
      this.historique.set(await this.service.historique(this.eleveId()));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected async chargerClasses(): Promise<void> {
    if (this.chargementClasses() || this.classesChargees()) {
      return;
    }
    this.chargementClasses.set(true);
    this.erreurClasses.set(null);
    try {
      this.classes.set(await this.service.classes());
      this.classesChargees.set(true);
    } catch (e) {
      this.erreurClasses.set(
        `Les classes n'ont pas pu être chargées. ${messageErreur(e)}`,
      );
    } finally {
      this.chargementClasses.set(false);
    }
  }

  protected ouvrir(action: Action): void {
    this.action.set(action);
    this.erreur.set(null);
    this.sortie.reset({
      statut: 'TRANSFERE',
      date: new Date().toISOString().slice(0, 10),
      motif: '',
    });
    // Rouvrir le formulaire retente le chargement des classes s'il avait échoué.
    void this.chargerClasses();
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
    if (v.statut === 'RADIE') {
      // Jamais deux modales : on ferme celle du statut, puis la confirmation s'ouvre.
      this.motifRadiation.set(v.motif);
      this.dateRadiation.set(v.date);
      this.erreurRadiation.set(null);
      this.action.set(null);
      afterNextRender(() => this.radiation.set(true), {
        injector: this.injecteur,
      });
      return;
    }
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

  protected async radier(motif: string | undefined): Promise<void> {
    const courante = this.active();
    if (!courante) {
      return;
    }
    this.erreurRadiation.set(null);
    this.confirmation.set(null);
    this.avertissements.set([]);
    this.occupe.set(true);
    try {
      const resultat = await this.service.sortir(courante, {
        statut: 'RADIE',
        date: this.dateRadiation(),
        motif,
      });
      this.avertissements.set(resultat.avertissements ?? []);
      this.confirmation.set('Élève radié.');
      this.radiation.set(false);
      await this.charger();
    } catch (e) {
      this.erreurRadiation.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
