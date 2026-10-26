package sn.ecole.shared.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.shared.application.ProfilService;

@RestController
@RequestMapping("/api/v1/moi")
class MoiController {

  private final ProfilService profils;
  private final MoiMapper mapper;

  MoiController(ProfilService profils, MoiMapper mapper) {
    this.profils = profils;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Utilisateur connecté et ses rôles dans l'école demandée")
  MoiDto moi() {
    return mapper.versDto(profils.profilCourant());
  }
}
