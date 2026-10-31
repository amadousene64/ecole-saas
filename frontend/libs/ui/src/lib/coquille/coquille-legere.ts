import { Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Icone } from '../icones/icone';
import { LienNavigation } from '../modeles';
import { TEXTES } from '../textes';

/**
 * Coquille sans aucun composant Material (HTML natif + variables du thème) pour l'application
 * parent : le budget du premier chargement y est de 500 Ko, que la coquille Material (sidenav,
 * menu, overlay, boutons : environ 160 Ko) ferait dépasser. Mêmes entrées et sorties que {@link
 * Coquille}.
 */
@Component({
  selector: 'ecole-coquille-legere',
  imports: [RouterLink, RouterLinkActive, Icone],
  templateUrl: './coquille-legere.html',
  styleUrl: './coquille-legere.scss',
})
export class CoquilleLegere {
  protected readonly textes = TEXTES;

  readonly titre = input<string>(TEXTES.nomPlateforme);
  readonly liens = input<LienNavigation[]>([]);
  readonly ecole = input<string | null>(null);
  readonly utilisateur = input<string | null>(null);
  readonly plusieursEcoles = input(false);

  readonly changerEcole = output<void>();
  readonly deconnexion = output<void>();
}
