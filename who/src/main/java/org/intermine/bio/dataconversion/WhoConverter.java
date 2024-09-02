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

import java.io.Reader;
import java.nio.file.Files;

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
        /* Open BufferedReader is passed as argument (from FileConverterTask.execute()) */
        
        String line;
        String[] values;
        Item study;

        Map<String, Integer> fieldsToInd = this.getHeaders(HEADERS_FILE_PATH);

        if (fieldsToInd != null) {
            while ((line = br.readLine()) != null) {
                study = createItem("Study");
                values = line.split(",");

                sb.append(line);
                store(study);
            }
        } else {
            throw new Exception("");
        }

        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    public Map<String, Integer> getHeaders(String headersFilePath) throws Exception {
        // TODO: to some config file somewhere?
        String[] fileContent = Files.readAllLines(headersFilePath);
        Map<String, Integer> fieldsToInd = null;

        if (content.length > 0) {
            String headersLine = String.join("", fileContent).strip();
            String[] fields = headersLine.split(",");
            fieldsToInd = new HashMap<String, Integer>();
            
            for (int ind = 0; i < fields.length; ind++) {
                fieldsToInd.put(fields[ind], ind);
            }
        } else {
            throw new Exception("WHO Headers file is empty");
        }

        return fieldsToInd;
    }
}
