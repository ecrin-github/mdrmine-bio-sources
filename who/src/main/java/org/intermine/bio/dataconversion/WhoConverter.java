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
    // TODO: to somewhere else? headers file
    private static final String DATASET_TITLE = "ICTRPWeek22July2024";
    private static final String DATA_SOURCE_NAME = "WHO";
    private String headersFilePath = "";

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

        final Map<String, Integer> fieldsToInd = this.getHeaders();

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

        /* BufferedReader is closed in FileConverterTask.execute() */
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
