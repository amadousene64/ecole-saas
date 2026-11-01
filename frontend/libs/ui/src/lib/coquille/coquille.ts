import { BreakpointObserver } from '@angular/cdk/layout';
import {
  afterNextRender,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatSidenavModule } from '@angular/material/sidenav';
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
} from '@angular/router';
import { filter, map } from 'rxjs';
import { Icone } from '../icones/icone';
import { LienNavigation } from '../modeles';
import { ZoneNotifications } from '../notifications/notifications';
import { TEXTES } from '../textes';

/** Les trois modes de la barre latérale. */
export type ModeNavigation = 'tiroir' | 'icones' | 'complet';

interface Section {
  titre: string | null;
  liens: LienNavigation[];
}

/**
 * Mise en page commune du personnel :
 * - sous 600 px, navigation en tiroir ;
 * - de 600 à 1024 px, barre latérale réduite aux icônes ;
 * - au-delà, barre latérale complète.
 * La barre du haut porte la recherche globale (Ctrl+K), le menu du compte (nom et rôle) et les
 * notifications courtes en bas d'écran. Le contenu de la page est projeté.
 */
@Component({
  selector: 'ecole-coquille',
  host: {
    '(document:keydown)': 'clavierMenuCompte($event)',
    '(document:focusin)': 'interactionHorsMenu($event)',
    '(document:click)': 'interactionHorsMenu($event)',
  },
  imports: [
    MatSidenavModule,
    RouterLink,
    RouterLinkActive,
    Icone,
    ZoneNotifications,
  ],
  templateUrl: './coquille.html',
  styleUrl: './coquille.scss',
})
export class Coquille {
  protected readonly textes = TEXTES;

  readonly titre = input<string>(TEXTES.nomPlateforme);
  readonly liens = input<LienNavigation[]>([]);
  /** Nom de l'école courante ; absent tant qu'aucune école n'est choisie. */
  readonly ecole = input<string | null>(null);
  readonly utilisateur = input<string | null>(null);
  /** Rôle affiché sous le nom dans le menu du compte. */
  readonly role = input<string | null>(null);
  /** Afficher « Changer d'école » (utilisateur rattaché à plusieurs écoles). */
  readonly plusieursEcoles = input(false);
  /** Afficher le bouton de recherche globale. */
  readonly recherche = input(false);

  readonly changerEcole = output<void>();
  readonly deconnexion = output<void>();
  readonly rechercher = output<void>();

  private readonly etat = toSignal(
    inject(BreakpointObserver)
      .observe(['(max-width: 599.98px)', '(max-width: 1023.98px)'])
      .pipe(map((e) => e.breakpoints)),
    { initialValue: {} as Record<string, boolean> },
  );

  protected readonly mode = computed<ModeNavigation>(() => {
    const e = this.etat();
    if (e['(max-width: 599.98px)']) {
      return 'tiroir';
    }
    return e['(max-width: 1023.98px)'] ? 'icones' : 'complet';
  });

  protected readonly menuOuvert = signal(false);
  protected readonly menuCompteOuvert = signal(false);

  private readonly boutonCompte =
    viewChild<ElementRef<HTMLButtonElement>>('boutonCompte');
  private readonly conteneurCompte =
    viewChild<ElementRef<HTMLElement>>('conteneurCompte');
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injecteur = inject(Injector);

  /** Entrées regroupées par section, dans l'ordre d'apparition. */
  protected readonly sections = computed<Section[]>(() => {
    const sections: Section[] = [];
    for (const lien of this.liens()) {
      const titre = lien.groupe ?? null;
      const derniere = sections.at(-1);
      if (derniere && derniere.titre === titre) {
        derniere.liens.push(lien);
      } else {
        sections.push({ titre, liens: [lien] });
      }
    }
    return sections;
  });

  constructor() {
    // Le tiroir se referme à chaque navigation.
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        this.menuOuvert.set(false);
        this.menuCompteOuvert.set(false);
      });
  }

  protected basculerMenu(): void {
    this.menuOuvert.update((ouvert) => !ouvert);
  }

  /**
   * Menu du compte : un menu simple (disclosure) sans la couche de superposition de Material, que le
   * premier écran n'a pas à charger. Ouvert, le focus va sur le premier élément ; Échap le referme et
   * rend le focus au bouton ; Tab, un clic ailleurs ou un changement de page le ferment aussi ;
   * flèches, Début et Fin parcourent les éléments.
   */
  protected basculerMenuCompte(): void {
    if (this.menuCompteOuvert()) {
      this.fermerMenuCompte(false);
      return;
    }
    this.menuCompteOuvert.set(true);
    afterNextRender(() => this.elementsMenu()[0]?.focus(), {
      injector: this.injecteur,
    });
  }

  protected choisirDansMenu(choix: 'ecole' | 'deconnexion'): void {
    this.fermerMenuCompte(false);
    if (choix === 'ecole') {
      this.changerEcole.emit();
    } else {
      this.deconnexion.emit();
    }
  }

  protected clavierMenuCompte(evenement: KeyboardEvent): void {
    if (!this.menuCompteOuvert()) {
      return;
    }
    if (evenement.key === 'Escape') {
      evenement.preventDefault();
      this.fermerMenuCompte(true);
      return;
    }
    const elements = this.elementsMenu();
    const courant = elements.indexOf(document.activeElement as HTMLElement);
    let cible: number | null = null;
    switch (evenement.key) {
      case 'ArrowDown':
        cible = (courant + 1) % elements.length;
        break;
      case 'ArrowUp':
        cible = (courant - 1 + elements.length) % elements.length;
        break;
      case 'Home':
        cible = 0;
        break;
      case 'End':
        cible = elements.length - 1;
        break;
    }
    if (cible !== null && elements.length > 0) {
      evenement.preventDefault();
      elements[cible].focus();
    }
  }

  /** Focus ou clic hors du menu (Tab, clic ailleurs) : il se referme sans voler le focus. */
  protected interactionHorsMenu(evenement: Event): void {
    if (!this.menuCompteOuvert()) {
      return;
    }
    const conteneur = this.conteneurCompte()?.nativeElement;
    if (!conteneur?.contains(evenement.target as Node)) {
      this.fermerMenuCompte(false);
    }
  }

  private fermerMenuCompte(rendreLeFocus: boolean): void {
    this.menuCompteOuvert.set(false);
    if (rendreLeFocus) {
      this.boutonCompte()?.nativeElement.focus();
    }
  }

  private elementsMenu(): HTMLElement[] {
    return Array.from(
      this.hote.nativeElement.querySelectorAll<HTMLElement>(
        '#menu-compte [role="menuitem"]',
      ),
    );
  }
}
