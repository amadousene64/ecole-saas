/** Styles partagés par les écrans : titres, formulaires en grille, tableaux, messages. */
export const STYLES_COMMUNS = `
  :host {
    display: block;
    max-width: 1100px;
  }
  h1 {
    margin-top: 0;
  }
  h2 {
    margin: 24px 0 8px;
    font-size: 1.15rem;
  }
  .grille {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 0 16px;
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
    border-collapse: collapse;
    margin-bottom: 16px;
  }
  th,
  td {
    text-align: left;
    padding: 6px 10px;
    border-bottom: 1px solid var(--mat-sys-outline-variant, #ccc);
    vertical-align: middle;
  }
  .message {
    padding: 8px 12px;
    border-radius: 4px;
    margin: 8px 0;
  }
  .message.erreur {
    background: var(--mat-sys-error-container, #fdd);
    color: var(--mat-sys-on-error-container, #410002);
  }
  .message.ok {
    background: var(--mat-sys-secondary-container, #dfd);
    color: var(--mat-sys-on-secondary-container, #002a00);
  }
  .aide {
    color: var(--mat-sys-on-surface-variant, #555);
    font-size: 0.9rem;
  }
  .pastille {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    background: var(--mat-sys-surface-container-high, #eee);
    font-size: 0.85rem;
  }
  .pastille.en-cours {
    background: var(--mat-sys-primary-container, #cde);
  }
`;
