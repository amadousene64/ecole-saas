import { mesEcoles } from './generated/fn/moi-controller/mes-ecoles';
import { moi } from './generated/fn/moi-controller/moi';

describe('contrat OpenAPI du backend', () => {
  it('expose /moi et /moi/ecoles', () => {
    expect(moi.PATH).toBe('/api/v1/moi');
    expect(mesEcoles.PATH).toBe('/api/v1/moi/ecoles');
  });
});
