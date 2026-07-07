package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;

public class IdFileBuilder {
    private Logger logger = null;

    public IdFileBuilder(String logDir) throws Exception {
        this.logger = new Logger(logDir, "id_file_builder");
    }

    public void parseWho(Path dataFP, Path headersFP) throws Exception {
        this.logger.writeLog("Parsing WHO, using this file: " + dataFP);
        Map<String, Integer> fieldsToInd = this.getWhoHeaders(headersFP);

        BufferedReader reader = Files.newBufferedReader(dataFP);

        final CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .build();

        boolean skipNext = false;

        // nextLine[] is an array of values from the line
        String[] lineValues = csvReader.readNext();

        while (lineValues != null) {
            if (!skipNext) {
                /* Trial ID */
                String trialID = this.getAndCleanValueWho(lineValues, fieldsToInd, "TrialID");
                /* Secondary IDs */
                String secondaryIDs = this.getAndCleanValueWho(lineValues, fieldsToInd, "SecondaryIDs");

                // In MDR model but unused; Unclear what the various values mean - certain
                // bridging flag/childs
                // values with parent or child bridged type seem to refer to the same study and
                // not additional/children studies
                // Treating bridgingFlag and childs as other IDs of this study
                String bridgingFlag = this.getAndCleanValueWho(lineValues, fieldsToInd, "Bridging_flag");
                // TODO: unused?
                String bridgedType = this.getAndCleanValueWho(lineValues, fieldsToInd, "Bridged_type");
                String childs = this.getAndCleanValueWho(lineValues, fieldsToInd, "Childs");

                System.out.println("hello");
                // IDsHandler idsH = this.xxx(trialID, secondaryIDs, bridgingFlag, childs);
            } else {
                skipNext = false;
            }
            try {
                lineValues = csvReader.readNext();
            } catch (CsvMalformedLineException e) {
                this.logger.writeLog("Found malformed line, skipping it: " + e);
                lineValues = new String[0];
                skipNext = true;
            }
        }

        csvReader.close();
    }

    /**
     * Get a dictionary (map) of the WHO data file field names linked to their
     * corresponding column index in the data file, using a separate headers file.
     * The headers file path is defined in the project.xml file (and set as an
     * instance attribute of this class).
     * 
     * @return map of data file field names and their corresponding column index
     */
    public Map<String, Integer> getWhoHeaders(Path headersFilePath) throws Exception {
        if (headersFilePath == null || headersFilePath.toString().isEmpty()) {
            throw new Exception("headersFilePath property not set in mdrmine project.xml");
        }

        if (!headersFilePath.toFile().isFile()) {
            throw new Exception("WHO Headers file does not exist (path tested: " + headersFilePath + " )");
        }

        List<String> fileContent = Files.readAllLines(headersFilePath, StandardCharsets.UTF_8);
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();

        if (fileContent.size() > 0) {
            String headersLine = String.join("", fileContent).strip();
            // Deleting the invisible \FEFF unicode character at the beginning of the header
            // file
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

    public String getAndCleanValueWho(String[] lineValues, Map<String, Integer> fieldsToInd, String field) {
        return ConverterUtils.unescapeHtml(ConverterUtils.removeQuotes(lineValues[fieldsToInd.get(field)])).strip();
    }

}
