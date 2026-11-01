/**
 * ECharts, chargé à la demande et réduit à ce que le tableau de bord utilise (barres, courbe, grille,
 * info-bulle, légende, aria). Tout ce fichier n'est atteint que par un `import()` : ECharts reste
 * hors du lot initial du back-office (budget de taille de l'application).
 */
export async function chargerEcharts(): Promise<typeof import('echarts/core')> {
  const [coeur, graphiques, composants, rendus] = await Promise.all([
    import('echarts/core'),
    import('echarts/charts'),
    import('echarts/components'),
    import('echarts/renderers'),
  ]);
  coeur.use([
    graphiques.BarChart,
    graphiques.LineChart,
    composants.GridComponent,
    composants.TooltipComponent,
    composants.LegendComponent,
    composants.AriaComponent,
    rendus.CanvasRenderer,
  ]);
  return coeur;
}
