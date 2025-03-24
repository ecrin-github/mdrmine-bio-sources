package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024-2025 MDRMine
 * Modified from 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;



/**
 * 
 * @author
 */
public class CtgConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "CTG-Studies";
    private static final String DATA_SOURCE_NAME = "CTG";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public CtgConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */

        final CSVParser parser = new CSVParserBuilder()
                                        .withSeparator(',')
                                        // .withQuoteChar('"')
                                        .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                                            .withCSVParser(parser)
                                            .build();

        /* Headers */
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();
        String[] fields = csvReader.readNext();

        if (fields.length == 0) {
            throw new Exception("CTG data file is empty (failed getting headers)");
        }

        for (int ind = 0; ind < fields.length; ind++) {
            fieldsToInd.put(fields[ind], ind);
        }

        /* Reading file */
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
        Item study = createItem("Study");

        String displayTitle = CtgConverter.removeQuotes(values[fieldsToInd.get("Study Title")].strip());
        if (!displayTitle.isEmpty()) {
            study.setAttribute("displayTitle", displayTitle);
        } else {
            study.setAttribute("displayTitle", "Unknown study title");
        }
        
        Item studySource = createItem("StudySource");
        studySource.setAttribute("sourceName", "CTG");
        studySource.setReference("study", study);
        store(studySource);

        String briefDescription = CtgConverter.removeQuotes(values[fieldsToInd.get("Brief Summary")].strip());
        if (!briefDescription.isEmpty()) {
            study.setAttribute("briefDescription", briefDescription);
        }

        Item studyIdentifier = createItem("StudyIdentifier");
        String trialID = CtgConverter.removeQuotes(values[fieldsToInd.get("NCT Number")].strip());
        if (!trialID.isEmpty()) {
            studyIdentifier.setAttribute("identifierValue", trialID);
        }
        // TODO: add study identifier type id
        studyIdentifier.setReference("study", study);
        store(studyIdentifier);

        // TODO: add study gender elig id

        // Study collections
        study.addToCollection("studyIdentifiers", studyIdentifier);
        study.addToCollection("studySources", studySource);
        store(study);
    }

    public static String removeQuotes(String s) {
        // TODO move to util file
        if (s != null && s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"') {
            return s.substring(1, s.length()-1);
        }
        return s;
    }
}
