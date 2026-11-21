import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  output,
  untracked,
  viewChild,
} from '@angular/core';
import { Icone } from '../icones/icone';

/** Une étape de l'assistant ; `detail` s'affiche sous le libellé une fois l'étape faite. */
export interface EtapeAssistant {
  libelle: string;
  detail?: string | null;
}

/**
 * Assistant en étapes, en écran plein (maquettes 08, 09, 10) : en-tête (fermer, titre, sous-titre,
 * actions), étapes numérotées (barre de progression sur téléphone), contenu de 720 px centré avec
 * le titre de l'étape, pied collé en bas (« Étape n sur N » et les boutons projetés `[pied]`).
 *
 *   <ecole-assistant titre="Nouvelle inscription" [etapes]="etapes" [courante]="etape()"
 *       titreEtape="Responsables de l'élève" (fermer)="quitter()" (allerA)="aller($event)">
 *     <button actions-entete matButton="outlined">Enregistrer et quitter</button>
 *     … contenu de l'étape …
 *     <div pied><button matButton="outlined">Précédent</button> <button …>Continuer</button></div>
 *   </ecole-assistant>
 *
 * À afficher sur une route `data: { pleinEcran: true }` (la coquille retire alors sa barre et sa
 * navigation). Le titre de l'étape (h1) reçoit le focus à chaque changement d'étape : un lecteur
 * d'écran annonce la nouvelle étape. Les étapes déjà faites sont des boutons (`allerA`).
 */
@Component({
  selector: 'ecole-assistant',
  imports: [Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './assistant.html',
  styleUrl: './assistant.scss',
})
export class Assistant {
  readonly titre = input.required<string>();
  readonly sousTitre = input<string | null>(null);
  readonly etapes = input.required<readonly EtapeAssistant[]>();
  /** Étape affichée, de 1 à etapes().length. */
  readonly courante = input.required<number>();
  /** Dernière étape atteinte : les étapes jusqu'à elle sont navigables. Par défaut, la courante. */
  readonly atteinte = input<number | null>(null);
  readonly titreEtape = input.required<string>();
  readonly sousTitreEtape = input<string | null>(null);
  readonly libelleFermer = input('Fermer l’assistant');

  readonly fermer = output<void>();
  readonly allerA = output<number>();

  private readonly titreH1 = viewChild<ElementRef<HTMLElement>>('titreEtapeH1');

  protected readonly total = computed(() => this.etapes().length);
  protected readonly derniere = computed(() =>
    Math.max(this.atteinte() ?? 0, this.courante()),
  );
  protected readonly libelleCourant = computed(
    () => this.etapes()[this.courante() - 1]?.libelle ?? '',
  );
  protected readonly suivante = computed(
    () => this.etapes()[this.courante()]?.libelle ?? null,
  );
  protected readonly progression = computed(
    () => (100 * this.courante()) / Math.max(1, this.total()),
  );

  private premiere = true;

  constructor() {
    effect(() => {
      this.courante();
      // Au premier affichage, le focus reste où le navigateur l'a mis (lien d'arrivée).
      if (this.premiere) {
        this.premiere = false;
        return;
      }
      untracked(() =>
        queueMicrotask(() => this.titreH1()?.nativeElement.focus()),
      );
    });
  }

  protected etat(numero: number): 'faite' | 'courante' | 'a-venir' {
    if (numero === this.courante()) {
      return 'courante';
    }
    return numero <= this.derniere() ? 'faite' : 'a-venir';
  }
}
