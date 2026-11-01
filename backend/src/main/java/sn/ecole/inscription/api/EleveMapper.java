package sn.ecole.inscription.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.inscription.application.EleveService.VueEleve;
import sn.ecole.inscription.application.LigneEleve;
import sn.ecole.inscription.application.ResumeEleve;

@Mapper
interface EleveMapper {

  @Mapping(target = ".", source = "eleve")
  @Mapping(target = "photoUrl", source = "photoUrl")
  @Mapping(target = "compteActive", expression = "java(vue.eleve().getUtilisateurId() != null)")
  EleveDto versDto(VueEleve vue);

  @Mapping(target = ".", source = "eleve")
  @Mapping(target = "classe", source = "classe")
  EleveResumeDto versResume(ResumeEleve resume);

  List<EleveResumeDto> versResumes(List<ResumeEleve> resumes);

  @Mapping(target = ".", source = "eleve")
  EleveListeDto versLigne(LigneEleve ligne);

  List<EleveListeDto> versLignes(List<LigneEleve> lignes);
}
