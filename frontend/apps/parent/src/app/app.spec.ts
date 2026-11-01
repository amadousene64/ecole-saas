import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONFIG_APPS, Session, URLS_DEV } from '@ecole/auth';
import { App } from './app';

describe('App (espace parent)', () => {
  beforeEach(async () => {
    const session = {
      ecoleActive: signal({ id: 'a', code: 'LYCEE-A', nom: 'Lycée Exemple A' }),
      utilisateur: signal('parent.test'),
      plusieursEcoles: signal(false),
      roles: signal(['PARENT']),
    };
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: Session, useValue: session },
        {
          provide: CONFIG_APPS,
          useValue: { courante: 'parent', urls: URLS_DEV },
        },
      ],
    }).compileComponents();
  });

  it("affiche le titre, l'école et le rôle", async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const racine = fixture.nativeElement as HTMLElement;
    expect(racine.querySelector('.titre')?.textContent).toContain(
      'Espace parent',
    );
    expect(racine.querySelector('.ecole')?.textContent).toContain(
      'Lycée Exemple A',
    );
    expect(racine.querySelector('.menu .role')?.textContent).toContain(
      'Parent',
    );
  });

  it("masque la barre du bas tant qu'il y a moins de deux entrées", async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const racine = fixture.nativeElement as HTMLElement;
    expect(racine.querySelector('nav.barre-basse')).toBeNull();
    expect(racine.querySelector('main')?.classList).not.toContain('avec-barre');
  });
});
