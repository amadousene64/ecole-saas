package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.Creneau;

@Mapper
interface CreneauMapper {

  CreneauDto versDto(Creneau creneau);

  List<CreneauDto> versDtos(List<Creneau> creneaux);
}
