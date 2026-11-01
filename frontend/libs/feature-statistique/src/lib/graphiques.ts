import { TableauDeBordDto } from '@ecole/api-client';
import type { EChartsCoreOption } from 'echarts/core';

/**
 * Données et options des graphiques, en fonctions pures (testables sans ECharts ni navigateur).
 * Aucun nom d'élève ici : que des nombres par niveau, statut ou semaine.
 */

/** Une couleur par cycle, la même partout (graphiques, pastilles, légende). */
export const COULEURS_CYCLES = {
  ELEMENTAIRE: '#1B8A5A',
  MOYEN: '#2A6FC9',
  SECONDAIRE: '#C2611A',
} as const;

export const LIBELLES_CYCLES = {
  ELEMENTAIRE: 'Primaire',
  MOYEN: 'Collège',
  SECONDAIRE: 'Lycée',
} as const;

export const LIBELLES_STATUTS: Record<string, string> = {
  PRE_INSCRIT: 'Pré-inscrits',
  INSCRIT: 'Inscrits',
  TRANSFERE: 'Transférés',
  RADIE: 'Radiés',
  ABANDON: 'Abandons',
};

const COULEURS_STATUTS: Record<string, string> = {
  PRE_INSCRIT: '#7A5AC8',
  INSCRIT: '#2F4858',
  TRANSFERE: '#6B7683',
  RADIE: '#B3261E',
  ABANDON: '#9A6B12',
};

const COULEUR_COURBE = '#1F6B4F';
const TEXTE = '#16201C';
const TEXTE_SECONDAIRE = '#56635D';
const TRAIT = '#DDE3DF';

/** Ligne d'un tableau de données (alternative accessible à chaque graphique). */
export interface LigneDonnees {
  libelle: string;
  valeur: number;
  detail?: string;
}

export interface Graphique {
  titre: string;
  /** Phrase lue à la place du graphique par un lecteur d'écran. */
  resume: string;
  colonne: string;
  lignes: LigneDonnees[];
  options: EChartsCoreOption;
}

function nombre(valeur: number): string {
  return new Intl.NumberFormat('fr-FR').format(valeur);
}

function pluriel(n: number, singulier: string, plurielForme: string): string {
  return `${nombre(n)} ${n > 1 ? plurielForme : singulier}`;
}

function styleCommun(police: string) {
  return {
    textStyle: { fontFamily: police, color: TEXTE },
    animationDuration: 300,
    // Pas de mouvement pour qui le demande : géré à l'affichage (voir la page).
    // Motifs en plus des couleurs ; le texte lu est le résumé de la page (aria-label), pas celui d'ECharts.
    aria: { enabled: true, label: { enabled: false }, decal: { show: true } },
    tooltip: {
      trigger: 'axis' as const,
      confine: true,
      textStyle: { fontFamily: police, color: TEXTE },
    },
  };
}

/** Effectif (inscrits) par niveau, une couleur par cycle. */
export function graphiqueNiveaux(
  t: TableauDeBordDto,
  police = 'inherit',
): Graphique {
  const niveaux = t.parNiveau ?? [];
  const lignes: LigneDonnees[] = niveaux.map((n) => ({
    libelle: n.libelle ?? n.code ?? '',
    valeur: n.nombre ?? 0,
    detail: n.cycle ? LIBELLES_CYCLES[n.cycle] : undefined,
  }));
  const total = t.inscrits ?? 0;
  return {
    titre: 'Inscrits par niveau',
    colonne: 'Inscrits',
    lignes,
    resume:
      total === 0
        ? 'Aucun élève inscrit pour le moment.'
        : `${pluriel(total, 'élève inscrit', 'élèves inscrits')} : ` +
          niveaux
            .filter((n) => (n.nombre ?? 0) > 0)
            .map((n) => `${n.libelle} ${nombre(n.nombre ?? 0)}`)
            .join(', ') +
          '.',
    options: {
      ...styleCommun(police),
      grid: { left: 8, right: 8, top: 16, bottom: 8, containLabel: true },
      xAxis: {
        type: 'category',
        data: lignes.map((l) => l.libelle),
        axisLabel: { color: TEXTE_SECONDAIRE, interval: 0, hideOverlap: true },
        axisLine: { lineStyle: { color: TRAIT } },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: TEXTE_SECONDAIRE },
        splitLine: { lineStyle: { color: TRAIT } },
      },
      series: [
        {
          type: 'bar',
          name: 'Inscrits',
          barMaxWidth: 36,
          data: niveaux.map((n) => ({
            value: n.nombre ?? 0,
            itemStyle: {
              color: n.cycle ? COULEURS_CYCLES[n.cycle] : COULEUR_COURBE,
            },
          })),
        },
      ],
    },
  };
}

/** Inscriptions par statut (tous les statuts de l'année). */
export function graphiqueStatuts(
  t: TableauDeBordDto,
  police = 'inherit',
): Graphique {
  const statuts = t.parStatut ?? [];
  const lignes: LigneDonnees[] = statuts.map((s) => ({
    libelle: LIBELLES_STATUTS[s.statut ?? ''] ?? s.statut ?? '',
    valeur: s.nombre ?? 0,
  }));
  const total = lignes.reduce((somme, l) => somme + l.valeur, 0);
  return {
    titre: 'Inscriptions par statut',
    colonne: 'Inscriptions',
    lignes,
    resume:
      total === 0
        ? 'Aucune inscription cette année.'
        : lignes.map((l) => `${l.libelle} ${nombre(l.valeur)}`).join(', ') +
          '.',
    options: {
      ...styleCommun(police),
      tooltip: { ...styleCommun(police).tooltip, trigger: 'item' },
      grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
      xAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: TEXTE_SECONDAIRE },
        splitLine: { lineStyle: { color: TRAIT } },
      },
      yAxis: {
        type: 'category',
        inverse: true,
        data: lignes.map((l) => l.libelle),
        axisLabel: { color: TEXTE },
        axisLine: { lineStyle: { color: TRAIT } },
      },
      series: [
        {
          type: 'bar',
          name: 'Inscriptions',
          barMaxWidth: 24,
          label: { show: true, position: 'right', color: TEXTE },
          data: statuts.map((s) => ({
            value: s.nombre ?? 0,
            itemStyle: {
              color: COULEURS_STATUTS[s.statut ?? ''] ?? TEXTE_SECONDAIRE,
            },
          })),
        },
      ],
    },
  };
}

/** Inscriptions enregistrées par semaine (lundi de la semaine). */
export function graphiqueCourbe(
  t: TableauDeBordDto,
  police = 'inherit',
): Graphique {
  const semaines = t.courbe ?? [];
  const lignes: LigneDonnees[] = semaines.map((s) => ({
    libelle: `Semaine du ${formaterLundi(s.lundi)}`,
    valeur: s.nombre ?? 0,
  }));
  const total = lignes.reduce((somme, l) => somme + l.valeur, 0);
  const meilleure = lignes.reduce<LigneDonnees | null>(
    (m, l) => (m === null || l.valeur > m.valeur ? l : m),
    null,
  );
  return {
    titre: 'Inscriptions par semaine',
    colonne: 'Inscriptions',
    lignes,
    resume:
      total === 0 || !meilleure
        ? 'Aucune inscription sur la période.'
        : `${pluriel(total, 'inscription', 'inscriptions')} sur ` +
          `${pluriel(semaines.length, 'semaine', 'semaines')} ; la plus active : ` +
          `${meilleure.libelle.toLowerCase()} avec ${nombre(meilleure.valeur)}.`,
    options: {
      ...styleCommun(police),
      grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: semaines.map((s) => formaterLundi(s.lundi, true)),
        axisLabel: { color: TEXTE_SECONDAIRE, hideOverlap: true },
        axisLine: { lineStyle: { color: TRAIT } },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: TEXTE_SECONDAIRE },
        splitLine: { lineStyle: { color: TRAIT } },
      },
      series: [
        {
          type: 'line',
          name: 'Inscriptions',
          smooth: false,
          symbolSize: 6,
          showSymbol: semaines.length <= 20,
          lineStyle: { width: 2, color: COULEUR_COURBE },
          itemStyle: { color: COULEUR_COURBE },
          areaStyle: { color: COULEUR_COURBE, opacity: 0.12 },
          data: semaines.map((s) => s.nombre ?? 0),
        },
      ],
    },
  };
}

/** « 2 novembre 2026 » (ou « 2 nov. » en abrégé), sans fuseau : la date est celle de l'école. */
export function formaterLundi(iso: string | undefined, court = false): string {
  if (!iso) {
    return '';
  }
  const [a, m, j] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(a, m - 1, j));
  return new Intl.DateTimeFormat('fr-FR', {
    timeZone: 'UTC',
    day: 'numeric',
    month: court ? 'short' : 'long',
    ...(court ? {} : { year: 'numeric' }),
  }).format(date);
}
