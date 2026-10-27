package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.AnneeScolaire;

@Mapper
interface AnneeScolaireMapper {

  AnneeScolaireDto versDto(AnneeScolaire annee);

  List<AnneeScolaireDto> versDtos(List<AnneeScolaire> annees);
}
