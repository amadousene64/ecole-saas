package sn.ecole.personnel.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.personnel.application.AffectationService.Vue;
import sn.ecole.personnel.application.AnnuairePersonnel.Membre;

@Mapper
interface AffectationMapper {

  @Mapping(target = ".", source = "affectation")
  @Mapping(target = "classe", source = "classe")
  @Mapping(target = "matiere", source = "matiere")
  @Mapping(target = "enCours", source = "enCours")
  AffectationDto versDto(Vue vue);

  List<AffectationDto> versDtos(List<Vue> vues);

  List<MembreDto> versMembres(List<Membre> membres);
}
