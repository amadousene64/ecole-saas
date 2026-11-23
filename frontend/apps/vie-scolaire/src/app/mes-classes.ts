import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  AffectationDto,
  Api,
  EleveResumeDto,
  affectationMiennes,
  inscriptionElevesDeLaClasse,
} from '@ecole/api-client';
import { EnTetePage } from '@ecole/ui';

/**
 * « Mes classes » de l'enseignant : ses affectations en cours (matière dans une classe, ou classe
 * entière à l'élémentaire) et la liste des élèves de chaque classe. Le serveur ne répond que pour
 * les classes affectées : rien d'autre n'est demandé ni affiché.
 */
@Component({
  selector: 'ecole-mes-classes',
  imports: [EnTetePage, MatButtonModule],
  template: `
    <ecole-en-tete-page
      titre="Mes classes"
      sousTitre="Classes de vos affectations en cours"
    />
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-mes-classes">
        {{ message }}
      </p>
    }
    @if (charge() && affectations().length === 0) {
      <p class="aide" data-testid="aucune-classe">
        Aucune affectation en cours : la direction ou le censeur vous affecte à
        vos classes.
      </p>
    }
    <div class="grille-cartes" role="list" data-testid="mes-classes">
      @for (a of affectations(); track a.id) {
        <section class="carte" role="listitem">
          <h2>{{ a.classe }}</h2>
          <p class="aide">
            {{ a.type === 'TITULAIRE' ? 'Maître titulaire' : a.matiere }}
          </p>
          <button
            matButton="outlined"
            type="button"
            (click)="ouvrir(a)"
            [attr.aria-expanded]="ouverte() === a.classeId"
            [attr.data-testid]="'voir-eleves-' + a.classe"
          >
            Voir les élèves<span class="sr-only"> de {{ a.classe }}</span>
          </button>
        </section>
      }
    </div>
    @if (ouverte()) {
      <h2 data-testid="titre-eleves">Élèves de {{ nomClasse() }}</h2>
      <table class="cartes" data-testid="table-eleves-classe">
        <thead>
          <tr>
            <th>Nom</th>
            <th>Prénoms</th>
            <th>Matricule</th>
          </tr>
        </thead>
        <tbody>
          @for (e of eleves(); track e.id) {
            <tr>
              <td data-label="Nom">{{ e.nom }}</td>
              <td data-label="Prénoms">{{ e.prenoms }}</td>
              <td data-label="Matricule" class="mono">{{ e.matricule }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="3" class="aide">Aucun élève inscrit.</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styleUrl: '../../../../libs/ui/src/lib/styles/ecran.scss',
})
export class MesClasses {
  private readonly api = inject(Api);

  protected readonly affectations = signal<AffectationDto[]>([]);
  protected readonly eleves = signal<EleveResumeDto[]>([]);
  protected readonly ouverte = signal<string | null>(null);
  protected readonly nomClasse = signal('');
  protected readonly charge = signal(false);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      // Une classe peut porter plusieurs matières : une carte par classe et matière.
      this.affectations.set(await this.api.invoke(affectationMiennes, {}));
    } catch {
      this.erreur.set('Vos classes n’ont pas pu être chargées. Réessayez.');
    } finally {
      this.charge.set(true);
    }
  }

  protected async ouvrir(a: AffectationDto): Promise<void> {
    if (!a.classeId) {
      return;
    }
    this.erreur.set(null);
    try {
      this.eleves.set(
        await this.api.invoke(inscriptionElevesDeLaClasse, {
          classeId: a.classeId,
        }),
      );
      this.ouverte.set(a.classeId);
      this.nomClasse.set(a.classe ?? '');
    } catch {
      this.erreur.set('La liste des élèves n’a pas pu être chargée.');
    }
  }
}
