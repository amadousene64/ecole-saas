import { TestBed } from '@angular/core/testing';
import { TypeImage } from '../services/service-etablissement';
import { CarteImage } from './carte-image';

function creer(type: TypeImage, modifiable = true, url?: string) {
  const fixture = TestBed.createComponent(CarteImage);
  fixture.componentRef.setInput('type', type);
  fixture.componentRef.setInput('titre', 'Logo');
  fixture.componentRef.setInput('modifiable', modifiable);
  fixture.componentRef.setInput('url', url);
  return fixture;
}

function choisir(racine: HTMLElement, type: TypeImage, fichier: File): void {
  const champ = racine.querySelector<HTMLInputElement>(
    `[data-testid="fichier-${type}"]`,
  )!;
  Object.defineProperty(champ, 'files', {
    value: { item: () => fichier, length: 1 },
    configurable: true,
  });
  champ.dispatchEvent(new Event('change'));
}

describe('CarteImage', () => {
  it('transmet un PNG valide', async () => {
    const fixture = creer('LOGO');
    const deposes: File[] = [];
    fixture.componentInstance.deposer.subscribe((f) => deposes.push(f));
    await fixture.whenStable();

    const png = new File([new Uint8Array(1000)], 'logo.png', {
      type: 'image/png',
    });
    choisir(fixture.nativeElement, 'LOGO', png);
    await fixture.whenStable();

    expect(deposes).toEqual([png]);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('refuse un format autre que PNG ou JPEG (SVG, PDF…)', async () => {
    const fixture = creer('LOGO');
    const deposes: File[] = [];
    fixture.componentInstance.deposer.subscribe((f) => deposes.push(f));
    await fixture.whenStable();

    choisir(
      fixture.nativeElement,
      'LOGO',
      new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' }),
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(deposes).toHaveLength(0);
    expect(
      fixture.nativeElement.querySelector('[role="alert"]')?.textContent,
    ).toContain('PNG et JPEG');
  });

  it('refuse un fichier trop volumineux pour son type (512 Ko pour la signature)', async () => {
    const fixture = creer('SIGNATURE');
    const deposes: File[] = [];
    fixture.componentInstance.deposer.subscribe((f) => deposes.push(f));
    await fixture.whenStable();

    choisir(
      fixture.nativeElement,
      'SIGNATURE',
      new File([new Uint8Array(600_000)], 's.png', { type: 'image/png' }),
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(deposes).toHaveLength(0);
    expect(
      fixture.nativeElement.querySelector('[role="alert"]')?.textContent,
    ).toContain('512 Ko');
  });

  it("n'offre ni dépôt ni suppression en lecture seule, mais montre l'aperçu", async () => {
    const fixture = creer('LOGO', false, 'https://stockage.test/logo.png');
    await fixture.whenStable();
    const racine = fixture.nativeElement as HTMLElement;

    expect(racine.querySelector('img')?.getAttribute('src')).toBe(
      'https://stockage.test/logo.png',
    );
    expect(racine.querySelector('input[type="file"]')).toBeNull();
    expect(racine.querySelector('button')).toBeNull();
  });
});
