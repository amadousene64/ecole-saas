package sn.ecole.shared.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
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
  private final EcoleMapper ecoleMapper;

  MoiController(ProfilService profils, MoiMapper mapper, EcoleMapper ecoleMapper) {
    this.profils = profils;
    this.mapper = mapper;
    this.ecoleMapper = ecoleMapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Utilisateur connecté et ses rôles dans l'école demandée")
  MoiDto moi() {
    return mapper.versDto(profils.profilCourant());
  }

  /** Hors école : sert à choisir l'école, donc sans X-Tenant-Id (voir SecuriteConfig). */
  @GetMapping(path = "/ecoles", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Écoles dans lesquelles l'utilisateur connecté a un rôle")
  List<EcoleDto> mesEcoles() {
    return ecoleMapper.versDtos(profils.mesEcoles());
  }
}
