import { DatePipe, registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FcfaPipe } from './fcfa';

registerLocaleData(localeFr);

// Espaces insécables (U+00A0 et U+202F, séparateur de milliers français) ramenées à une espace.
const normaliser = (texte: string) =>
  texte.replace(
    new RegExp(
      `[${String.fromCharCode(0x202f)}${String.fromCharCode(0x00a0)}]`,
      'g',
    ),
    ' ',
  );

describe('formats français', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [{ provide: LOCALE_ID, useValue: 'fr' }],
    }),
  );

  it('affiche les montants en FCFA sans décimale', () => {
    const pipe = TestBed.runInInjectionContext(() => new FcfaPipe());
    expect(normaliser(pipe.transform(1500000))).toBe('1 500 000 FCFA');
    expect(normaliser(pipe.transform(0))).toBe('0 FCFA');
    expect(normaliser(pipe.transform(25000))).toBe('25 000 FCFA');
    expect(pipe.transform(null)).toBe('');
  });

  it('affiche les dates en français', () => {
    const pipe = TestBed.runInInjectionContext(() => new DatePipe('fr'));
    expect(pipe.transform('2026-10-26T12:00:00Z', 'longDate', 'UTC')).toBe(
      '26 octobre 2026',
    );
    expect(pipe.transform('2027-03-01T12:00:00Z', 'EEEE d MMMM y', 'UTC')).toBe(
      'lundi 1 mars 2027',
    );
  });
});
