package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.etablissement.application.NiveauService.VueNiveau;

@Mapper
interface NiveauMapper {

  @Mapping(target = ".", source = "niveau")
  NiveauDto versDto(VueNiveau vue);

  List<NiveauDto> versDtos(List<VueNiveau> vues);
}
