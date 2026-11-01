import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { Acces } from './acces';
import { RechercheGlobale } from './recherche-globale';
import { SOURCES_RECHERCHE, SourceRecherche } from './recherche-sources';
import { CONFIG_APPS, URLS_DEV } from './redirection-app';
import { Session } from './session';

function source(
  rechercher: SourceRecherche['rechercher'],
  disponible = true,
): SourceRecherche {
  return {
    id: 'eleves',
    titre: 'Élèves',
    disponible: () => disponible,
    rechercher,
  };
}

function creer(roles: string[], sources: SourceRecherche[]) {
  const fermer = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: Session, useValue: { roles: signal(roles) } },
      {
        provide: CONFIG_APPS,
        useValue: { courante: 'backoffice', urls: URLS_DEV },
      },
      { provide: MatDialogRef, useValue: { close: fermer } },
      ...sources.map((s) => ({
        provide: SOURCES_RECHERCHE,
        multi: true,
        useValue: s,
      })),
    ],
  });
  const fixture = TestBed.createComponent(RechercheGlobale);
  fixture.detectChanges();
  const racine = fixture.nativeElement as HTMLElement;
  const saisir = (texte: string) => {
    const champ = racine.querySelector<HTMLInputElement>(
      '[data-testid="terme-recherche-globale"]',
    )!;
    champ.value = texte;
    champ.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  return { fixture, racine, saisir, fermer };
}

describe('RechercheGlobale', () => {
  afterEach(() => vi.useRealTimers());

  it('propose les pages du menu du rôle, sans accent ni casse, jamais une page interdite', () => {
    const { racine, saisir } = creer(['SECRETARIAT'], []);
    saisir('IMPORT');
    expect(
      racine.querySelector('[data-testid="resultat-page-import"]'),
    ).not.toBeNull();

    saisir('param');
    expect(racine.textContent).not.toContain('Établissement');
    expect(racine.textContent).toContain('Aucun résultat');

    // Le secrétariat n'a pas « Années et périodes » : même en la nommant.
    saisir('annees');
    expect(racine.textContent).not.toContain('Années et périodes');
  });

  it('interroge les sources après un délai, avec le terme, et ignore une réponse périmée', async () => {
    vi.useFakeTimers();
    const rechercher = vi.fn().mockResolvedValue([
      {
        id: 'eleve-1',
        libelle: 'Awa Fictif',
        detail: 'M-1',
        chemin: '/eleves/1',
      },
    ]);
    const { fixture, racine, saisir } = creer(
      ['SECRETARIAT'],
      [source(rechercher)],
    );

    saisir('a');
    saisir('aw');
    saisir('awa');
    expect(rechercher).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(300);
    fixture.detectChanges();
    expect(rechercher).toHaveBeenCalledTimes(1);
    expect(rechercher).toHaveBeenCalledWith('awa');
    expect(racine.textContent).toContain('Awa Fictif');
  });

  it("n'interroge pas une source indisponible pour ce rôle", async () => {
    vi.useFakeTimers();
    const rechercher = vi.fn().mockResolvedValue([]);
    const { saisir } = creer(['COMPTABLE'], [source(rechercher, false)]);
    saisir('awa');
    await vi.advanceTimersByTimeAsync(400);
    expect(rechercher).not.toHaveBeenCalled();
  });

  it("une source en erreur n'empêche pas d'afficher les pages", async () => {
    vi.useFakeTimers();
    const rechercher = vi.fn().mockRejectedValue(new Error('panne'));
    const { fixture, racine, saisir } = creer(
      ['SECRETARIAT'],
      [source(rechercher)],
    );
    saisir('eleve');
    await vi.advanceTimersByTimeAsync(400);
    fixture.detectChanges();
    expect(
      racine.querySelector('[data-testid="resultat-page-eleves"]'),
    ).not.toBeNull();
  });

  it('va au résultat choisi puis se ferme', async () => {
    const { racine, saisir, fermer } = creer(['SECRETARIAT'], []);
    const aller = vi
      .spyOn(TestBed.inject(Router), 'navigateByUrl')
      .mockResolvedValue(true);
    saisir('import');
    racine
      .querySelector<HTMLButtonElement>('[data-testid="resultat-page-import"]')!
      .click();
    expect(aller).toHaveBeenCalledWith('/eleves/import');
    expect(fermer).toHaveBeenCalled();
  });

  it('un terme trop court ne lance aucune recherche distante', async () => {
    vi.useFakeTimers();
    const rechercher = vi.fn().mockResolvedValue([]);
    const { saisir } = creer(['SECRETARIAT'], [source(rechercher)]);
    saisir('a');
    await vi.advanceTimersByTimeAsync(400);
    expect(rechercher).not.toHaveBeenCalled();
  });
});

describe('Acces', () => {
  it('lit les mêmes droits que la table : menu, pages et actions', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: Session, useValue: { roles: signal(['CENSEUR']) } },
        {
          provide: CONFIG_APPS,
          useValue: { courante: 'backoffice', urls: URLS_DEV },
        },
      ],
    });
    const acces = TestBed.inject(Acces);
    expect(acces.menu().map((e) => e.libelle)).toEqual([
      'Accueil',
      'Élèves',
      'Classes',
    ]);
    expect(acces.libelleRole()).toBe('Censeur');
    expect(acces.peut('eleves.sensible.lire')).toBe(true);
    expect(acces.peut('eleves.ecrire')).toBe(false);
    expect(acces.pagePermise('import')).toBe(false);
    expect(acces.signalPour('eleves.certificat.ouvrir')()).toBe(true);
  });

  it("n'a pas de menu sans configuration d'application (bibliothèques testées seules)", () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: Session, useValue: { roles: signal(['DIRECTEUR']) } },
      ],
    });
    expect(TestBed.inject(Acces).menu()).toEqual([]);
  });
});
