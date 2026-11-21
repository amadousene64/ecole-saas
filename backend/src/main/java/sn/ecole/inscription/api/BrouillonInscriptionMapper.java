package sn.ecole.inscription.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.inscription.api.BrouillonInscriptionDto.ContenuDto;
import sn.ecole.inscription.api.BrouillonInscriptionRequetes.BrouillonFinaliseDto;
import sn.ecole.inscription.api.BrouillonInscriptionRequetes.BrouillonResumeDto;
import sn.ecole.inscription.application.BrouillonInscriptionService.Contenu;
import sn.ecole.inscription.application.BrouillonInscriptionService.Finalisation;
import sn.ecole.inscription.application.BrouillonInscriptionService.Resume;
import sn.ecole.inscription.application.BrouillonInscriptionService.Vue;

@Mapper
interface BrouillonInscriptionMapper {

  BrouillonInscriptionDto versDto(Vue vue);

  List<BrouillonResumeDto> versResumes(List<Resume> resumes);

  @Mapping(target = "codeActivation", source = "code")
  BrouillonFinaliseDto versDto(Finalisation finalisation);

  Contenu versContenu(ContenuDto dto);
}
