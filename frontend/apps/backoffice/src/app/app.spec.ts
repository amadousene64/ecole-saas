import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONFIG_APPS, Session, URLS_DEV } from '@ecole/auth';
import { App } from './app';

describe('App (back-office)', () => {
  async function creer(roles: string[]) {
    const session = {
      ecoleActive: signal({ id: 'a', code: 'LYCEE-A', nom: 'Lycée Exemple A' }),
      utilisateur: signal('utilisateur.test'),
      plusieursEcoles: signal(false),
      roles: signal(roles),
    };
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: Session, useValue: session },
        {
          provide: CONFIG_APPS,
          useValue: { courante: 'backoffice', urls: URLS_DEV },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const liens = (racine: HTMLElement) =>
    Array.from(racine.querySelectorAll('nav a')).map((a) =>
      a.textContent?.trim(),
    );

  it("affiche le titre, l'école, le rôle et le menu du directeur", async () => {
    const racine = await creer(['DIRECTEUR']);
    expect(racine.querySelector('.titre')?.textContent).toContain(
      'Back-office',
    );
    expect(racine.querySelector('.ecole')?.textContent).toContain(
      'Lycée Exemple A',
    );
    expect(racine.querySelector('.compte .role')?.textContent).toContain(
      'Directeur',
    );
    expect(liens(racine)).toEqual([
      'Accueil',
      'Élèves',
      'Classes et salles',
      'Années et périodes',
      'Établissement',
      'Structure pédagogique',
    ]);
    // Sections du menu
    expect(
      Array.from(racine.querySelectorAll('.titre-section')).map((e) =>
        e.textContent?.trim(),
      ),
    ).toEqual(['Classes et années', 'Paramètres']);
  });

  it('chaque rôle voit son propre menu', async () => {
    expect(liens(await creer(['SECRETARIAT']))).toEqual([
      'Accueil',
      'Élèves et inscriptions',
      'Import',
      'Classes et salles',
      'Années, périodes et calendrier',
      'Profil et logo',
      'Niveaux et séries',
    ]);
  });

  it('propose la recherche globale', async () => {
    const racine = await creer(['CENSEUR']);
    expect(
      racine.querySelector('[data-testid="ouvrir-recherche"]'),
    ).not.toBeNull();
  });
});
