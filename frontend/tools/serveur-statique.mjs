#!/usr/bin/env node
// Serveur des applications construites, pour les e2e en CI (cible « serve-static ») :
//   - fichiers de dist/apps/<app>/browser, avec retour à index.html pour les routes de l'app ;
//   - /api/** relayé vers le backend, comme le proxy.conf.json du serveur de développement.
// Sans dépendance (node:http). Usage :
//   node tools/serveur-statique.mjs --dossier dist/apps/parent/browser --port 4201 \
//     [--api http://localhost:8080]
import { createReadStream, statSync } from 'node:fs';
import { createServer, request } from 'node:http';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    dossier: { type: 'string' },
    port: { type: 'string' },
    api: {
      type: 'string',
      default: process.env.E2E_API ?? 'http://localhost:8080',
    },
  },
});
if (!values.dossier || !values.port) {
  console.error(
    'Usage : serveur-statique.mjs --dossier <dist> --port <port> [--api <url>]',
  );
  process.exit(2);
}

const racine = resolve(values.dossier);
const api = new URL(values.api);
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
};

/** Fichier demandé, ou null s'il n'existe pas ou sort du dossier servi. */
function fichier(chemin) {
  const relatif = normalize(decodeURIComponent(chemin)).replace(/^[/\\]+/, '');
  const complet = join(racine, relatif);
  if (complet !== racine && !complet.startsWith(racine + sep)) {
    return null;
  }
  try {
    return statSync(complet).isFile() ? complet : null;
  } catch {
    return null;
  }
}

function servir(reponse, complet) {
  reponse.writeHead(200, {
    'Content-Type': TYPES[extname(complet)] ?? 'application/octet-stream',
    'Cache-Control': 'no-store',
  });
  createReadStream(complet).pipe(reponse);
}

function relayer(requete, reponse) {
  const sortante = request(
    {
      protocol: api.protocol,
      hostname: api.hostname,
      port: api.port,
      method: requete.method,
      path: requete.url,
      headers: { ...requete.headers, host: api.host },
    },
    (retour) => {
      reponse.writeHead(retour.statusCode ?? 502, retour.headers);
      retour.pipe(reponse);
    },
  );
  sortante.on('error', () => {
    reponse.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    reponse.end('API injoignable');
  });
  requete.pipe(sortante);
}

createServer((requete, reponse) => {
  const { pathname } = new URL(requete.url ?? '/', 'http://local');
  if (pathname === '/api' || pathname.startsWith('/api/')) {
    relayer(requete, reponse);
    return;
  }
  if (requete.method !== 'GET' && requete.method !== 'HEAD') {
    reponse.writeHead(405).end();
    return;
  }
  const trouve = fichier(pathname);
  if (trouve) {
    servir(reponse, trouve);
  } else if (!extname(pathname)) {
    // Route de l'application (/etablissement/classes…) : c'est le routeur qui l'affiche.
    servir(reponse, join(racine, 'index.html'));
  } else {
    reponse.writeHead(404).end();
  }
}).listen(Number(values.port), () => {
  console.log(
    `${racine} servi sur http://localhost:${values.port} (/api → ${api.origin})`,
  );
});
