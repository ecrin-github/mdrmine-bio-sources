package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.Reader;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

import org.apache.commons.text.StringEscapeUtils;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.exceptions.CsvMalformedLineException;


/**
 * 
 * @author
 */
public class WhoConverter extends BioFileConverter
{
    private static final String DATASET_TITLE = "ICTRPWeek22July2024";
    private static final String DATA_SOURCE_NAME = "WHO";
    private String headersFilePath = "";
    private Map<String, Integer> fieldsToInd;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public WhoConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    public void setHeadersFilePath(String fp) {
        /* headers.filePath property in project.xml */
        this.headersFilePath = fp;
    }

    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */

        this.fieldsToInd = this.getHeaders();

        final CSVParser parser = new CSVParserBuilder()
                                        .withSeparator(',')
                                        .withQuoteChar('"')
                                        .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                                            .withCSVParser(parser)
                                            .build();

        boolean skipNext = false;

        // nextLine[] is an array of values from the line
        String[] nextLine = csvReader.readNext();

        // TODO: performance tests? compared to iterator
        while (nextLine != null) {
            if (!skipNext) {
                this.storeValues(nextLine);
            } else {
                skipNext = false;
            }
            try {
                nextLine = csvReader.readNext();
            } catch (CsvMalformedLineException e) {
                nextLine = new String[0];
                skipNext = true;
            }
        }

        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    public void storeValues(String[] lineValues) throws Exception {
        // TODO: something with last_update
        Item study = createItem("Study");

        // TODO: DOs

        // TODO: skip creating study if ID is missing?
        String trialID = this.getAndCleanValue(lineValues, "TrialID");
        if (!trialID.isEmpty()) {
            Item studyIdentifier = createItem("StudyIdentifier");
            studyIdentifier.setAttribute("identifierValue", trialID);
            studyIdentifier.setReference("study", study);

            String url = this.getAndCleanValue(lineValues, "url");
            if (!url.isEmpty()) {
                studyIdentifier.setAttribute("identifierLink", url);
            }
            store(studyIdentifier);
            study.addToCollection("studyIdentifiers", studyIdentifier);
            // TODO: identifier type -> ask Sergio
            // TODO: date?
        }

        String publicTitle = this.getAndCleanValue(lineValues, "public_title");
        if (!publicTitle.isEmpty()) {
            Item studyTitle = createItem("StudyTitle");
            study.setAttribute("displayTitle", publicTitle);

            studyTitle.setAttribute("titleType", "Public title");
            studyTitle.setAttribute("titleText", publicTitle);
            studyTitle.setReference("study", study);
            store(studyTitle);
            study.addToCollection("studyTitles", studyTitle);
        } else {
            study.setAttribute("displayTitle", "Unknown study title");
        }

        // TODO: not working as intended
        Item studySource = createItem("StudySource");
        studySource.setAttribute("sourceName", "WHO");
        studySource.setReference("study", study);
        store(studySource);
        study.addToCollection("studySources", studySource);

        // TODO: secondary IDs -> ask Sergio

        String scientificTitle = this.getAndCleanValue(lineValues, "Scientific_title");
        if (!scientificTitle.isEmpty()) {
            Item studyTitle = createItem("StudyTitle");
            studyTitle.setAttribute("titleType", "Scientific Title");
            studyTitle.setAttribute("titleText", scientificTitle);
            studyTitle.setReference("study", study);
            store(studyTitle);
            study.addToCollection("studyTitles", studyTitle);
        }

        // TODO: URL?

        String minAge = this.getAndCleanValue(lineValues, "Agemin");
        if (!minAge.isEmpty()) {
            String[] minAgeSplit = minAge.split(" ");
            if (minAgeSplit.length > 0) {
                String minAgeValue = minAgeSplit[0].strip();
                if (!minAgeValue.isEmpty() && WhoConverter.isPosWholeNumber(minAgeValue)) {
                    study.setAttribute("minAge", minAgeValue);
                }

                if (minAgeSplit.length > 1) {
                    // TODO: build a standardized list of values for unit
                    String minAgeUnit = minAgeSplit[1].strip();
                    if (!minAgeUnit.isEmpty()) {
                        study.setAttribute("minAgeUnit", minAgeUnit);
                    }
                }
            }
        }

        String maxAge = this.getAndCleanValue(lineValues, "Agemax");
        if (!maxAge.isEmpty()) {
            String[] maxAgeSplit = maxAge.split(" ");
            if (maxAgeSplit.length > 0) {
                String maxAgeValue = maxAgeSplit[0].strip();
                if (!maxAgeValue.isEmpty() && WhoConverter.isPosWholeNumber(maxAgeValue)) {
                    study.setAttribute("maxAge", maxAgeValue);
                }

                if (maxAgeSplit.length > 1) {
                    String maxAgeUnit = maxAgeSplit[1].strip();
                    if (!maxAgeUnit.isEmpty()) {
                        study.setAttribute("maxAgeUnit", maxAgeUnit);
                    }
                }
            }
        }

        // TODO: test with null value

        // TODO: index the values from the last one (e.g. 4 total but 2 present)
        String[] firstNames = {};
        String[] lastNames = {};
        String[] affiliations = {};
        
        // TODO: this might always be empty, check (currently unused at least)
        String firstNamesString = this.getAndCleanValue(lineValues, "Public_Contact_Firstname");
        if (!firstNamesString.isEmpty()) {
            firstNames = firstNamesString.split(";");
        }

        String lastNamesString = this.getAndCleanValue(lineValues, "Public_Contact_Lastname");
        if (!lastNamesString.isEmpty()) {
            lastNames = lastNamesString.split(";");
        }

        String affiliationsString = this.getAndCleanValue(lineValues, "Public_Contact_Affiliation");
        if (!affiliationsString.isEmpty()) {
            affiliations = affiliationsString.split(";");
        }
        /* Address and phone strings present in WHO are unused here since they don't appear in our model */
        int maxLen = Math.max(Math.max(firstNames.length, lastNames.length), affiliations.length);

        if (maxLen > 0) {
            for (int i = 0; i < maxLen; i++) {
                // TODO: Contributor type seems unknown? or is there an order/pre-defined roles in the WHO values?
                Item sp = createItem("StudyPeople");
                // TODO: case with semi colons on both ends?
                if ((lastNames.length == maxLen || (lastNamesString.endsWith(";") && i < lastNames.length)) && !lastNames[i].isBlank()) {
                    // Case where everyone has names or people missing names are at the end of the list
                    this.setPublicNameValues(sp, lastNames[i]);
                } else if (lastNamesString.startsWith(";") && (lastNames.length + i - maxLen >= 0) && !lastNames[lastNames.length + i - maxLen].isBlank()) {
                    // Case where people missing names are at the beginning of the list
                    this.setPublicNameValues(sp, lastNames[lastNames.length + i - maxLen]);
                }
                // Difference between person affiliation and organisation name?
                // TODO: not sure how to handle affiliation, there seem to be only one every time?
                if ((affiliations.length == maxLen || (affiliationsString.endsWith(";") && i < affiliations.length)) && !affiliations[i].isBlank()) {
                    sp.setAttribute("personAffiliation", affiliations[i]);
                } else if (affiliationsString.startsWith(";") && (affiliations.length + i - maxLen >= 0) && !affiliations[affiliations.length + i - maxLen].isBlank()) {
                    sp.setAttribute("personAffiliation", affiliations[affiliations.length + i - maxLen]);
                }

                sp.setReference("study", study);
                store(sp);
                study.addToCollection("studyPeople", sp);
            }
        }

        /*<class name="StudyPeople" is-interface="true">
            <reference name="study" referenced-type="Study" reverse-reference="studyPeople"/>
            <attribute name="contribType" type="java.lang.Integer"/>
            <attribute name="personGivenName" type="java.lang.String"/>
            <attribute name="personFamilyName" type="java.lang.String"/>
            <attribute name="personFullName" type="java.lang.String"/>
            <attribute name="orcid" type="java.lang.String"/>
            <attribute name="personAffiliation" type="java.lang.String"/>
            <attribute name="organisation" type="java.lang.Integer"/>
            <attribute name="organisationName" type="java.lang.String"/>
            <attribute name="organisationRor" type="java.lang.String"/>
        </class>*/

        store(study);
    }

    public void setPublicNameValues(Item studyPeople, String fullName) {
        /* Set values for various public name fields of a Study People instance */
        fullName = fullName.strip();
        String[] separatedName = fullName.split(" ");

        if (separatedName.length > 1) {
            studyPeople.setAttribute("personGivenName", separatedName[0]);
            studyPeople.setAttribute("personFamilyName", separatedName[separatedName.length-1]);
        } else {
            studyPeople.setAttribute("personFamilyName", fullName);
        }
        studyPeople.setAttribute("personFullName", fullName); 
    }

    public String getAndCleanValue(String[] lineValues, String field) {
        /* Get field value from array of values using a field's position-lookup Map, value is also cleaned (see cleanValue()) */
        // TODO: handle errors
        return WhoConverter.cleanValue(lineValues[this.fieldsToInd.get(field)].strip());
    }

    public static String cleanValue(String s) {
        /* Removing extra quotes (see removeQuotes() and stripping the string of empty spaces) */
        return WhoConverter.unescapeHtml(WhoConverter.removeQuotes(s)).strip();
    }

    public static String unescapeHtml(String s) {
        return StringEscapeUtils.unescapeHtml4(s);
    }

    public static String removeQuotes(String s) {
        /* Unfortunately opencsv only transforms triple double-quoted values into single double-quotes values,
           so we have to remove the remaining quotes manually */
        if (s != null && s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"') {
            return s.substring(1, s.length()-1);
        }
        return s;
    }

    public static boolean isPosWholeNumber(String s) {
        if (s.length() == 0) { return false; }
        
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                continue;
            }
            return false;
        }

        return true;
    }

    public Map<String, Integer> getHeaders() throws Exception {
        if (this.headersFilePath.equals("")) {
            throw new Exception("headersFilePath property not set in mdrmine project.xml");
        }

        if (!(new File(this.headersFilePath).isFile())) {
            throw new Exception("WHO Headers file does not exist (path tested: " + this.headersFilePath + " )");
        }

        List<String> fileContent = Files.readAllLines(Paths.get(this.headersFilePath), StandardCharsets.UTF_8);
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();

        if (fileContent.size() > 0) {
            String headersLine = String.join("", fileContent).strip();
            // Deleting the invisible \FEFF unicode character at the beginning of the header file
            if (Integer.toHexString(headersLine.charAt(0) | 0x10000).substring(1).toLowerCase().equals("feff")) {
                headersLine = headersLine.substring(1);
            }
            String[] fields = headersLine.split(",");
            
            for (int ind = 0; ind < fields.length; ind++) {
                fieldsToInd.put(fields[ind], ind);
            }
        } else {
            throw new Exception("WHO Headers file is empty");
        }

        return fieldsToInd;
    }
}
