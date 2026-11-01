import { BreakpointObserver } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Coquille } from './coquille';
import { CoquilleLegere } from './coquille-legere';

const PETIT = '(max-width: 599.98px)';
const MOYEN = '(max-width: 1023.98px)';

function largeur(px: number) {
  return {
    matches: px < 1024,
    breakpoints: { [PETIT]: px < 600, [MOYEN]: px < 1024 },
  };
}

const LIENS = [
  { chemin: '/', libelle: 'Accueil', icone: 'accueil' as const },
  { chemin: '/eleves', libelle: 'Élèves', icone: 'eleves' as const },
  {
    chemin: '/etablissement/classes',
    libelle: 'Classes et salles',
    icone: 'classes' as const,
    groupe: 'Classes et années',
  },
];

async function coquille(px: number) {
  const etat = new BehaviorSubject(largeur(px));
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: BreakpointObserver, useValue: { observe: () => etat } },
    ],
  });
  const fixture = TestBed.createComponent(Coquille);
  fixture.componentRef.setInput('liens', LIENS);
  fixture.componentRef.setInput('utilisateur', 'directeur.a');
  fixture.componentRef.setInput('role', 'Directeur');
  fixture.componentRef.setInput('recherche', true);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, racine: fixture.nativeElement as HTMLElement };
}

describe('Coquille : trois modes de navigation', () => {
  it('sous 600 px : tiroir et bouton menu', async () => {
    const { racine } = await coquille(390);
    expect(
      racine.querySelector('[data-testid="ouvrir-tiroir"]'),
    ).not.toBeNull();
    expect(racine.querySelector('mat-sidenav')?.className).not.toContain(
      'reduite',
    );
  });

  it('de 600 à 1024 px : barre latérale réduite aux icônes, libellés pour lecteurs d’écran', async () => {
    const { racine } = await coquille(768);
    expect(racine.querySelector('[data-testid="ouvrir-tiroir"]')).toBeNull();
    expect(racine.querySelector('mat-sidenav')?.className).toContain('reduite');
    const libelles = Array.from(racine.querySelectorAll('.libelle-lien'));
    expect(libelles.length).toBe(3);
    for (const l of libelles) {
      expect(l.classList).toContain('sr-only');
    }
    // Chaque lien garde un nom accessible et une info-bulle.
    expect(racine.querySelector('a.lien')?.getAttribute('title')).toBe(
      'Accueil',
    );
  });

  it('au-dessus de 1024 px : barre complète avec libellés et sections', async () => {
    const { racine } = await coquille(1440);
    expect(racine.querySelector('[data-testid="ouvrir-tiroir"]')).toBeNull();
    expect(racine.querySelector('mat-sidenav')?.className).not.toContain(
      'reduite',
    );
    for (const l of Array.from(racine.querySelectorAll('.libelle-lien'))) {
      expect(l.classList).not.toContain('sr-only');
    }
    expect(racine.querySelector('.titre-section')?.textContent).toContain(
      'Classes et années',
    );
  });

  it('le menu du compte affiche le nom et le rôle', async () => {
    const { racine } = await coquille(1440);
    const compte = racine.querySelector('[data-testid="menu-compte"]')!;
    expect(compte.textContent).toContain('directeur.a');
    expect(compte.textContent).toContain('Directeur');
  });

  it('émet la demande de recherche', async () => {
    const { fixture, racine } = await coquille(1440);
    let demandes = 0;
    fixture.componentInstance.rechercher.subscribe(() => demandes++);
    racine
      .querySelector<HTMLButtonElement>('[data-testid="ouvrir-recherche"]')!
      .click();
    expect(demandes).toBe(1);
  });

  it('le tiroir s’ouvre au clic sur le bouton menu', async () => {
    const { fixture, racine } = await coquille(390);
    racine
      .querySelector<HTMLButtonElement>('[data-testid="ouvrir-tiroir"]')!
      .click();
    fixture.detectChanges();
    expect(
      racine
        .querySelector('[data-testid="ouvrir-tiroir"]')
        ?.getAttribute('aria-expanded'),
    ).toBe('true');
  });
});

describe('CoquilleLegere : barre du bas', () => {
  function legere(nb: number) {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(CoquilleLegere);
    fixture.componentRef.setInput('liens', LIENS.slice(0, nb));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('masquée avec une seule entrée', () => {
    const racine = legere(1);
    expect(racine.querySelector('nav.barre-basse')).toBeNull();
    expect(racine.querySelector('main')?.classList).not.toContain('avec-barre');
  });

  it('masquée sans entrée', () => {
    expect(legere(0).querySelector('nav.barre-basse')).toBeNull();
  });

  it('affichée dès deux entrées', () => {
    const racine = legere(2);
    expect(racine.querySelectorAll('nav.barre-basse a')).toHaveLength(2);
    expect(racine.querySelector('main')?.classList).toContain('avec-barre');
  });
});

describe('Coquille : menu du compte', () => {
  const bouton = (racine: HTMLElement) =>
    racine.querySelector<HTMLButtonElement>('[data-testid="menu-compte"]')!;
  const elements = (racine: HTMLElement) =>
    Array.from(racine.querySelectorAll<HTMLButtonElement>('[role="menuitem"]'));

  async function ouvert(plusieursEcoles = true) {
    const { fixture, racine } = await coquille(1440);
    fixture.componentRef.setInput('plusieursEcoles', plusieursEcoles);
    fixture.detectChanges();
    document.body.appendChild(racine);
    bouton(racine).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, racine };
  }

  afterEach(() => document.body.replaceChildren());

  it('est fermé au départ, puis annoncé ouvert avec ses éléments', async () => {
    const { fixture, racine } = await coquille(1440);
    expect(bouton(racine).getAttribute('aria-expanded')).toBe('false');
    expect(bouton(racine).getAttribute('aria-haspopup')).toBe('menu');
    expect(racine.querySelector('[role="menu"]')).toBeNull();

    bouton(racine).click();
    fixture.detectChanges();
    expect(bouton(racine).getAttribute('aria-expanded')).toBe('true');
    expect(racine.querySelector('[role="menu"]')).not.toBeNull();
    expect(elements(racine).map((e) => e.textContent?.trim())).toEqual([
      'Se déconnecter',
    ]);
  });

  it('place le focus sur le premier élément et sait changer d’école', async () => {
    const { racine } = await ouvert();
    expect(elements(racine).map((e) => e.textContent?.trim())).toEqual([
      "Changer d'école",
      'Se déconnecter',
    ]);
    expect(document.activeElement).toBe(elements(racine)[0]);
  });

  it('Échap ferme le menu et rend le focus au bouton', async () => {
    const { fixture, racine } = await ouvert();
    elements(racine)[0].dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    );
    fixture.detectChanges();
    expect(racine.querySelector('[role="menu"]')).toBeNull();
    expect(bouton(racine).getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(bouton(racine));
  });

  it('les flèches, Début et Fin parcourent les éléments en boucle', async () => {
    const { racine } = await ouvert();
    const [ecole, sortie] = elements(racine);
    const touche = (key: string) =>
      (document.activeElement as HTMLElement).dispatchEvent(
        new KeyboardEvent('keydown', { key, bubbles: true }),
      );
    touche('ArrowDown');
    expect(document.activeElement).toBe(sortie);
    touche('ArrowDown');
    expect(document.activeElement).toBe(ecole);
    touche('ArrowUp');
    expect(document.activeElement).toBe(sortie);
    touche('Home');
    expect(document.activeElement).toBe(ecole);
    touche('End');
    expect(document.activeElement).toBe(sortie);
  });

  it('un choix émet l’événement et ferme le menu', async () => {
    const { fixture, racine } = await ouvert();
    const demandes: string[] = [];
    fixture.componentInstance.changerEcole.subscribe(() =>
      demandes.push('ecole'),
    );
    fixture.componentInstance.deconnexion.subscribe(() =>
      demandes.push('deconnexion'),
    );

    elements(racine)[1].click();
    fixture.detectChanges();
    expect(demandes).toEqual(['deconnexion']);
    expect(racine.querySelector('[role="menu"]')).toBeNull();
  });

  it('« Changer d’école » n’apparaît que pour plusieurs écoles', async () => {
    const { racine } = await ouvert(false);
    expect(elements(racine).map((e) => e.textContent?.trim())).toEqual([
      'Se déconnecter',
    ]);
  });

  it('se ferme quand le focus sort du menu ou qu’on clique ailleurs', async () => {
    const { fixture, racine } = await ouvert();
    const dehors = document.createElement('button');
    document.body.appendChild(dehors);
    dehors.focus();
    fixture.detectChanges();
    expect(racine.querySelector('[role="menu"]')).toBeNull();

    // Un clic ailleurs referme aussi.
    bouton(racine).click();
    fixture.detectChanges();
    expect(racine.querySelector('[role="menu"]')).not.toBeNull();
    document.body.click();
    fixture.detectChanges();
    expect(racine.querySelector('[role="menu"]')).toBeNull();
  });
});
