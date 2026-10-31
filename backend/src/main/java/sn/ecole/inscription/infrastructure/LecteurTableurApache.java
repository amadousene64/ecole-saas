package sn.ecole.inscription.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import sn.ecole.inscription.domain.LecteurTableur;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/**
 * CSV (commons-csv : séparateur « ; » ou « , » détecté, UTF-8 avec ou sans BOM, sinon Windows-1252)
 * et XLSX (Apache POI, première feuille). Le format vient du contenu : un XLSX est une archive ZIP
 * (« PK »). Garde-fous : taille bornée avant lecture, archive protégée contre les bombes de
 * décompression (ZipSecureFile), nombre de lignes et de colonnes borné ; les formules ne sont
 * jamais évaluées (seule la valeur en cache est lue).
 */
@Component
class LecteurTableurApache implements LecteurTableur {

  private static final int COLONNES_MAX = 40;
  private static final int CELLULE_MAX = 500;

  static {
    // Taux de compression minimal et tailles maximales des entrées de l'archive XLSX.
    ZipSecureFile.setMinInflateRatio(0.01);
    ZipSecureFile.setMaxEntrySize(50_000_000L);
    ZipSecureFile.setMaxTextSize(20_000_000L);
  }

  @Override
  public List<Ligne> lire(byte[] contenu) {
    if (contenu == null || contenu.length == 0) {
      throw invalide("Le fichier est vide.");
    }
    if (contenu.length > TAILLE_MAX) {
      throw invalide("Fichier trop volumineux (2 Mo au plus).");
    }
    boolean zip = contenu.length > 3 && contenu[0] == 'P' && contenu[1] == 'K';
    List<List<String>> brut = zip ? lireXlsx(contenu) : lireCsv(contenu);
    return enLignes(brut);
  }

  private static List<List<String>> lireCsv(byte[] contenu) {
    String texte = decoder(contenu);
    if (texte.indexOf('\0') >= 0) {
      throw invalide("Seuls les fichiers CSV et XLSX sont acceptés.");
    }
    String premiere = texte.lines().findFirst().orElse("");
    char separateur =
        premiere.chars().filter(c -> c == ';').count()
                >= premiere.chars().filter(c -> c == ',').count()
            ? ';'
            : ',';
    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(separateur)
            .setIgnoreSurroundingSpaces(true)
            .setMaxRows(LIGNES_MAX + 50L)
            .get();
    List<List<String>> lignes = new ArrayList<>();
    try (CSVParser analyseur = CSVParser.parse(new StringReader(texte), format)) {
      for (CSVRecord enregistrement : analyseur) {
        if (lignes.size() > LIGNES_MAX + 1) {
          throw trop();
        }
        List<String> cellules = new ArrayList<>();
        enregistrement.forEach(cellules::add);
        lignes.add(cellules);
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof ErreurMetier erreur) {
        throw erreur;
      }
      throw invalide("Fichier CSV illisible.");
    }
    return lignes;
  }

  private static List<List<String>> lireXlsx(byte[] contenu) {
    List<List<String>> lignes = new ArrayList<>();
    DataFormatter formateur = new DataFormatter();
    try (InputStream flux = new ByteArrayInputStream(contenu);
        Workbook classeur = new XSSFWorkbook(flux)) {
      if (classeur.getNumberOfSheets() == 0) {
        throw invalide("Le classeur ne contient aucune feuille.");
      }
      Sheet feuille = classeur.getSheetAt(0);
      if (feuille.getLastRowNum() > LIGNES_MAX + 1) {
        throw trop();
      }
      for (int i = 0; i <= feuille.getLastRowNum(); i++) {
        Row ligne = feuille.getRow(i);
        List<String> cellules = new ArrayList<>();
        if (ligne != null) {
          int derniere = Math.min(Math.max(ligne.getLastCellNum(), 0), COLONNES_MAX);
          for (int j = 0; j < derniere; j++) {
            cellules.add(texte(ligne.getCell(j), formateur));
          }
        }
        lignes.add(cellules);
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof ErreurMetier erreur) {
        throw erreur;
      }
      throw invalide("Fichier Excel illisible (format XLSX attendu).");
    }
    return lignes;
  }

  /**
   * Valeur affichée de la cellule ; date en AAAA-MM-JJ ; formule : valeur en cache, jamais évaluée.
   */
  private static String texte(Cell cellule, DataFormatter formateur) {
    if (cellule == null) {
      return "";
    }
    CellType type =
        cellule.getCellType() == CellType.FORMULA
            ? cellule.getCachedFormulaResultType()
            : cellule.getCellType();
    if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cellule)) {
      return cellule
          .getLocalDateTimeCellValue()
          .toLocalDate()
          .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
    if (type == CellType.NUMERIC) {
      double v = cellule.getNumericCellValue();
      return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
    if (type == CellType.STRING) {
      return cellule.getStringCellValue();
    }
    if (type == CellType.BOOLEAN) {
      return String.valueOf(cellule.getBooleanCellValue());
    }
    return cellule.getCellType() == CellType.FORMULA ? "" : formateur.formatCellValue(cellule);
  }

  /** En-têtes = première ligne non vide ; lignes vides ignorées ; numéros de ligne du fichier. */
  private static List<Ligne> enLignes(List<List<String>> brut) {
    int debut = 0;
    while (debut < brut.size() && vide(brut.get(debut))) {
      debut++;
    }
    if (debut == brut.size()) {
      throw invalide("Le fichier ne contient aucune ligne.");
    }
    List<String> entetes = brut.get(debut).stream().map(String::strip).toList();
    if (entetes.size() > COLONNES_MAX) {
      throw invalide("Trop de colonnes (" + COLONNES_MAX + " au plus).");
    }
    List<Ligne> lignes = new ArrayList<>();
    for (int i = debut + 1; i < brut.size(); i++) {
      List<String> cellules = brut.get(i);
      if (vide(cellules)) {
        continue;
      }
      Map<String, String> valeurs = new LinkedHashMap<>();
      for (int j = 0; j < entetes.size(); j++) {
        String valeur = j < cellules.size() ? cellules.get(j).strip() : "";
        if (valeur.length() > CELLULE_MAX) {
          valeur = valeur.substring(0, CELLULE_MAX);
        }
        if (!entetes.get(j).isEmpty()) {
          valeurs.put(entetes.get(j), valeur);
        }
      }
      lignes.add(new Ligne(i + 1, valeurs));
    }
    if (lignes.size() > LIGNES_MAX) {
      throw trop();
    }
    return lignes;
  }

  private static boolean vide(List<String> cellules) {
    return cellules.stream().allMatch(c -> c == null || c.isBlank());
  }

  /**
   * UTF-8 (BOM retiré) si le contenu est de l'UTF-8 valide, sinon Windows-1252 (Excel français).
   */
  private static String decoder(byte[] contenu) {
    int debut =
        contenu.length >= 3
                && (contenu[0] & 0xFF) == 0xEF
                && (contenu[1] & 0xFF) == 0xBB
                && (contenu[2] & 0xFF) == 0xBF
            ? 3
            : 0;
    ByteBuffer octets = ByteBuffer.wrap(contenu, debut, contenu.length - debut);
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(octets)
          .toString();
    } catch (CharacterCodingException e) {
      return new String(contenu, debut, contenu.length - debut, Charset.forName("windows-1252"));
    }
  }

  private static ErreurMetier invalide(String message) {
    return new ErreurMetier("FICHIER_INVALIDE", TypeErreur.INVALIDE, message);
  }

  private static ErreurMetier trop() {
    return new ErreurMetier(
        "IMPORT_TROP_GRAND",
        TypeErreur.INVALIDE,
        "Trop de lignes : " + LIGNES_MAX + " élèves au plus par fichier.");
  }
}
