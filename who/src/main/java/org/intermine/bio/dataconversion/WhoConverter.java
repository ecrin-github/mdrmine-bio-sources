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
    // TODO: to somewhere else?
    private static final String DATASET_TITLE = "ICTRPWeek22July2024";
    private static final String DATA_SOURCE_NAME = "WHO";
    private static final String HEADERS_FILE_PATH = "/home/ubuntu/data/who/ICTRP_headers.csv";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public WhoConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    public void process(Reader reader) throws Exception {
        final Map<String, Integer> fieldsToInd = this.getHeaders(HEADERS_FILE_PATH);

        final CSVParser parser = new CSVParserBuilder()
                                        .withSeparator(',')
                                        .withQuoteChar('"')
                                        .build();
        // final CSVParser parser = new CSVParserBuilder().withSeparator(',').withIgnoreQuotations(true).build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                                            .withCSVParser(parser)
                                            .build();

        boolean skipNext = false;

        // nextLine[] is an array of values from the line
        String[] nextLine = csvReader.readNext();

        // TODO: performance tests? compared to iterator
        while (nextLine != null) {
            if (!skipNext) {
                this.storeValues(fieldsToInd, nextLine);
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
    }

    public void storeValues(Map<String, Integer> fieldsToInd, String[] values) throws Exception {
        /* TODO: something with last_update */
        Item study = createItem("Study");
        String displayTitle = WhoConverter.removeQuotes(values[fieldsToInd.get("public_title")].strip());
        if (!displayTitle.isEmpty()) {
            study.setAttribute("displayTitle", displayTitle);
        } else {
            study.setAttribute("displayTitle", "Unknown study title");
        }

        Item studyIdentifier = createItem("StudyIdentifier");
        String trialID = WhoConverter.removeQuotes(values[fieldsToInd.get("TrialID")].strip());
        if (!trialID.isEmpty()) {
            studyIdentifier.setAttribute("identifierValue", trialID);
        }
        studyIdentifier.setAttribute("source", "source_example_value");
        studyIdentifier.setReference("study", study);
        store(studyIdentifier);

        // TODO: secondary IDs

        Item studyTitle = createItem("StudyTitle");
        String titleText = WhoConverter.removeQuotes(values[fieldsToInd.get("Scientific_title")].strip());
        if (!titleText.isEmpty()) {
            studyTitle.setAttribute("titleText", titleText);
        }
        studyTitle.setReference("study", study);
        store(studyTitle);

        String minAge = WhoConverter.removeQuotes(values[fieldsToInd.get("Agemin")].strip());
        if (!minAge.isEmpty()) {
            String[] minAgeSplit = minAge.split(" ");
            if (minAgeSplit.length > 0) {
                String minAgeValue = minAgeSplit[0].strip();
                if (!minAgeValue.isEmpty() && WhoConverter.isPosWholeNumber(minAgeValue)) {
                    study.setAttribute("minAge", minAgeValue);
                }

                if (minAgeSplit.length > 1) {
                    String minAgeUnit = minAgeSplit[1].strip();
                    // TODO: add minAge id
                }
            }
        }

        String maxAge = WhoConverter.removeQuotes(values[fieldsToInd.get("Agemax")].strip());
        if (!maxAge.isEmpty()) {
            String[] maxAgeSplit = maxAge.split(" ");
            if (maxAgeSplit.length > 0) {
                String maxAgeValue = maxAgeSplit[0].strip();
                if (!maxAgeValue.isEmpty() && WhoConverter.isPosWholeNumber(maxAgeValue)) {
                    study.setAttribute("maxAge", maxAgeValue);
                }

                if (maxAgeSplit.length > 1) {
                    String maxAgeUnit = maxAgeSplit[1].strip();
                    // TODO: add maxAge id
                }
            }
        }

        // Study collections
        study.addToCollection("studyIdentifiers", studyIdentifier);
        study.addToCollection("studyTitles", studyTitle);
        store(study);
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

    /**
     * 
     *
     * {@inheritDoc}
     */
    // public void processzzzzzzzzzzzzzzzzzzz(Reader reader) throws Exception {
    //     /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        
    //     String line;
    //     String[] values;
    //     Item study;
    //     Boolean firstCol = true;
    //     Boolean strFinished = true;
    //     Boolean startNewline = false;
    //     StringBuilder sb;
    //     int c;
    //     int quotes = 0;

    //     Map<String, Integer> fieldsToInd = this.getHeaders(HEADERS_FILE_PATH);

    //     if (fieldsToInd != null) {
    //         BufferedReader br = new BufferedReader(reader);
    //         // while ((line = br.readLine()) != null) {
    //         while ((c = br.read()) != -1) {
    //             /* Most values are delimited by triple quotes ("""[value]""")
    //                exceptions are ID (1st field), NULL values, and dates (AFAIK) */
    //             // TODO: need to do a custom parsing, careful of empty values ("""""")
    //             // TODO: use external csv library (this is nonsense) or look how MDR parses it in case
    //             c = (char) c;
                
    //             if (startNewline && c == "n") {
    //                 startNewline = false;

    //             } else {
    //                 if (strFinished) {
    //                     if (c == "\"") {    // Quotes
    //                         if (quotes == 3) {  // TODO: could break with """"""" (7 quotes) value
    //                             // Adding empty value ("""""")
    //                             values.append("");
    //                             quotes = 0;
    //                         }
    //                         quotes++;
    //                     } else if (c == ",") {  // Separator (comma)
    //                         quotes = 0;
    //                     } else if (c == "\\") {  // Might be newline
    //                         startNewline = true;
    //                     } else {    // Non-quotes/comma character found
    //                         strFinished = false;
    //                         if (quotes == 3) {  // End of the first 3 quotes surrounding the value
    //                             quotes = 0;
    //                             // Adding previous value to list of values
    //                             values.append(sb);
    //                             // Beginning new value string
    //                             sb = new StringBuilder(c);
    //                         } else {    // 1 or 2 quotes are in the middle of the value
    //                             while (quotes > 0) {
    //                                 sb.append("\"");
    //                                 quotes--;
    //                             }
    //                             sb.append(c);
    //                         }
    //                     }
    //                 } else {
    //                     if (c != "\"") {
    //                         sb.append(c);
    //                     } else {
    //                         quotes++;
    //                         strFinished = true;
    //                     }
    //                 }
    //             }

    //             study = createItem("Study");
    //             String displayTitle = values[fieldsToInd.get("public_title")].strip();
    //             if (!displayTitle.isEmpty()) {
    //                 study.setAttribute("displayTitle", displayTitle);
    //             } else {
    //                 study.setAttribute("displayTitle", "Unknown study title");
    //             }
    //             Item studyIdentifier = createItem("StudyIdentifier");
    //             String trialID = values[fieldsToInd.get("TrialID")].strip();
    //             if (!trialID.isEmpty()) {
    //                 studyIdentifier.setAttribute("identifierValue", trialID);
    //             }
    //             studyIdentifier.setAttribute("source", "source_example_value");
    //             studyIdentifier.setReference("study5", study);
    //             store(studyIdentifier);

    //             Item studyTitle = createItem("StudyTitle");
    //             String titleText = values[fieldsToInd.get("Scientific_title")].strip();
    //             if (!titleText.isEmpty()) {
    //                 studyTitle.setAttribute("titleText", titleText);
    //             }
    //             studyTitle.setReference("study11", study);
    //             store(studyTitle);

    //             // Study collections
    //             study.addToCollection("studyIdentifiers", studyIdentifier);
    //             study.addToCollection("studyTitles", studyTitle);
    //             store(study);
    //         }
    //     } else {
    //         throw new Exception("Failed to parse header file");
    //     }

    //     /* BufferedReader is closed in FileConverterTask.execute() */
    // }

    public Map<String, Integer> getHeaders(String headersFilePath) throws Exception {
        // TODO: to some config file somewhere?
        List<String> fileContent = Files.readAllLines(Paths.get(headersFilePath), StandardCharsets.UTF_8);
        Map<String, Integer> fieldsToInd = null;

        if (fileContent.size() > 0) {
            String headersLine = String.join("", fileContent).strip();
            // Deleting the invisible \FEFF unicode character at the beginning of the header file
            if (Integer.toHexString(headersLine.charAt(0) | 0x10000).substring(1).toLowerCase().equals("feff")) {
                headersLine = headersLine.substring(1);
            }
            String[] fields = headersLine.split(",");
            fieldsToInd = new HashMap<String, Integer>();
            
            for (int ind = 0; ind < fields.length; ind++) {
                fieldsToInd.put(fields[ind], ind);
            }
        } else {
            throw new Exception("WHO Headers file is empty");
        }

        return fieldsToInd;
    }
}
