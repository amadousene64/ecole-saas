import { ApplicationRef, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  DELAI_ENREGISTREMENT_MS,
  provideEnregistrementServiceWorker,
} from './service-worker';

describe('enregistrement du service worker', () => {
  let register: ReturnType<typeof vi.fn>;
  let stable: () => void;
  const origine = Object.getOwnPropertyDescriptor(navigator, 'serviceWorker');

  beforeEach(() => {
    vi.useFakeTimers();
    register = vi.fn().mockResolvedValue({});
    Object.defineProperty(navigator, 'serviceWorker', {
      configurable: true,
      value: { register },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    if (origine) Object.defineProperty(navigator, 'serviceWorker', origine);
    else delete (navigator as { serviceWorker?: unknown }).serviceWorker;
    TestBed.resetTestingModule();
  });

  function demarrer(actif: boolean) {
    const appRef = {
      whenStable: () => new Promise<void>((r) => (stable = r)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: ApplicationRef, useValue: appRef },
        provideEnregistrementServiceWorker('ngsw-worker.js', actif),
      ],
    });
    TestBed.inject(ApplicationRef);
  }

  it('enregistre ngsw-worker.js une seule fois, dès que l’app est stable', async () => {
    demarrer(true);
    expect(register).not.toHaveBeenCalled();
    stable();
    await Promise.resolve();
    vi.advanceTimersByTime(DELAI_ENREGISTREMENT_MS);
    expect(register).toHaveBeenCalledTimes(1);
    expect(register).toHaveBeenCalledWith('ngsw-worker.js', { scope: './' });
  });

  it('enregistre au plus tard après 30 s si l’app ne devient pas stable', () => {
    demarrer(true);
    vi.advanceTimersByTime(DELAI_ENREGISTREMENT_MS - 1);
    expect(register).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(register).toHaveBeenCalledTimes(1);
  });

  it('n’enregistre rien en développement', async () => {
    demarrer(false);
    vi.advanceTimersByTime(DELAI_ENREGISTREMENT_MS);
    expect(register).not.toHaveBeenCalled();
  });
});
