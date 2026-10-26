import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Api, EcoleDto, mesEcoles, moi } from '@ecole/api-client';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { EcoleCourante } from './ecole-courante';
import { Session } from './session';

const A: EcoleDto = { id: 'a', code: 'LYCEE-A', nom: 'Lycée Exemple A' };
const B: EcoleDto = { id: 'b', code: 'ECOLE-B', nom: 'École Exemple B' };

describe('Session', () => {
  let ecolesServeur: EcoleDto[];
  let pannes: number;
  const invoke = vi.fn();
  const oidc = {
    userData: signal({ userData: { preferred_username: 'enseignant.parent' } }),
    logoff: vi.fn(() => of(null)),
  };

  function session(): Session {
    return TestBed.inject(Session);
  }

  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
    ecolesServeur = [A, B];
    pannes = 0;
    // Le « serveur » : les rôles dépendent de l'école demandée (celle mémorisée).
    invoke.mockImplementation(async (fn: unknown) => {
      if (pannes > 0) {
        pannes--;
        throw new Error('panne');
      }
      if (fn === mesEcoles) {
        return ecolesServeur;
      }
      if (fn === moi) {
        const id = TestBed.inject(EcoleCourante).id();
        return {
          utilisateurId: 'u',
          ecoleId: id,
          roles: id === 'a' ? ['ENSEIGNANT'] : ['PARENT'],
        };
      }
      throw new Error('appel inattendu');
    });
    TestBed.configureTestingModule({
      providers: [
        { provide: Api, useValue: { invoke } },
        { provide: OidcSecurityService, useValue: oidc },
      ],
    });
  });

  it("choisit seule l'unique école et charge le profil", async () => {
    ecolesServeur = [A];
    await session().charger();
    expect(session().etat()).toBe('pret');
    expect(session().ecoleActive()?.nom).toBe('Lycée Exemple A');
    expect(session().roles()).toEqual(['ENSEIGNANT']);
    expect(TestBed.inject(EcoleCourante).id()).toBe('a');
  });

  it("demande le choix quand il y a plusieurs écoles et aucune n'est mémorisée", async () => {
    await session().charger();
    expect(session().etat()).toBe('choix-requis');
    expect(session().plusieursEcoles()).toBe(true);
    expect(invoke).not.toHaveBeenCalledWith(moi);
  });

  it("reprend l'école mémorisée si elle est toujours accessible", async () => {
    TestBed.inject(EcoleCourante).choisir('b');
    await session().charger();
    expect(session().etat()).toBe('pret');
    expect(session().roles()).toEqual(['PARENT']);
  });

  it("ignore une école mémorisée qui n'est plus accessible", async () => {
    TestBed.inject(EcoleCourante).choisir('disparue');
    await session().charger();
    expect(session().etat()).toBe('choix-requis');
    expect(TestBed.inject(EcoleCourante).id()).toBeNull();
  });

  it('signale un compte sans école', async () => {
    ecolesServeur = [];
    await session().charger();
    expect(session().etat()).toBe('aucune-ecole');
  });

  it('choisir une école charge ses rôles ; une école inconnue est ignorée', async () => {
    await session().charger();
    await session().choisirEcole('inconnue');
    expect(session().etat()).toBe('choix-requis');
    await session().choisirEcole('b');
    expect(session().etat()).toBe('pret');
    expect(session().roles()).toEqual(['PARENT']);
    expect(session().aUnRole(['PARENT'])).toBe(true);
    expect(session().aUnRole(['DIRECTEUR'])).toBe(false);
    expect(session().aUnRole([])).toBe(true);
  });

  it("changer d'école oublie l'école courante et vide le profil", async () => {
    TestBed.inject(EcoleCourante).choisir('a');
    await session().charger();
    session().changerEcole();
    expect(session().etat()).toBe('choix-requis');
    expect(session().profil()).toBeNull();
    expect(TestBed.inject(EcoleCourante).id()).toBeNull();
  });

  it('passe en erreur sur une panne, puis se rétablit au rechargement', async () => {
    pannes = 1;
    await session().charger();
    expect(session().etat()).toBe('erreur');
    await session().recharger();
    expect(session().etat()).toBe('choix-requis');
  });

  it('partage un seul chargement entre appels simultanés', async () => {
    await Promise.all([session().charger(), session().charger()]);
    expect(invoke.mock.calls.filter(([fn]) => fn === mesEcoles)).toHaveLength(
      1,
    );
  });

  it("expose le nom d'utilisateur du jeton et se déconnecte proprement", async () => {
    TestBed.inject(EcoleCourante).choisir('a');
    await session().charger();
    expect(session().utilisateur()).toBe('enseignant.parent');
    session().deconnecter();
    expect(oidc.logoff).toHaveBeenCalled();
    expect(TestBed.inject(EcoleCourante).id()).toBeNull();
    expect(session().profil()).toBeNull();
  });
});
