import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  DOCUMENT,
  ElementRef,
  inject,
  input,
  model,
  output,
  signal,
  untracked,
  viewChild,
  ViewEncapsulation,
} from '@angular/core';
import { Modales } from './modales';

/** Pourquoi la modale s'est fermée à la demande de l'utilisateur. */
export type RaisonFermeture = 'fermer' | 'echap' | 'fond' | 'abandon';

/** Éléments que la touche Tab peut atteindre. */
const FOCALISABLES = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

let suivant = 0;

/**
 * Modale sur l'élément natif <dialog> (`showModal()`) : le reste de la page devient inerte, le
 * navigateur la place au-dessus de tout (les listes de mat-select s'ouvrent dans la modale).
 *
 * - En-tête : titre, sous-titre, bouton « Fermer » ; corps défilant ; pied à droite
 *   (`<div pied>Annuler, puis l'action principale</div>`).
 * - Échap, le bouton « Fermer » et un clic sur le fond ferment ; avec des changements non
 *   enregistrés (`[modifiee]`), une confirmation s'affiche DANS la modale (jamais deux modales).
 * - Focus : premier champ du corps (ou `focusInitial`) à l'ouverture, piégé dans la modale,
 *   rendu à l'élément d'origine à la fermeture.
 * - Plein écran sous 600 px.
 *
 * Ouverture : `[(ouverte)]` ; la modale ne se ferme d'elle-même que sur une demande de
 * l'utilisateur (`fermee`). Après un enregistrement réussi, le parent remet `ouverte` à faux.
 */
@Component({
  selector: 'ecole-modale',
  templateUrl: './modale.html',
  styleUrl: './modale.scss',
  // Styles non encapsulés (préfixe .ecole-modale) : le pied met en forme le contenu projeté.
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Modale {
  readonly titre = input.required<string>();
  readonly sousTitre = input<string>();
  readonly ouverte = model(false);
  /** Changements non enregistrés : fermer demande d'abord confirmation. */
  readonly modifiee = input(false);
  /** Textes de cette confirmation, si « modifications non enregistrées » ne convient pas. */
  readonly messageAbandon = input(
    'Vos modifications ne sont pas enregistrées. Fermer quand même ?',
  );
  readonly libelleContinuer = input('Continuer la saisie');
  readonly libelleAbandon = input('Abandonner les modifications');
  /** `alertdialog` pour une confirmation d'action. */
  readonly role = input<'dialog' | 'alertdialog'>('dialog');
  readonly largeur = input<'etroite' | 'normale' | 'large'>('normale');
  /** `haut` : posée vers le haut de l'écran (recherche). */
  readonly position = input<'centre' | 'haut'>('centre');
  /** Sélecteur, dans la modale, de l'élément à focaliser à l'ouverture. */
  readonly focusInitial = input<string>();
  /** Identifiant d'un élément du corps qui décrit la modale (à défaut du sous-titre). */
  readonly decritPar = input<string>();
  readonly testId = input<string>();
  readonly fermee = output<RaisonFermeture>();

  private readonly modales = inject(Modales);
  private readonly document = inject(DOCUMENT);
  private readonly dialogue =
    viewChild.required<ElementRef<HTMLDialogElement>>('dialogue');
  private readonly boutonContinuer =
    viewChild<ElementRef<HTMLButtonElement>>('continuer');

  protected readonly confirmerAbandon = signal(false);
  protected readonly id = `ecole-modale-${++suivant}`;
  private origine: HTMLElement | null = null;
  private retourFormulaire = false;
  /** Élément sous le pointeur à l'appui : un glisser depuis le corps ne ferme pas. */
  private appui: EventTarget | null = null;

  constructor() {
    afterRenderEffect({
      write: () => {
        const ouverte = this.ouverte();
        untracked(() => (ouverte ? this.afficher() : this.masquer()));
      },
    });
    // Focus après rendu : sur « Continuer la saisie » quand la confirmation s'affiche, sur le
    // premier champ quand on revient au formulaire (le corps n'est visible qu'une fois rendu).
    afterRenderEffect({
      write: () => {
        const confirmer = this.confirmerAbandon();
        untracked(() => {
          if (confirmer) {
            this.boutonContinuer()?.nativeElement.focus();
          } else if (this.retourFormulaire) {
            this.retourFormulaire = false;
            this.focaliserCorps();
          }
        });
      },
    });
    inject(DestroyRef).onDestroy(() => {
      this.masquer();
      // Déplacé sous <body> à l'ouverture : Angular ne l'y retrouverait pas.
      this.dialogue().nativeElement.remove();
    });
  }

  /** Fermeture demandée par l'utilisateur (Échap, bouton, fond). */
  demanderFermeture(raison: RaisonFermeture): void {
    if (this.modifiee() && !this.confirmerAbandon()) {
      this.confirmerAbandon.set(true);
      return;
    }
    this.fermer(raison);
  }

  protected fermer(raison: RaisonFermeture): void {
    this.confirmerAbandon.set(false);
    this.ouverte.set(false);
    this.fermee.emit(raison);
  }

  protected continuerSaisie(): void {
    this.retourFormulaire = true;
    this.confirmerAbandon.set(false);
  }

  /** Échap : le navigateur annule ; c'est la modale qui décide. */
  protected surAnnulation(evenement: Event): void {
    evenement.preventDefault();
    this.demanderFermeture('echap');
  }

  /**
   * Fermeture native sans notre accord (Échap répété : le navigateur n'annule plus) : avec des
   * changements, on rouvre sur la confirmation ; sinon on suit.
   */
  protected surFermetureNative(): void {
    if (!this.ouverte()) {
      return;
    }
    if (this.modifiee()) {
      ouvrirNatif(this.dialogue().nativeElement);
      this.confirmerAbandon.set(true);
      return;
    }
    this.fermer('echap');
  }

  protected surAppui(evenement: PointerEvent): void {
    this.appui = evenement.target;
  }

  /** Clic sur le fond (hors du cadre) : appui et relâchement sur le <dialog> lui-même. */
  protected surClic(evenement: MouseEvent): void {
    const dialogue = this.dialogue().nativeElement;
    if (evenement.target === dialogue && this.appui === dialogue) {
      this.demanderFermeture('fond');
    }
    this.appui = null;
  }

  /** Tab et Maj+Tab bouclent dans la modale (le navigateur laisserait sortir vers sa barre). */
  protected surTouche(evenement: KeyboardEvent): void {
    if (evenement.key !== 'Tab') {
      return;
    }
    const elements = this.focalisables();
    if (elements.length === 0) {
      evenement.preventDefault();
      return;
    }
    const premier = elements[0];
    const dernier = elements[elements.length - 1];
    const actif = this.document.activeElement;
    if (evenement.shiftKey && (actif === premier || !this.contient(actif))) {
      evenement.preventDefault();
      dernier.focus();
    } else if (!evenement.shiftKey && actif === dernier) {
      evenement.preventDefault();
      premier.focus();
    }
  }

  private afficher(): void {
    const dialogue = this.dialogue().nativeElement;
    if (dialogue.hasAttribute('open')) {
      return;
    }
    if (!this.modales.reserver(this)) {
      // Une autre modale est ouverte : on reste fermée.
      this.ouverte.set(false);
      return;
    }
    const actif = this.document.activeElement;
    this.origine = actif instanceof HTMLElement ? actif : null;
    this.confirmerAbandon.set(false);
    // Sous <body>, hors des conteneurs à défilement (onglets, contenu de page) : le navigateur
    // place déjà la modale au-dessus de tout, mais les outils qui lisent le DOM (axe, lecteurs
    // d'écran) jugeraient sinon son contenu masqué par l'overflow d'un ancêtre. Le contenu projeté
    // et les écouteurs suivent l'élément.
    if (dialogue.parentElement !== this.document.body) {
      this.document.body.appendChild(dialogue);
    }
    ouvrirNatif(dialogue);
    this.focaliserCorps();
  }

  private masquer(): void {
    this.modales.liberer(this);
    const dialogue = this.dialogue().nativeElement;
    if (!dialogue.hasAttribute('open')) {
      return;
    }
    fermerNatif(dialogue);
    const origine = this.origine;
    this.origine = null;
    if (origine?.isConnected) {
      origine.focus();
    }
  }

  private focaliserCorps(): void {
    const dialogue = this.dialogue().nativeElement;
    const voulu = this.focusInitial();
    const cible =
      (voulu ? dialogue.querySelector<HTMLElement>(voulu) : null) ??
      dialogue.querySelector<HTMLElement>(
        `.ecole-modale__corps :is(${FOCALISABLES})`,
      ) ??
      dialogue.querySelector<HTMLElement>(
        `.ecole-modale__pied :is(${FOCALISABLES})`,
      ) ??
      dialogue.querySelector<HTMLElement>('.ecole-modale__fermer');
    cible?.focus();
  }

  private focalisables(): HTMLElement[] {
    return [
      ...this.dialogue().nativeElement.querySelectorAll<HTMLElement>(
        FOCALISABLES,
      ),
    ].filter((e) => !e.closest('[hidden]') && e.getClientRects().length > 0);
  }

  private contient(element: Element | null): boolean {
    return !!element && this.dialogue().nativeElement.contains(element);
  }
}

/**
 * showModal() / close(), avec un repli pour jsdom (tests unitaires des écrans), qui n'implémente
 * pas <dialog> : l'attribut `open` suffit à y rendre le contenu. Le vrai comportement (modale,
 * focus, Échap) est vérifié dans Chromium (modale.navigateur.spec.ts).
 */
function ouvrirNatif(dialogue: HTMLDialogElement): void {
  if (typeof dialogue.showModal === 'function') {
    dialogue.showModal();
  } else {
    dialogue.setAttribute('open', '');
  }
}

function fermerNatif(dialogue: HTMLDialogElement): void {
  if (typeof dialogue.close === 'function') {
    dialogue.close();
  } else {
    dialogue.removeAttribute('open');
  }
}
