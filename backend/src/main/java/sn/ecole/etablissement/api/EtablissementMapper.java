package sn.ecole.etablissement.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.etablissement.application.EtablissementService.VueEtablissement;

@Mapper
interface EtablissementMapper {

  @Mapping(target = ".", source = "etablissement")
  EtablissementDto versDto(VueEtablissement vue);
}
