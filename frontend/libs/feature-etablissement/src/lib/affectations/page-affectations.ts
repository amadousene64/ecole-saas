import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import {
  AffectationDto,
  AnneeScolaireDto,
  ClasseDto,
  MatiereDto,
  MembreDto,
} from '@ecole/api-client';
import {
  CHAMPS_FORMULAIRE,
  Champ,
  Confirmation,
  EnTetePage,
  Pastille,
} from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import {
  ServiceAffectations,
  libelleAffectation,
  nomMembre,
} from '../services/service-affectations';
import { ServiceAnnees } from '../services/service-annees';
import { ServiceClasses } from '../services/service-classes';
import { ServiceEnseignements } from '../services/service-enseignements';
import { ServiceStructure } from '../services/service-structure';
import { ModaleAffectation } from './modale-affectation';
import { ModalePeriode } from './modale-periode';

/**
 * Affectations de l'année : enseignants (matière dans une classe, maître titulaire) et
 * surveillants (cycles). Elles fixent ce que chacun voit, dès leur enregistrement. Une affectation
 * se termine (effet immédiat) ou se prévoit jusqu'à une date ; l'historique reste.
 */
@Component({
  selector: 'ecole-page-affectations',
  providers: [CHAMPS_FORMULAIRE],
  imports: [
    Champ,
    Confirmation,
    DatePipe,
    EnTetePage,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatSelectModule,
    ModaleAffectation,
    ModalePeriode,
    Pastille,
  ],
  template: `
    <ecole-en-tete-page
      titre="Affectations"
      sousTitre="Chacun ne voit que les classes et les cycles qui lui sont affectés"
    >
      <button
        actions
        matButton="filled"
        type="button"
        [disabled]="!annee()"
        (click)="ajout.set(true)"
        data-testid="nouvelle-affectation"
      >
        Affecter
      </button>
    </ecole-en-tete-page>

    <div
      class="filtres-liste"
      role="search"
      aria-label="Filtres des affectations"
    >
      <ecole-champ libelle="Année scolaire">
        <mat-form-field appearance="outline">
          <mat-select
            [value]="annee()?.id"
            (selectionChange)="choisirAnnee($event.value)"
            data-testid="filtre-annee-affectations"
          >
            @for (a of annees(); track a.id) {
              <mat-option [value]="a.id">{{ a.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </ecole-champ>
      <mat-checkbox
        [checked]="historique()"
        (change)="historique.set($event.checked)"
        data-testid="afficher-historique"
      >
        Afficher les affectations terminées
      </mat-checkbox>
    </div>

    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-affectations">
        {{ message }}
      </p>
    }

    <table data-testid="table-affectations" class="cartes">
      <thead>
        <tr>
          <th>Personne</th>
          <th>Affectation</th>
          <th>Période</th>
          <th>État</th>
          <th><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        @for (a of affichees(); track a.id) {
          <tr [attr.data-testid]="'affectation-' + libelle(a)">
            <td data-label="Personne">{{ nom(a) }}</td>
            <td data-label="Affectation">{{ libelle(a) }}</td>
            <td data-label="Période">
              du {{ a.debut | date: 'dd/MM/yyyy' }}
              @if (a.fin) {
                · prend fin le {{ a.fin | date: 'dd/MM/yyyy' }}
              }
            </td>
            <td data-label="État">
              @if (a.enCours) {
                <ecole-pastille ton="primaire">En cours</ecole-pastille>
              } @else if (aVenir(a)) {
                <ecole-pastille ton="fleuve">À venir</ecole-pastille>
              } @else {
                <ecole-pastille ton="neutre">Terminée</ecole-pastille>
              }
            </td>
            <td data-label="Actions">
              @if (a.enCours || aVenir(a)) {
                <button matButton type="button" (click)="periode.set(a)">
                  Modifier<span class="sr-only"> {{ libelle(a) }}</span>
                </button>
                <button
                  matButton
                  type="button"
                  (click)="aTerminer.set(a)"
                  [attr.data-testid]="'terminer-' + libelle(a)"
                >
                  Terminer<span class="sr-only"> {{ libelle(a) }}</span>
                </button>
              }
            </td>
          </tr>
        } @empty {
          <tr>
            <td colspan="5" class="aide">
              Aucune affectation pour cette année.
            </td>
          </tr>
        }
      </tbody>
    </table>

    @if (ajout() && annee(); as an) {
      <ecole-modale-affectation
        [annee]="an"
        [membres]="membres()"
        [classes]="classes()"
        [cycleDeClasse]="cycleDeClasse()"
        [matieres]="matieres()"
        [debutPropose]="debutPropose()"
        (enregistree)="apresEcriture()"
        (fermee)="ajout.set(false)"
      />
    }
    @if (periode(); as a) {
      <ecole-modale-periode
        [affectation]="a"
        (enregistree)="apresEcriture()"
        (fermee)="periode.set(null)"
      />
    }
    @if (aTerminer(); as a) {
      <ecole-confirmation
        titre="Terminer l'affectation"
        [consequence]="
          nom(a) +
          ' : ' +
          libelle(a) +
          '. Elle prend fin aujourd’hui : la personne ne voit plus ces élèves dès maintenant. Pour un remplacement prévu, modifiez plutôt la période.'
        "
        action="Terminer"
        testId="confirmer-fin-affectation"
        [ouverte]="true"
        [occupe]="occupe()"
        [erreur]="erreurFin()"
        (confirmee)="terminer(a)"
        (annulee)="aTerminer.set(null)"
      />
    }
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class PageAffectations {
  private readonly service = inject(ServiceAffectations);
  private readonly serviceAnnees = inject(ServiceAnnees);
  private readonly serviceClasses = inject(ServiceClasses);
  private readonly enseignements = inject(ServiceEnseignements);
  private readonly structure = inject(ServiceStructure);

  protected readonly annees = signal<AnneeScolaireDto[]>([]);
  protected readonly annee = signal<AnneeScolaireDto | null>(null);
  protected readonly affectations = signal<AffectationDto[]>([]);
  protected readonly membres = signal<MembreDto[]>([]);
  protected readonly classes = signal<ClasseDto[]>([]);
  protected readonly matieres = signal<MatiereDto[]>([]);
  protected readonly cycleDeClasse = signal<Record<string, string>>({});
  protected readonly historique = signal(false);
  protected readonly ajout = signal(false);
  protected readonly periode = signal<AffectationDto | null>(null);
  protected readonly aTerminer = signal<AffectationDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurFin = signal<string | null>(null);
  protected readonly occupe = signal(false);

  /** Aujourd'hui (fuseau du navigateur), au format des dates de l'API. */
  private readonly aujourdhui = new Date().toLocaleDateString('sv-SE');

  protected readonly affichees = computed(() =>
    this.historique()
      ? this.affectations()
      : this.affectations().filter((a) => a.enCours || this.aVenir(a)),
  );
  protected readonly debutPropose = computed(() => {
    const debut = this.annee()?.dateDebut ?? this.aujourdhui;
    return debut > this.aujourdhui ? debut : this.aujourdhui;
  });

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      const [annees, membres, matieres, niveaux, cycles] = await Promise.all([
        this.serviceAnnees.annees(),
        this.service.affectables(),
        this.enseignements.matieres(),
        this.structure.niveaux(),
        this.structure.cycles(),
      ]);
      this.annees.set(annees);
      this.membres.set(membres);
      this.matieres.set(matieres);
      const codeCycle = new Map(cycles.map((c) => [c.id, c.code]));
      this.cycleNiveau = new Map(
        niveaux.map((n) => [n.id ?? '', codeCycle.get(n.cycleId) ?? '']),
      );
      const initiale = annees.find((a) => a.statut === 'EN_COURS') ?? annees[0];
      if (initiale?.id) {
        await this.choisirAnnee(initiale.id);
      }
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  private cycleNiveau = new Map<string, string>();

  protected async choisirAnnee(id: string): Promise<void> {
    this.annee.set(this.annees().find((a) => a.id === id) ?? null);
    this.erreur.set(null);
    try {
      const [affectations, classes] = await Promise.all([
        this.service.lister(id),
        this.serviceClasses.classes(id),
      ]);
      this.affectations.set(affectations);
      this.classes.set(classes);
      this.cycleDeClasse.set(
        Object.fromEntries(
          classes.map((c) => [
            c.id ?? '',
            this.cycleNiveau.get(c.niveauId ?? '') ?? '',
          ]),
        ),
      );
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected aVenir(a: AffectationDto): boolean {
    return !a.enCours && (a.debut ?? '') > this.aujourdhui;
  }

  protected nom(a: AffectationDto): string {
    return nomMembre(
      this.membres().find((m) => m.utilisateurId === a.utilisateurId),
      a.utilisateurId,
    );
  }

  protected libelle(a: AffectationDto): string {
    return libelleAffectation(a);
  }

  protected async apresEcriture(): Promise<void> {
    this.ajout.set(false);
    this.periode.set(null);
    const id = this.annee()?.id;
    if (id) {
      await this.choisirAnnee(id);
    }
  }

  protected async terminer(a: AffectationDto): Promise<void> {
    this.erreurFin.set(null);
    this.occupe.set(true);
    try {
      await this.service.terminer(a);
      this.aTerminer.set(null);
      await this.apresEcriture();
    } catch (e) {
      this.erreurFin.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
