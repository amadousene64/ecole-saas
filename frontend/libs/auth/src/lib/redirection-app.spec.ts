import { TestBed } from '@angular/core/testing';
import { EcoleCourante } from './ecole-courante';
import { CONFIG_APPS, RedirectionApp, URLS_DEV } from './redirection-app';

const ECOLE = '01930000-0000-7000-8000-0000000000a1';

function service(courante: 'backoffice' | 'parent' = 'backoffice') {
  TestBed.configureTestingModule({
    providers: [
      { provide: CONFIG_APPS, useValue: { courante, urls: URLS_DEV } },
    ],
  });
  return TestBed.inject(RedirectionApp);
}

function espionner(r: RedirectionApp) {
  return vi
    .spyOn(r as unknown as { naviguer(u: string): void }, 'naviguer')
    .mockImplementation(() => undefined);
}

describe('RedirectionApp', () => {
  beforeEach(() => {
    sessionStorage.clear();
    window.history.replaceState(null, '', '/');
  });

  it("va sur l'accueil de l'autre application avec l'école et l'origine", () => {
    const r = service();
    TestBed.inject(EcoleCourante).choisir(ECOLE);
    const aller = espionner(r);

    r.vers('vie-scolaire');

    const url = new URL(aller.mock.calls[0][0]);
    expect(url.origin).toBe('http://localhost:4202');
    expect(url.pathname).toBe('/');
    expect(url.searchParams.get('ecole')).toBe(ECOLE);
    expect(url.searchParams.get('depuis')).toBe('backoffice');
  });

  it("sans école choisie, n'ajoute que l'origine", () => {
    const r = service();
    const aller = espionner(r);
    r.vers('parent');
    const url = new URL(aller.mock.calls[0][0]);
    expect(url.searchParams.has('ecole')).toBe(false);
    expect(url.searchParams.get('depuis')).toBe('backoffice');
  });

  it("à l'arrivée, retient l'école et l'origine puis les retire de l'adresse", () => {
    window.history.replaceState(
      null,
      '',
      `/eleves?ecole=${ECOLE.toUpperCase()}&depuis=parent&autre=1#haut`,
    );
    const r = service();
    r.accueillir();

    expect(TestBed.inject(EcoleCourante).id()).toBe(ECOLE);
    expect(r.origine()).toBe('parent');
    expect(window.location.search).toBe('?autre=1');
    expect(window.location.hash).toBe('#haut');
  });

  it("n'accepte ni une école qui n'est pas un UUID, ni une application inconnue", () => {
    window.history.replaceState(
      null,
      '',
      '/?ecole=%3Cscript%3Ealert(1)%3C/script%3E&depuis=parent',
    );
    const r = service();
    r.accueillir();
    // L'origine est valide : elle est retenue ; l'école, qui n'est pas un UUID, est ignorée.
    expect(TestBed.inject(EcoleCourante).id()).toBeNull();
    expect(r.origine()).toBe('parent');
    expect(window.location.search).toBe('');
  });

  it("sans origine valide, ?ecole= n'est PAS un renvoi : rien n'est retenu ni retiré", () => {
    // Lien d'activation remis par l'école, ou lien piégé : l'école courante ne change pas toute seule.
    window.history.replaceState(
      null,
      '',
      `/activer?ecole=${ECOLE}&depuis=pirate`,
    );
    const r = service();
    r.accueillir();
    expect(TestBed.inject(EcoleCourante).id()).toBeNull();
    expect(r.origine()).toBeNull();
    expect(window.location.search).toBe(`?ecole=${ECOLE}&depuis=pirate`);

    window.history.replaceState(null, '', `/activer?ecole=${ECOLE}`);
    r.accueillir();
    expect(TestBed.inject(EcoleCourante).id()).toBeNull();
    expect(window.location.search).toBe(`?ecole=${ECOLE}`);
  });

  it("ne touche à rien quand l'adresse ne porte ni école ni origine", () => {
    window.history.replaceState(null, '', '/eleves?page=2');
    const r = service();
    r.accueillir();
    expect(window.location.search).toBe('?page=2');
    expect(TestBed.inject(EcoleCourante).id()).toBeNull();
  });

  it("oublie l'origine mémorisée", () => {
    window.history.replaceState(null, '', '/?depuis=parent');
    const r = service();
    r.accueillir();
    expect(r.origine()).toBe('parent');
    r.oublierOrigine();
    expect(r.origine()).toBeNull();
  });
});
