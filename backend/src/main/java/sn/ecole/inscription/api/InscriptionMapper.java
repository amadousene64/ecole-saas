package sn.ecole.inscription.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.inscription.application.InscriptionService.VueInscription;

@Mapper
interface InscriptionMapper {

  @Mapping(target = ".", source = "inscription")
  @Mapping(target = "classe", source = "classe")
  @Mapping(target = "optionIds", source = "options")
  @Mapping(target = "avertissements", source = "avertissements")
  InscriptionDto versDto(VueInscription vue);

  List<InscriptionDto> versDtos(List<VueInscription> vues);
}
