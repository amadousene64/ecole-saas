// Régénère le contrat OpenAPI depuis le backend (Docker requis pour ses tests), puis le client.
// Portable : Git Bash, PowerShell et CI. Pour ne régénérer que le client : npm run api:generate.
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const racine = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const windows = process.platform === 'win32';

function lancer(commande, args, cwd) {
  const r = spawnSync(commande, args, { cwd, stdio: 'inherit', shell: windows });
  if (r.status !== 0) {
    process.exit(r.status ?? 1);
  }
}

lancer(
  windows ? 'mvnw.cmd' : './mvnw',
  ['test', '-Dtest=ContratOpenApiTest', '-Dopenapi.maj=true'],
  join(racine, 'backend'),
);
lancer('npm', ['run', 'api:generate'], join(racine, 'frontend'));
