package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.Serie;

@Mapper
interface SerieMapper {

  SerieDto versDto(Serie serie);

  List<SerieDto> versDtos(List<Serie> series);
}
