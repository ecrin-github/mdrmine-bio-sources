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

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

import java.io.Reader;
import java.util.Map;
import java.util.HashMap;


/**
 * 
 * @author
 */
public class BbmriConverter extends CacheConverter
{
    //
    private static final String DATASET_TITLE = "BBMRI Biosamples Manual mapping 20230903";
    private static final String DATA_SOURCE_NAME = "BBMRI";

    private Map<String, Item> studyMap = new HashMap<String, Item>();
    private Map<String, Integer> fieldsToInd;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public BbmriConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * TODO
     * 
     * @param reader
     * @throws Exception
     */
    public void parseData(Reader reader) throws Exception {
        final CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .build();

        boolean skipNext = false;

        this.fieldsToInd = this.getHeaders(csvReader.readNext());

        // nextLine[] is an array of values from the line
        String[] nextLine = csvReader.readNext();

        // TODO: performance tests? compared to iterator
        while (nextLine != null) {
            if (!skipNext) {
                Item study = null;

                String trialID = this.getAndCleanValue(nextLine, "study_id");

                if (this.studyMap.containsKey(trialID)) {
                    study = this.studyMap.get(trialID);
                } else {
                    study = this.createItem("Study");
                    study.setAttributeIfNotNull("primaryIdentifier", trialID);
                    store(study);
                    this.studyMap.put(trialID, study);
                }

                this.createAndStoreClassItem(study, "Biosample",
                    new String[][] { { "bbmriID", this.getAndCleanValue(nextLine, "bbmri_id") },
                            { "title", this.getAndCleanValue(nextLine, "title") },
                            { "description", this.getAndCleanValue(nextLine, "description") },
                            { "materialTypes", this.getAndCleanValue(nextLine, "material_types") },
                            { "accessUrl", this.getAndCleanValue(nextLine, "url") } });
            } else {
                skipNext = false;
            }

            try {
                nextLine = csvReader.readNext();
            } catch (CsvMalformedLineException e) {
                this.writeLog("Failed to parse line");
                nextLine = new String[0];
                skipNext = true;
            }
        }

        csvReader.close();
    }

    /**
     * TODO
     * TODO: also move to parent class (abstract)?
     * 
     * @return map of data file field names and their corresponding column index
     */
    public Map<String, Integer> getHeaders(String[] headersList) throws Exception {
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();

        if (headersList.length > 0) {
            for (int ind = 0; ind < headersList.length; ind++) {
                // Deleting the invisible \FEFF unicode character at the beginning of the header
                // file
                if (ind == 0 && Integer.toHexString(headersList[ind].charAt(0) | 0x10000).substring(1).toLowerCase()
                        .equals("feff")) {
                    headersList[ind] = headersList[ind].substring(1);
                }
                fieldsToInd.put(headersList[ind], ind);
            }
        } else {
            throw new Exception("BBMRI Headers are empty");
        }

        return fieldsToInd;
    }

    public String getAndCleanValue(String[] lineValues, String field) {
        // TODO: handle errors
        return this.cleanValue(lineValues[this.fieldsToInd.get(field)], true);
    }

    /**
     * TODO
     */
    public String cleanValue(String s, boolean strip) {
        if (strip) {
            return s.strip();
        }
        return s;
    }
}
