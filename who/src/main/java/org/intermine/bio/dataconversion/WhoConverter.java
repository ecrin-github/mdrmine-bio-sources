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



/**
 * 
 * @author
 */
public class WhoConverter extends BioFileConverter
{
    //
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

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        
        String line;
        String[] values;
        Item study;

        System.out.println("WhoConverter - in process");

        Map<String, Integer> fieldsToInd = this.getHeaders(HEADERS_FILE_PATH);

        if (fieldsToInd != null) {
            System.out.println("WhoConverter - in process, fields to Ind not null");
            BufferedReader br = new BufferedReader(reader);
            while ((line = br.readLine()) != null) {
                System.out.println("WhoConverter - in process, after readline");
                values = line.split(",");
                System.out.println("WhoConverter - in process, number of values in line: " + values.length);

                study = createItem("Study");
                study.setAttribute("displayTitle", values[fieldsToInd.get("public_title")]);
                System.out.println("WhoConverter - in process, before studyidentifier");

                Item studyIdentifier = createItem("StudyIdentifier");
                System.out.println("WhoConverter - in process, after studyidentifier createItem");
                System.out.println("WhoConverter - in process, fieldsToInd: " + fieldsToInd);
                studyIdentifier.setAttribute("identifierValue", values[fieldsToInd.get("TrialID")]);
                System.out.println("WhoConverter - in process, after studyidentifier 1st create attribute");
                studyIdentifier.setAttribute("source", values[fieldsToInd.get("source_example_value")]);
                System.out.println("WhoConverter - in process, after studyidentifier 2nd create attribute");
                studyIdentifier.setReference("study5", study);
                System.out.println("WhoConverter - in process, after studyidentifier 3rd create attribute");
                store(studyIdentifier);
                System.out.println("WhoConverter - in process, before studytitle");

                Item studyTitle = createItem("StudyTitle");
                studyTitle.setAttribute("titleText", values[fieldsToInd.get("Scientific_title")]);
                studyTitle.setReference("study11", study);
                store(studyTitle);
                System.out.println("WhoConverter - in process, before study collections");

                // Study collections
                study.addToCollection("studyIdentifiers", studyIdentifier);
                study.addToCollection("studyTitles", studyTitle);
                store(study);
            }
        } else {
            throw new Exception("Failed to parse header file");
        }

        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    public Map<String, Integer> getHeaders(String headersFilePath) throws Exception {
        // TODO: to some config file somewhere?
        System.out.println("WhoConverter - in getHeaders");
        List<String> fileContent = Files.readAllLines(Paths.get(headersFilePath), StandardCharsets.UTF_8);
        System.out.println("WhoConverter - in getHeaders after read all lines");
        Map<String, Integer> fieldsToInd = null;

        if (fileContent.size() > 0) {
            System.out.println("WhoConverter - in getHeaders fileContent > 0");
            String headersLine = String.join("", fileContent).strip();
            System.out.println("WhoConverter - string join: " + String.join("", fileContent));
            System.out.println("WhoConverter - headersLine: " + headersLine);
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
