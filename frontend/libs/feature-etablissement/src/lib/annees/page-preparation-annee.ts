import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AnneeScolaireDto } from '@ecole/api-client';
import {
  Assistant,
  CHAMPS_FORMULAIRE,
  Champ,
  Confirmation,
  EtapeAssistant,
} from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { ServiceAnnees } from '../services/service-annees';
import { SectionCalendrier } from './section-calendrier';
import { SectionPeriodes } from './section-periodes';

const TEXTES: readonly { titre: string; sousTitre: string }[] = [
  {
    titre: "L'année scolaire",
    sousTitre:
      'Libellé et dates. La création génère les périodes de chaque cycle et propose les jours fériés.',
  },
  {
    titre: 'Périodes',
    sousTitre:
      'Trimestres ou semestres selon le cycle : ajustez les dates si besoin.',
  },
  {
    titre: 'Calendrier',
    sousTitre:
      'Jours fériés et vacances. Les fêtes musulmanes dépendent de la lune : ajoutez-les à la main.',
  },
  {
    titre: "Ouverture de l'année",
    sousTitre:
      "Tant qu'elle est en préparation, l'année se modifie librement. Ouverte, elle reçoit les inscriptions.",
  },
];

/**
 * Assistant de préparation d'une année scolaire (écran plein, même composant que les assistants
 * d'inscription et d'import) : Année, Périodes, Calendrier, Ouverture. Pas de brouillon : l'année
 * est créée « en préparation » dès la première étape (avec ses périodes et jours fériés générés),
 * puis complétée ; seul le directeur l'ouvre, après confirmation.
 *
 * `/etablissement/annees/preparation` crée une année ; `…/preparation/:anneeId` reprend une année
 * en préparation.
 */
@Component({
  selector: 'ecole-page-preparation-annee',
  providers: [CHAMPS_FORMULAIRE],
  imports: [
    Assistant,
    Champ,
    Confirmation,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    SectionCalendrier,
    SectionPeriodes,
  ],
  template: `
    <ecole-assistant
      titre="Préparer une année scolaire"
      [sousTitre]="
        annee() ? 'Année ' + annee()!.libelle + ' · en préparation' : null
      "
      [etapes]="etapes()"
      [courante]="etape()"
      [atteinte]="annee() ? 4 : 1"
      [titreEtape]="texte().titre"
      [sousTitreEtape]="texte().sousTitre"
      libelleFermer="Fermer la préparation"
      (fermer)="quitter()"
      (allerA)="etape.set($event)"
    >
      @if (erreur(); as message) {
        <p class="message erreur" role="alert" data-testid="erreur-preparation">
          {{ message }}
        </p>
      }

      @switch (etape()) {
        @case (1) {
          <form
            [formGroup]="formulaire"
            class="formulaire carte"
            data-testid="form-annee"
            (ngSubmit)="continuer()"
          >
            <ecole-champ libelle="Année (AAAA-AAAA)" class="champ-plein">
              <mat-form-field appearance="outline" class="champ-moyen">
                <input
                  matInput
                  formControlName="libelle"
                  placeholder="2027-2028"
                  data-testid="champ-annee-libelle"
                />
                @if (formulaire.controls.libelle.hasError('pattern')) {
                  <mat-error
                    >Format AAAA-AAAA, par exemple 2027-2028.</mat-error
                  >
                }
              </mat-form-field>
            </ecole-champ>
            <ecole-champ libelle="Rentrée" class="champ-court">
              <mat-form-field appearance="outline">
                <input
                  matInput
                  type="date"
                  formControlName="dateDebut"
                  data-testid="champ-annee-debut"
                />
              </mat-form-field>
            </ecole-champ>
            <ecole-champ libelle="Fin des cours" class="champ-court">
              <mat-form-field appearance="outline">
                <input
                  matInput
                  type="date"
                  formControlName="dateFin"
                  data-testid="champ-annee-fin"
                />
              </mat-form-field>
            </ecole-champ>
            <button
              type="submit"
              hidden
              tabindex="-1"
              aria-hidden="true"
            ></button>
          </form>
        }
        @case (2) {
          <ecole-section-periodes [annee]="annee()!" />
        }
        @case (3) {
          <ecole-section-calendrier [annee]="annee()!" />
        }
        @case (4) {
          <section class="carte" data-testid="recap-annee">
            <dl class="recap">
              <div>
                <dt>Année</dt>
                <dd>{{ annee()?.libelle }}</dd>
              </div>
              <div>
                <dt>Du</dt>
                <dd>{{ annee()?.dateDebut | date: 'd MMMM y' }}</dd>
              </div>
              <div>
                <dt>Au</dt>
                <dd>{{ annee()?.dateFin | date: 'd MMMM y' }}</dd>
              </div>
            </dl>
            @if (droits.estDirecteur()) {
              <p class="aide">
                L'ouverture fige les paramètres de notation des cycles et des
                niveaux pour cette année. Une seule année peut être en cours.
              </p>
            } @else {
              <p class="aide" data-testid="ouverture-directeur">
                L'ouverture de l'année est réservée au directeur. Elle reste en
                préparation.
              </p>
            }
          </section>
        }
      }

      <div pied>
        @if (etape() > 1) {
          <button
            matButton="outlined"
            type="button"
            (click)="etape.set(etape() - 1)"
            data-testid="etape-precedente"
          >
            Précédent
          </button>
        } @else {
          <a matButton="outlined" routerLink="/etablissement/annees">Annuler</a>
        }
        @if (etape() < 4) {
          <button
            matButton="filled"
            type="button"
            [disabled]="occupe()"
            (click)="continuer()"
            data-testid="etape-suivante"
          >
            {{ etape() === 1 && !annee() ? "Créer l'année" : 'Continuer' }}
          </button>
        } @else if (droits.estDirecteur()) {
          <a
            matButton="outlined"
            routerLink="/etablissement/annees"
            data-testid="garder-preparation"
            >Garder en préparation</a
          >
          <button
            matButton="filled"
            type="button"
            (click)="confirmerOuverture.set(true)"
            data-testid="ouvrir-annee"
          >
            Ouvrir l'année
          </button>
        } @else {
          <a
            matButton="filled"
            routerLink="/etablissement/annees"
            data-testid="terminer-preparation"
            >Terminer</a
          >
        }
      </div>
    </ecole-assistant>

    @if (confirmerOuverture()) {
      <ecole-confirmation
        [titre]="titreOuverture()"
        consequence="Les paramètres de notation des cycles et des niveaux sont copiés pour cette année et ne changeront plus. Une seule année peut être en cours."
        action="Ouvrir l'année"
        testId="confirmer-ouverture"
        [ouverte]="true"
        [occupe]="occupe()"
        [erreur]="erreurOuverture()"
        (confirmee)="ouvrir()"
        (annulee)="confirmerOuverture.set(false)"
      />
    }
  `,
  styleUrls: [
    '../../../../ui/src/lib/styles/ecran.scss',
    './page-preparation-annee.scss',
  ],
})
export class PagePreparationAnnee implements OnInit {
  private readonly service = inject(ServiceAnnees);
  private readonly routeur = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly droits = inject(Droits);

  protected readonly annee = signal<AnneeScolaireDto | null>(null);
  protected readonly etape = signal(1);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurOuverture = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly confirmerOuverture = signal(false);

  protected readonly texte = computed(() => TEXTES[this.etape() - 1]);
  protected readonly titreOuverture = computed(() =>
    `Ouvrir l'année ${this.annee()?.libelle ?? ''}`.trim(),
  );
  protected readonly etapes = computed<EtapeAssistant[]>(() => [
    { libelle: 'Année', detail: this.annee()?.libelle ?? null },
    { libelle: 'Périodes' },
    { libelle: 'Calendrier' },
    { libelle: 'Ouverture' },
  ]);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    libelle: ['', [Validators.required, Validators.pattern(/^\d{4}-\d{4}$/)]],
    dateDebut: ['', Validators.required],
    dateFin: ['', Validators.required],
  });

  async ngOnInit(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('anneeId');
    if (!id) {
      return;
    }
    try {
      const annee = (await this.service.annees()).find((a) => a.id === id);
      if (!annee || annee.statut !== 'PREPARATION') {
        this.erreur.set("Cette année n'est pas en préparation.");
        return;
      }
      this.appliquer(annee);
      this.etape.set(2);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  private appliquer(annee: AnneeScolaireDto): void {
    this.annee.set(annee);
    this.formulaire.reset({
      libelle: annee.libelle ?? '',
      dateDebut: annee.dateDebut ?? '',
      dateFin: annee.dateFin ?? '',
    });
  }

  /** Étape 1 : crée l'année (ou enregistre ses dates si elles ont changé), puis avance. */
  protected async continuer(): Promise<void> {
    if (this.etape() > 1) {
      this.etape.update((e) => e + 1);
      return;
    }
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const existante = this.annee();
      const saisie = this.formulaire.getRawValue();
      if (!existante) {
        const creee = await this.service.creer(saisie);
        this.appliquer(creee);
        // Un rechargement reprend cette année plutôt que d'en créer une autre.
        void this.routeur.navigate(
          ['/etablissement/annees/preparation', creee.id],
          {
            replaceUrl: true,
          },
        );
      } else if (this.formulaire.dirty) {
        this.appliquer(await this.service.modifier(existante, saisie));
      }
      this.etape.set(2);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async ouvrir(): Promise<void> {
    const annee = this.annee();
    if (!annee) {
      return;
    }
    this.erreurOuverture.set(null);
    this.occupe.set(true);
    try {
      await this.service.ouvrir(annee);
      await this.routeur.navigate(['/etablissement/annees']);
    } catch (e) {
      this.erreurOuverture.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected quitter(): void {
    void this.routeur.navigate(['/etablissement/annees']);
  }
}
