/**
 * Styles partagés par les écrans : mise en page, formulaires en grille, tableaux. Titres, messages
 * (.message.erreur/.ok…), pastilles et textes d'aide viennent du thème global (libs/ui/theme).
 */
export const STYLES_COMMUNS = `
  :host {
    display: block;
    max-width: 1100px;
  }
  h1 {
    margin-top: 0;
  }
  .grille {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 0 16px;
  }
  .grille .large {
    grid-column: 1 / -1;
  }
  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin: 8px 0 16px;
    align-items: center;
  }
  table {
    width: 100%;
    border-collapse: separate;
    border-spacing: 0;
    margin-bottom: 16px;
    background: var(--ecole-surface);
    border: 1px solid var(--ecole-bordure);
    border-radius: var(--ecole-rayon-carte);
    overflow: hidden;
  }
  th {
    color: var(--ecole-texte-secondaire);
    font-size: var(--ecole-libelle-taille);
    line-height: var(--ecole-libelle-interligne);
    font-weight: var(--ecole-libelle-graisse);
    background: var(--ecole-fond);
  }
  th,
  td {
    text-align: left;
    padding: 8px 12px;
    border-bottom: 1px solid var(--ecole-bordure);
    vertical-align: middle;
  }
  tbody tr:last-child td {
    border-bottom: 0;
  }
  tr[aria-selected='true'] td {
    background: var(--ecole-primaire-doux);
  }
  .aide {
    color: var(--ecole-texte-secondaire);
    font-size: var(--ecole-libelle-taille);
    line-height: var(--ecole-libelle-interligne);
  }
`;
