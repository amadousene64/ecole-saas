import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONFIG_APPS, Session, URLS_DEV } from '@ecole/auth';
import { App } from './app';

describe('App (vie scolaire)', () => {
  beforeEach(async () => {
    const session = {
      ecoleActive: signal({ id: 'a', code: 'LYCEE-A', nom: 'Lycée Exemple A' }),
      utilisateur: signal('surveillant.a'),
      plusieursEcoles: signal(false),
      roles: signal(['SURVEILLANT']),
    };
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: Session, useValue: session },
        {
          provide: CONFIG_APPS,
          useValue: { courante: 'vie-scolaire', urls: URLS_DEV },
        },
      ],
    }).compileComponents();
  });

  it("affiche le titre, l'école, le rôle et l'accueil", async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const racine = fixture.nativeElement as HTMLElement;
    expect(racine.querySelector('.titre')?.textContent).toContain(
      'Vie scolaire',
    );
    expect(racine.querySelector('.ecole')?.textContent).toContain(
      'Lycée Exemple A',
    );
    expect(racine.querySelector('.compte .role')?.textContent).toContain(
      'Surveillant',
    );
    expect(racine.querySelector('nav a')?.textContent).toContain('Accueil');
  });
});
