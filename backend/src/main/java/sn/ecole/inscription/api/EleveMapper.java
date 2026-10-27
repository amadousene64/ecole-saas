package sn.ecole.inscription.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.inscription.application.EleveService.VueEleve;
import sn.ecole.inscription.domain.Eleve;

@Mapper
interface EleveMapper {

  @Mapping(target = ".", source = "eleve")
  @Mapping(target = "photoUrl", source = "photoUrl")
  @Mapping(target = "compteActive", expression = "java(vue.eleve().getUtilisateurId() != null)")
  EleveDto versDto(VueEleve vue);

  EleveResumeDto versResume(Eleve eleve);

  List<EleveResumeDto> versResumes(List<Eleve> eleves);
}
