package com.tzm.supafinder.utils;

import com.tzm.supafinder.model.HttpSection;
import com.tzm.supafinder.model.RegexEntity;
import com.tzm.supafinder.model.RegexEntityJsonAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tzm.supafinder.utils.Messages.getLocaleString;
import static com.tzm.supafinder.utils.Utils.createGsonBuilder;

/**
 * Utils to work with generic files and JSON/CSV files containing list of regexes.
 */
public class FileUtils {

    /**
     * Write all lines to a file.
     * <p>A newline is already added after each line</p>
     *
     * @param filepath The path of the file to write to
     * @param lines    the lines to write to the file. To each line is appended a newline.
     * @return true, if the write operation was successful. False, otherwise.
     */
    public static boolean writeLinesToFile(String filepath, List<String> lines) {
        try {
            PrintWriter pwt = new PrintWriter(filepath, StandardCharsets.UTF_8);
            lines.forEach(pwt::println);
            pwt.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Read all lines from a file
     *
     * @param filepath The path to the file to read
     * @return A list containing all the lines from the file. If an error occurred, null is returned instead.
     */
    public static List<String> readLinesFromFile(String filepath) {
        try {
            return Files.readAllLines(Path.of(filepath));
        } catch (IOException e) {
            return null;
        }
    }

    public static void exportRegexListToFile(String filepath, List<RegexEntity> regexEntities) {
        switch (FilenameUtils.getExtension(filepath).toUpperCase()) {
            case "JSON" -> writeLinesToFile(filepath, List.of(exportRegexListToJson(regexEntities, false)));
            case "CSV" -> writeLinesToFile(filepath, exportRegexListToCSV(regexEntities));
            default -> throw new NotImplementedException();
        }
    }

    public static List<String> exportRegexListToCSV(List<RegexEntity> regexEntities) {
        List<String> lines = new ArrayList<>();

        lines.add("\"description\",\"regex\",\"refinerRegex\",\"sections\"");
        regexEntities.forEach(regexEntity -> {
            String description = regexEntity.getDescription().replaceAll("\"", "\"\"");
            String regex = regexEntity.getRegex().replaceAll("\"", "\"\"");
            String refinerRegex = regexEntity.getRefinerRegex().orElse("");
            String sections = String
                    .join("|", HttpSection.serializeSections(regexEntity.getSections()))
                    .replaceAll("\"", "\"\"");
            lines.add(String.format("\"%s\",\"%s\",\"%s\",\"%s\"", description, regex, refinerRegex, sections));
        });
        return lines;
    }

    /**
     *
     * @param regexEntities
     * @param includeActive if true, the optional "active" field is included in the exported list
     * @return
     */
    public static String exportRegexListToJson(List<RegexEntity> regexEntities, boolean includeActive) {
        List<JsonObject> lines;
        lines = regexEntities
                .stream()
                .map(regexEntity -> {
                    JsonObject json = new JsonObject();
                    if (includeActive) json.addProperty("active", regexEntity.isActive());
                    json.addProperty("description", regexEntity.getDescription());
                    json.addProperty("regex", regexEntity.getRegex());
                    json.addProperty("importance", regexEntity.getImportance());
                    regexEntity.getRefinerRegex().ifPresent(s -> json.addProperty("refinerRegex", s));
                    JsonArray sections = new JsonArray();
                    HttpSection.serializeSections(regexEntity.getSections()).forEach(sections::add);
                    json.add("sections", sections);
                    return json;
                })
                .collect(Collectors.toList());

        Type tListEntries = (new TypeToken<ArrayList<JsonObject>>() {
        }).getType();
        return createGsonBuilder().toJson(lines, tListEntries);
    }

    /**
     * Import a list of regexes from a file of a supported type.
     * <p>The supported types are JSON, CSV, and YAML. The type of the file is inferred from the "filepath" extension.</p>
     *
     * @param filepath    the path to the file to import
     * @param regexesList a list where to import the regexes
     * @return A list of regexes that were already present inside the provided "regexesList".
     * @throws Exception If the specified file is of an unsupported type.
     */
    public static List<RegexEntity> importRegexListFromFile(String filepath, List<RegexEntity> regexesList) throws Exception {
        String upperPath = filepath.toUpperCase();

        if (upperPath.endsWith("YAML") || upperPath.endsWith("YML")) {
            return FileUtils.importRegexListFromYAML(filepath, regexesList);
        }

        List<String> lines = readLinesFromFile(filepath);
        if (Objects.isNull(lines)) return List.of();

        if (upperPath.endsWith("JSON")) {
            return FileUtils.importRegexListFromJSON(String.join("", lines), regexesList, false);
        } else if (upperPath.endsWith("CSV")) {
            return FileUtils.importRegexListFromCSV(lines, regexesList);
        } else {
            throw new Exception(getLocaleString("exception-listTypeNotSupported"));
        }
    }

    /**
     * Import a regex from a single YAML file
     *
     * @param filepath    path to the YAML file
     * @param regexesList list where to import the regex
     * @return List of regexes that were already present
     */
    public static List<RegexEntity> importRegexListFromYAML(String filepath, List<RegexEntity> regexesList) {
        List<RegexEntity> duplicateRegexes = new ArrayList<>();

        try {
            RegexEntity entity = YamlParser.parseYamlFile(Path.of(filepath));
            if (!regexesList.contains(entity)) {
                regexesList.add(entity);
            } else {
                duplicateRegexes.add(entity);
            }
        } catch (IOException e) {
            System.err.println("Error parsing YAML file " + filepath + ": " + e.getMessage());
        }

        return duplicateRegexes;
    }

    /**
     * Import regexes from a directory containing YAML files (recursive)
     *
     * @param directoryPath path to directory
     * @param regexesList   list where to import the regexes
     * @return List of regexes that were already present
     */
    public static List<RegexEntity> importRegexListFromYAMLDirectory(String directoryPath, List<RegexEntity> regexesList) {
        List<RegexEntity> duplicateRegexes = new ArrayList<>();

        try {
            List<RegexEntity> entities = YamlParser.parseYamlDirectory(Path.of(directoryPath).toFile());
            for (RegexEntity entity : entities) {
                if (!regexesList.contains(entity)) {
                    regexesList.add(entity);
                } else {
                    duplicateRegexes.add(entity);
                }
            }
        } catch (IOException e) {
            System.err.println("Error parsing YAML directory " + directoryPath + ": " + e.getMessage());
        }

        return duplicateRegexes;
    }

    /**
     * Import a list of regexes from CSV.
     *
     * @param csv         Lines of a CSV file representing many RegexEntity
     * @param regexesList a list where to import the regexes
     * @return A list of regexes that were already present inside the provided "regexesList".
     */
    public static List<RegexEntity> importRegexListFromCSV(List<String> csv, List<RegexEntity> regexesList) {
        List<RegexEntity> duplicateRegexes = new ArrayList<>();

        // skip header line if present
        int startRow = csv.get(0).startsWith("\"description\",\"regex\"") ? 1 : 0;
        csv.subList(startRow, csv.size())
                .stream()
                .map(RegexEntity::checkRegexEntityFromCSV)
                .flatMap(Optional::stream)
                .map(match -> new RegexEntity(
                        unescapeCsvQuotes(match.group(1)),
                        unescapeCsvQuotes(match.group(2)),
                        true,
                        HttpSection.deserializeSections(decodeSectionListFromCSV(match.group(4))),
                        unescapeCsvQuotes(match.group(3))))
                .forEachOrdered(newRegex -> {
                    if (!regexesList.contains(newRegex)) {
                        regexesList.add(newRegex);
                    } else {
                        duplicateRegexes.add(newRegex);
                    }
                });
        return duplicateRegexes;
    }

    /**
     * Import a list of regexes from JSON.
     *
     * @param json          JSON representing a list containing many RegexEntity
     * @param regexesList   a list where to import the regexes
     * @param includeActive if false, the optional "active" field is ignored while importing regexes.
     * @return A list of regexes that were already present inside the provided "regexesList".
     */
    public static List<RegexEntity> importRegexListFromJSON(String json, List<RegexEntity> regexesList, boolean includeActive) {
        List<RegexEntityJsonAdapter> regexList;
        List<RegexEntity> duplicateRegexes = new ArrayList<>();

        try {
            regexList = Utils.parseListFromJSON(json, RegexEntityJsonAdapter.class);
        } catch (JsonSyntaxException e) {
            return List.of();
        }
        Stream.of(regexList)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(element -> new RegexEntity(
                        element.getDescription(),
                        element.getRegex(),
                        !includeActive || element.isActive(),
                        HttpSection.deserializeSections(element.getSections()),
                        element.getRefinerRegex(),
                        element.getTests(),
                        null, // category
                        null, // tags
                        element.getImportance(),
                        false, // precheckNeeded
                        null, // prechecks
                        false, // caseInsensitive
                        false, // stopFirstOccurrence
                        null)) // ignorePatterns
                .forEachOrdered(newRegex -> {
                    if (!regexesList.contains(newRegex)) {
                        regexesList.add(newRegex);
                    } else {
                        duplicateRegexes.add(newRegex);
                    }
                });
        return duplicateRegexes;
    }

    private static List<String> decodeSectionListFromCSV(String encodedSections) {
        return Objects.isNull(encodedSections) ? null : List.of(unescapeCsvQuotes(encodedSections).split("\\|"));
    }

    /**
     * @param input The text to unescape
     * @return the input with "" converted to ", or an empty string if input is null
     */
    public static String unescapeCsvQuotes(String input) {
        return Objects.nonNull(input) ? input.replaceAll("\"\"", "\"") : "";
    }
}
