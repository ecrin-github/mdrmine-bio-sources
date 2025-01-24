package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024 MDRMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor ;
import org.intermine.xml.full.Item;



/**
 * Class with utility functions for converter classes
 * @author
 */
public abstract class BaseConverter extends BioFileConverter
{
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSS");

    private String logDir = "";
    private Writer logWriter = null;
    protected String trialID = null;

    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
                             String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
    }

    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
                             String dataSetTitle, String licence) {
        super(writer, model, dataSourceName, dataSetTitle, licence);
    }

    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
            String dataSetTitle, String licence, boolean storeOntology) {
        super(writer, model, dataSourceName, dataSetTitle, licence, storeOntology);
    }

    public BaseConverter(ItemWriter writer, Model model) {
        super(writer, model);
    }

    /**
     * TODO
     */
    public Item createAndStoreStudyIdentifier(Item study, String id, String identifierType, String identifierLink) throws Exception {
        Item studyIdentifier = null;

        if (!ConverterUtils.isNullOrEmptyOrBlank(id)) {
            studyIdentifier = createItem("StudyIdentifier");
            studyIdentifier.setAttribute("identifierValue", trialID);
            if (!ConverterUtils.isNullOrEmptyOrBlank(identifierType)) {
                studyIdentifier.setAttribute("identifierType", identifierType);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(identifierLink)) {
                studyIdentifier.setAttribute("identifierLink", identifierLink);
            }
            studyIdentifier.setReference("study", study);

            store(studyIdentifier);
            study.addToCollection("studyIdentifiers", studyIdentifier);
        }

        return studyIdentifier;
    }

    /**
     * Instantiate logger by creating log file and writer.
     * This sets the logWriter instance attribute.
     */
    public void startLogging(String suffix) throws Exception {
        if (!this.logDir.equals("")) {
            String current_timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                
            Path logDir = Paths.get(this.logDir);
            if (!Files.exists(logDir)) Files.createDirectories(logDir);

            Path logFile = Paths.get(logDir.toString(), current_timestamp + "_" + suffix + ".log");
            this.logWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile.toString()), "utf-8"));
        } else {
            throw new Exception("Log folder not specified");
        }
    }

    /**
     * Close opened log writer.
     */
    public void stopLogging() throws IOException {
        if (this.logWriter != null) {
            this.logWriter.close();
        }
    }

    /**
     * Write to WHO log file with timestamp.
     * 
     * @param text the log text
     */
    public void writeLog(String text) {
        try {
            if (this.logWriter != null) {
                if (this.trialID != null) {
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + this.trialID + " - " + text + "\n");
                    this.logWriter.flush();
                } else {
                    // TODO: temp, modify it to still log but without id?
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + text + "\n");
                    this.logWriter.flush();
                    // System.out.println("WHO - Trial ID null (cannot write logs)");
                }
            } else {
                System.out.println("WHO - Log writer is null (cannot write logs)");
            }
        } catch(IOException e) {
            System.out.println("WHO - Couldn't write to log file");
        }
    }

    /**
     * Set logDir from the corresponding source property in project.xml.
     * Method called by InterMine.
     * 
     * @param logDir the path to the directory where the log file will be created
     */
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    /**
     * Create and store item (instance) of a class. Works for all classes except the Study class.
     * 
     * @param mainClassItem the already created item of the main class to reference (Study)
     * @param className the name of the class to create an item of
     * @param kv array of field name - field value pairs to set class item attribute values
     * @return the created item
     */
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item classItem = createItem(className);

        // Get class descriptor to get reference field
        ClassDescriptor cd = this.getModel().getClassDescriptorByName(className);
        ReferenceDescriptor[] rdArr = cd.getReferenceDescriptors().toArray(ReferenceDescriptor[]::new);

        // Get reference field of class (either "study" or "linkedStudy" for data objects)
        String referencedClass = rdArr[0].getName();
        // Get reverse reference field of class (=collection field, e.g. "studyFeatures")
        String reverseReferencedClass = rdArr[0].getReverseReferenceFieldName();

        // Set class values from fieldName - value pairs passed as argument
        for (int i = 0; i < kv.length; i++) {
            if (kv[i].length != 2) {
                throw new Exception("Key value tuple is not of length == 2");
            }
            classItem.setAttributeIfNotNull(kv[i][0], kv[i][1]);
        }

        classItem.setReference(referencedClass, mainClassItem);
        store(classItem);
        mainClassItem.addToCollection(reverseReferencedClass, classItem);

        return classItem;
    }

    /**
     * TODO
     */
    public Item createAndStoreObjectDate(Item dataObject, String dateStr, DateTimeFormatter dateFormatter, String dateType) throws Exception {
        Item objectDate = null;

        LocalDate date = ConverterUtils.getDateFromString(dateStr, dateFormatter);
        if (date != null) {
            objectDate = this.createAndStoreClassItem(dataObject, "ObjectDate", 
                new String[][]{{"dateType", dateType}, {"dateAsString", date.toString()}, 
                                {"startDay", String.valueOf(date.getDayOfMonth())}, 
                                {"startMonth", String.valueOf(date.getMonthValue())}, 
                                {"startYear", String.valueOf(date.getYear())}});
        }

        return objectDate;
    }

    /**
     * Get field value from array of values using a field's position-lookup Map, value is also cleaned.
     * 
     * @param lineValues the list of all values for a line in the data file
     * @param field the name of the field to get the value of
     * @return the cleaned value of the field
     * @see #cleanValue()
     */
    public abstract String getAndCleanValue(String[] lineValues, String field);

    /**
     * Clean a value according to the subclass' logic. Note: method should be static but Java does not allow abstract + static
     * 
     * @param s the value to clean
     * @param strip boolean indicating whether to strip the string of any leading/trailing whitespace
     * @return the cleaned value
     * @see #unescapeHtml()
     * @see #removeQuotes()
     */
    public abstract String cleanValue(String s, boolean strip);
}
