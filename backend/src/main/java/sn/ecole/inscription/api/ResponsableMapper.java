package sn.ecole.inscription.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.inscription.application.LienParentalService.VueLien;
import sn.ecole.inscription.domain.Responsable;

@Mapper
interface ResponsableMapper {

  @Mapping(target = "compteActive", expression = "java(r.getUtilisateurId() != null)")
  ResponsableDto versDto(Responsable r);

  List<ResponsableDto> versDtos(List<Responsable> responsables);

  @Mapping(target = ".", source = "lien")
  @Mapping(target = "id", source = "lien.id")
  @Mapping(target = "version", source = "lien.version")
  @Mapping(target = "responsable", source = "responsable")
  LienParentalDto versDto(VueLien vue);

  List<LienParentalDto> versDtosLiens(List<VueLien> vues);
}
