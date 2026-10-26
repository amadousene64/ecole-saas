import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Session } from '@ecole/auth';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    const session = {
      ecoleActive: signal({ id: 'a', code: 'LYCEE-A', nom: 'Lycée Exemple A' }),
      utilisateur: signal('directeur.a'),
      plusieursEcoles: signal(false),
    };
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: Session, useValue: session }],
    }).compileComponents();
  });

  it("affiche le titre, l'école courante et la navigation", async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const racine = fixture.nativeElement as HTMLElement;
    expect(racine.querySelector('.titre')?.textContent).toContain(
      'Back-office',
    );
    expect(racine.querySelector('.ecole')?.textContent).toContain(
      'Lycée Exemple A',
    );
    expect(racine.querySelector('nav a')?.textContent).toContain('Accueil');
  });
});
