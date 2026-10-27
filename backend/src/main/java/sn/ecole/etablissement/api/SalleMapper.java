package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.Salle;

@Mapper
interface SalleMapper {

  SalleDto versDto(Salle salle);

  List<SalleDto> versDtos(List<Salle> salles);
}
