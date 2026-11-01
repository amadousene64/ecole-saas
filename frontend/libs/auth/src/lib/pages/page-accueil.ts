import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EnTetePage, Icone } from '@ecole/ui';
import { Acces } from '../acces';
import { Session } from '../session';
import { PageMoi } from './page-moi';

/**
 * Accueil simple d'un rôle : bonjour, l'école, et des raccourcis vers les pages de son menu. Il
 * n'affiche rien qui n'existe pas encore (pas de chiffres inventés, pas d'emplacement « bientôt »).
 */
@Component({
  selector: 'ecole-page-accueil',
  imports: [EnTetePage, Icone, PageMoi, RouterLink],
  template: `
    <ecole-en-tete-page
      [titre]="'Bonjour ' + (session.utilisateur() ?? '')"
      [sousTitre]="sousTitre()"
    />
    @if (raccourcis().length > 0) {
      <section aria-labelledby="titre-raccourcis">
        <h2 id="titre-raccourcis">Aller à</h2>
        <ul class="raccourcis" data-testid="raccourcis">
          @for (r of raccourcis(); track r.id) {
            <li>
              <a
                class="carte"
                [routerLink]="r.chemin"
                [attr.data-testid]="'raccourci-' + r.id"
              >
                <ecole-icone [nom]="r.icone" [taille]="28" />
                <span>{{ r.libelle }}</span>
              </a>
            </li>
          }
        </ul>
      </section>
    }
    <ecole-page-moi [integre]="true" />
  `,
  styles: `
    .raccourcis {
      list-style: none;
      margin: 0 0 16px;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 12px;
    }
    .carte {
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 64px;
      padding: 12px 16px;
      color: var(--ecole-texte);
      text-decoration: none;
      font-weight: 600;
    }
    .carte:hover {
      background: var(--ecole-primaire-doux);
    }
  `,
})
export class PageAccueil {
  protected readonly session = inject(Session);
  private readonly acces = inject(Acces);

  protected readonly sousTitre = computed(() => {
    const ecole = this.session.ecoleActive()?.nom;
    const role = this.acces.libelleRole();
    return [ecole, role].filter(Boolean).join(' · ');
  });

  /** Les pages du menu, hors l'accueil lui-même. */
  protected readonly raccourcis = computed(() =>
    this.acces.menu().filter((e) => e.chemin !== '/'),
  );
}
