import { HttpErrorResponse } from '@angular/common/http';
import { baseCompatible } from './etiquettes';
import { codeErreur, ifMatch, messageErreur } from './erreurs';
import { requis } from './requis';

describe('messageErreur', () => {
  it('reprend le detail du ProblemDetail', () => {
    const erreur = new HttpErrorResponse({
      status: 409,
      error: {
        code: 'ANNEE_DEJA_EN_COURS',
        detail: 'Une autre année est déjà en cours.',
      },
    });
    expect(messageErreur(erreur)).toBe('Une autre année est déjà en cours.');
    expect(codeErreur(erreur)).toBe('ANNEE_DEJA_EN_COURS');
  });

  it('donne un message lisible sans corps exploitable', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 403 }))).toContain(
      'pas le droit',
    );
    expect(messageErreur(new HttpErrorResponse({ status: 0 }))).toContain(
      'injoignable',
    );
    expect(messageErreur(new Error('boum'))).toContain(
      'Une erreur est survenue',
    );
    expect(codeErreur(new Error('boum'))).toBeNull();
  });
});

describe('ifMatch', () => {
  it('met la version lue entre guillemets (ETag fort)', () => {
    expect(ifMatch(3)).toBe('"3"');
    expect(ifMatch(undefined)).toBe('"0"');
  });
});

describe('baseCompatible', () => {
  it('refuse la base 10 avec des coefficients seulement', () => {
    expect(baseCompatible('COEFFICIENT', 10)).toBe(false);
    expect(baseCompatible('COEFFICIENT', 20)).toBe(true);
    expect(baseCompatible('BAREME', 10)).toBe(true);
    expect(baseCompatible('BAREME', 20)).toBe(true);
    expect(baseCompatible('COEFFICIENT', null)).toBe(true);
  });
});

describe('requis', () => {
  it('rend la valeur ou signale une réponse incomplète', () => {
    expect(requis('a')).toBe('a');
    expect(() => requis(undefined)).toThrow('incomplète');
    expect(() => requis(null)).toThrow('incomplète');
  });
});
