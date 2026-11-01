import { validerAdressesApps } from './config';

const TOUTES = {
  backoffice: 'https://ecole.monapp.sn',
  'vie-scolaire': 'https://vie.monapp.sn',
  parent: 'https://parent.monapp.sn',
};

describe('validerAdressesApps', () => {
  it('en développement, complète par les ports locaux', () => {
    expect(validerAdressesApps(undefined, true).backoffice).toBe(
      'http://localhost:4200',
    );
    expect(
      validerAdressesApps({ parent: 'http://localhost:4300' }, true).parent,
    ).toBe('http://localhost:4300');
  });

  it('hors développement, exige les trois adresses (un oubli enverrait vers localhost)', () => {
    expect(() => validerAdressesApps(undefined, false)).toThrow(/manquantes/);
    expect(() =>
      validerAdressesApps({ backoffice: TOUTES.backoffice }, false),
    ).toThrow(/manquantes/);
    expect(validerAdressesApps(TOUTES, false)).toEqual(TOUTES);
  });

  it('refuse http hors localhost, et une adresse invalide', () => {
    expect(() =>
      validerAdressesApps(
        { ...TOUTES, parent: 'http://parent.monapp.sn' },
        false,
      ),
    ).toThrow(/https/);
    expect(() =>
      validerAdressesApps({ ...TOUTES, parent: 'pas une adresse' }, false),
    ).toThrow(/invalide/);
    expect(() =>
      validerAdressesApps({ ...TOUTES, parent: 'javascript:alert(1)' }, false),
    ).toThrow();
    expect(
      validerAdressesApps({ ...TOUTES, parent: 'http://127.0.0.1:4201' }, false)
        .parent,
    ).toBe('http://127.0.0.1:4201');
  });
});
