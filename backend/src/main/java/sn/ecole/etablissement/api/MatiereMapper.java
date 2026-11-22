package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.Matiere;

@Mapper
interface MatiereMapper {

  MatiereDto versDto(Matiere matiere);

  List<MatiereDto> versDtos(List<Matiere> matieres);
}
