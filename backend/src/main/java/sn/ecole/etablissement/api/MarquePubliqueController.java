package sn.ecole.etablissement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.etablissement.application.MarquePubliqueService;
import sn.ecole.etablissement.application.MarquePubliqueService.Logo;
import sn.ecole.etablissement.application.MarquePubliqueService.Marque;
import sn.ecole.shared.api.ResolveurEcolePublic;

/**
 * Marque de l'école pour les pages de connexion Keycloak. SANS authentification, donc limité à ce
 * qui est public : nom, ville, phrase d'accueil et logo. Le cachet et la signature n'ont aucun
 * chemin ici (le type d'image n'est pas un paramètre). Réponses mises en cache par les navigateurs
 * et les intermédiaires ; appels limités par adresse IP côté service.
 */
@RestController
@RequestMapping("/api/v1/public/marque")
@SecurityRequirements
class MarquePubliqueController {

  private static final CacheControl CACHE =
      CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

  private final MarquePubliqueService service;
  private final ResolveurEcolePublic resolveur;

  MarquePubliqueController(MarquePubliqueService service, ResolveurEcolePublic resolveur) {
    this.service = service;
    this.resolveur = resolveur;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Marque publique de l'école de l'hôte demandé (sans authentification)",
      description =
          "Nom, ville, phrase d'accueil et version du logo. `hote` : hôte de l'application vers"
              + " laquelle Keycloak redirige (sous-domaine de l'école). Un hôte inconnu, commun ou"
              + " une école inactive donne la même marque neutre (champs nuls). `ecole` n'est pris"
              + " en compte qu'en développement.")
  ResponseEntity<MarqueDto> marque(
      @RequestParam(required = false) String hote,
      @RequestParam(required = false) UUID ecole,
      HttpServletRequest requete) {
    Marque marque = service.marque(() -> resolveur.resoudre(hote, ecole), requete.getRemoteAddr());
    return ResponseEntity.ok()
        .cacheControl(CACHE)
        .body(
            new MarqueDto(
                marque.nom(), marque.ville(), marque.phraseAccueil(), marque.logoVersion()));
  }

  @GetMapping(path = "/logo")
  @Operation(
      summary = "Logo de l'école (le seul fichier public : jamais le cachet ni la signature)")
  ResponseEntity<byte[]> logo(
      @RequestParam(required = false) String hote,
      @RequestParam(required = false) UUID ecole,
      // Version du logo : change l'adresse (donc la copie du navigateur) quand le logo est remplacé
      // ;
      // le serveur ne la lit pas.
      @RequestParam(required = false) String v,
      HttpServletRequest requete) {
    Optional<Logo> logo =
        service.logo(() -> resolveur.resoudre(hote, ecole), requete.getRemoteAddr());
    if (logo.isEmpty()) {
      return ResponseEntity.notFound()
          .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
          .build();
    }
    return ResponseEntity.ok()
        .cacheControl(CACHE)
        .eTag("\"" + logo.get().version() + "\"")
        .contentType(MediaType.parseMediaType(logo.get().contentType()))
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .header("X-Content-Type-Options", "nosniff")
        .header("Cross-Origin-Resource-Policy", "cross-origin")
        .body(logo.get().octets());
  }
}
