package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024 MDRMine
 * Modified from 2002-2019 FlyMine 
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.math.NumberUtils;

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
    // TODO: add "-" and "NA" as no-limit
    private static final Pattern P_AGE_NOT_APPLICABLE = Pattern.compile(".*(not\\h*applicable|N/A|no\\h*limit|no).*", Pattern.CASE_INSENSITIVE);  // N/A / No limit
    private static final Pattern P_AGE_NOT_STATED = Pattern.compile(".*(none|not\\h*stated).*", Pattern.CASE_INSENSITIVE);  // Not stated
    // [number][unit] possibly with gt/lt in front (gte/lte is not interesting here)
    private static final Pattern P_AGE = Pattern.compile("[^0-9]*([<>][^=])?\\h*([0-9]+\\.?[0-9]*)\\h*(minute|hour|day|week|month|year|age)?.*", Pattern.CASE_INSENSITIVE);
    private static final String STR_AGE_NOT_APPLICABLE = "N/A";
    private static final String STR_AGE_NOT_STATED = "Not stated";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSS");

    private static final String DATASET_TITLE = "ICTRPWeek22July2024";
    private static final String DATA_SOURCE_NAME = "WHO";

    private String headersFilePath = "";
    private String logDir = "";
    private Map<String, Integer> fieldsToInd;
    private Writer logWriter = null;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public WhoConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * headersFilePath property in project.xml
     */
    public void setHeadersFilePath(String fp) {
        this.headersFilePath = fp;
    }

    /**
     * logDir property in project.xml
     * 
     * @param logDir
     */
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    /**
     * TODO
     */
    public void startLogging() throws Exception {
        if (!this.logDir.equals("")) {
            String current_timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                
            Path logDir = Paths.get(this.logDir);
            if (!Files.exists(logDir)) Files.createDirectories(logDir);

            Path logFile = Paths.get(logDir.toString(), current_timestamp + "_who.log");
            this.logWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile.toString()), "utf-8"));
        } else {
            throw new Exception("Log folder not specified");
        }
    }

    /**
     * TODO
     */
    public void stopLogging() throws IOException {
        if (this.logWriter != null) {
            this.logWriter.close();
        }
    }

    /**
     * TODO Opened BufferedReader is passed as argument (from FileConverterTask.execute())
     */
    public void process(Reader reader) throws Exception {
        this.startLogging();

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

        this.stopLogging();
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    /**
     * TODO
     */
    public void storeValues(String[] lineValues) throws Exception {
        // TODO: something with last_update
        Item study = createItem("Study");

        // TODO: DOs
        // TODO: properties file for age units thing
        // TODO: nohup experiment

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

        /* Min age */
        this.parseAgeField(this.getAndCleanValue(lineValues, "Agemin"), "minAge", "minAgeUnit", study, trialID);
        /* Max age */
        this.parseAgeField(this.getAndCleanValue(lineValues, "Agemax"), "maxAge", "maxAgeUnit", study, trialID);

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

    /**
     * TODO Set values for various public name fields of a Study People instance
     */
    public void setPublicNameValues(Item studyPeople, String fullName) {
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

    /**
     * TODO
     */
    public void parseAgeField(String ageStr, String ageAttr, String unitAttr, Item study, String trialID) {
        if (!ageStr.isEmpty()) {
            // Check for N/A or no limit
            Matcher mAgeNotApplicable = P_AGE_NOT_APPLICABLE.matcher(ageStr);
            if (mAgeNotApplicable.matches()) {
                study.setAttribute(ageAttr, STR_AGE_NOT_APPLICABLE);
            } else {
                Matcher mAgeNotStated = P_AGE_NOT_STATED.matcher(ageStr);
                if (mAgeNotStated.matches()) {
                    study.setAttribute(ageAttr, STR_AGE_NOT_STATED);
                } else {
                    Matcher mAge = P_AGE.matcher(ageStr);
                    boolean successful = false;
                    if (mAge.matches()) {
                        String g1 = mAge.group(1);  // GT or LT
                        String g2 = mAge.group(2);  // age value
                        String g3 = mAge.group(3);  // age unit
                        if (!(g2 == null)) {
                            if (NumberUtils.isParsable(g2)) {
                                if (NumberUtils.isDigits(g2)) {
                                    int ageNumber = Integer.parseInt(g2);

                                    if (g1 != null) {    // GT/LT
                                        if (ageAttr.equalsIgnoreCase("minage")) {
                                            if (g1.equals(">")) {
                                                ageNumber++;
                                            } else {
                                                this.writeLog(trialID + " Wrong inequality sign for minAgeUnit: "
                                                                + g1 + " full string: " + ageStr);
                                            }
                                        } else if (ageAttr.equalsIgnoreCase("maxage")) {
                                            if (g1.equals("<")) {
                                                ageNumber--;
                                            } else {
                                                this.writeLog(trialID + " Wrong inequality sign for maxAgeUnit: "
                                                                + g1 + " full string: " + ageStr);
                                            }
                                        }
                                    }

                                    study.setAttribute(ageAttr, String.valueOf(ageNumber));
                                } else {    // Case where min age value is float
                                    study.setAttribute(ageAttr, g2);
                                }

                                if (!(g3 == null)) {
                                    study.setAttribute(unitAttr, WhoConverter.normaliseUnit(g3));
                                } else {    // If no unit, we assume it's years
                                    study.setAttribute(unitAttr, "Years");
                                }

                                successful = true;
                            } else {
                                this.writeLog(trialID + " Wrong format minAge value: " 
                                                + g2 + " full string: " + ageStr);
                            }
                        }
                        if (!successful) {
                            this.writeLog(trialID + " Couldn't parse " + ageAttr + " and " + unitAttr + " properly, string: " + ageStr + 
                                ", parsed groups: -g1: " + g1 + " -g2: " + g2 + " -g3: " + g3);
                        }
                    } else {
                        this.writeLog(trialID + " Couldn't parse " + ageAttr + " and " + unitAttr + " properly, string: " + ageStr + 
                            ", no matches found");
                    }
                }
            }
        }
    }

    /**
     * TODO
     */
    public void writeLog(String text) {
        try {
            if (this.logWriter != null) {
                this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + text + "\n");
                this.logWriter.flush();
            } else {
                System.out.println("WHO - Log writer is null (cannot write logs)");
            }
        } catch(IOException e) {
            System.out.println("WHO - Couldn't write to log file");
        }
    }

    /**
     * Normalise word and add trailing s to unit
     * @see #normaliseWord()
     */
    public static String normaliseUnit(String u) {
        return WhoConverter.normaliseWord(u) + "s";
    }

    /**
     * Uppercase first letter and lowercase the rest
     */
    public static String normaliseWord(String w) {
        if (w.length() > 0) {
            w = w.substring(0,1).toUpperCase() + w.substring(1).toLowerCase();
        }
        return w;
    }

    /**
     * Get field value from array of values using a field's position-lookup Map, value is also cleaned
     * @see #cleanValue()
     */
    public String getAndCleanValue(String[] lineValues, String field) {
        // TODO: handle errors
        return WhoConverter.cleanValue(lineValues[this.fieldsToInd.get(field)].strip());
    }

    /**
     * Remove extra quotes, unescape HTML chars, and strip the string of empty spaces
     * @see #unescapeHtml()
     * @see #removeQuotes()
     */
    public static String cleanValue(String s) {
        /*  */
        return WhoConverter.unescapeHtml(WhoConverter.removeQuotes(s)).strip();
    }

    /**
     * TODO
     */
    public static String unescapeHtml(String s) {
        return StringEscapeUtils.unescapeHtml4(s);
    }

    /**
     * TODO
     * Unfortunately opencsv only transforms triple double-quoted values into single double-quotes values,
           so we have to remove the remaining quotes manually
     */
    public static String removeQuotes(String s) {
        if (s != null && s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"') {
            return s.substring(1, s.length()-1);
        }
        return s;
    }

    /**
     * TODO
     */
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

    /**
     * TODO
     */
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
