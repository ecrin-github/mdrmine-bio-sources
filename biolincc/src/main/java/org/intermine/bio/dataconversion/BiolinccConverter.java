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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 
 * @author
 */
public class BiolinccConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "BioLINCC full";
    private static final String DATA_SOURCE_NAME = "BioLINCC";
    private static final Pattern p = Pattern.compile("\\w+.*");

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public BiolinccConverter(ItemWriter writer, Model model) {
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
            throw new Exception("BioLINCC data file is empty (failed getting headers)");
        }

        for (int ind = 0; ind < fields.length; ind++) {
            if (ind == 0) {
                /* Opened file is passed to this function, so we can't change the encoding on read
                    to handle the BOM \uFEFF leading character, we have to remove it ourselves.
                    The character is read differently in Docker, so we match the header field name starting with a letter. */
                Matcher m = p.matcher(fields[ind]);
                if (m.find()) {
                    fieldsToInd.put(m.group(0), ind);
                } else {
                    throw new Exception("Couldn't properly parse BioLINCC first header value: '" + fields[ind] + "'");
                }
            } else {
                fieldsToInd.put(fields[ind], ind);
            }
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
        /* TODO: something with last_update */
        Item study = createItem("Study");
        String displayTitle = BiolinccConverter.removeQuotes(values[fieldsToInd.get("Study Name")].strip());
        if (!displayTitle.isEmpty()) {
            study.setAttribute("displayTitle", displayTitle);
        } else {
            study.setAttribute("displayTitle", "Unknown study title");
        }

        Item studySource = createItem("StudySource");
        studySource.setAttribute("sourceName", "BioLINCC");
        studySource.setReference("study", study);
        store(studySource);

        Item studyIdentifier = createItem("StudyIdentifier");
        String biolinccID = BiolinccConverter.removeQuotes(values[fieldsToInd.get("Accession Number")].strip());
        if (!biolinccID.isEmpty()) {
            studyIdentifier.setAttribute("identifierValue", biolinccID);
        }
        // TODO: java psql null exception on last line
        // Not adding NCT ids just yet because multiple BioLINCC entries have the same NCT link
        /*String ctgURL = BiolinccConverter.removeQuotes(values[fieldsToInd.get("Clinical trial urls")].strip());
        if (!ctgURL.isEmpty()) {
            Pattern p = Pattern.compile("(?<=/)[a-zA-Z0-9]*$");
            Matcher m = p.matcher(ctgURL);
            if (m.find()) {
                String ctgID = m.group(0);
                if (!ctgID.isEmpty()) {
                    studyIdentifier.setAttribute("identifierValue", ctgID);
                }
            }
        }*/
        studyIdentifier.setReference("study", study);
        store(studyIdentifier);

        // Study collections
        study.addToCollection("studyIdentifiers", studyIdentifier);
        study.addToCollection("studySources", studySource);
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
}
