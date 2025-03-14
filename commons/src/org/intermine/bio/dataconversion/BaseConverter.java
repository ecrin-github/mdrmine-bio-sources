package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024-2025 MDRMine
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
import java.util.Properties;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor ;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.util.PropertiesUtil;
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
    protected String currentTrialID = null;
    protected Item existingStudy = null;    // Indicates if currently parsing an existing study (if not null)
    protected ObjectStore os = null;

    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
                             String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence) {
        super(writer, model, dataSourceName, dataSetTitle, licence);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence, boolean storeOntology) {
        super(writer, model, dataSourceName, dataSetTitle, licence, storeOntology);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model) {
        super(writer, model);
        this.initObjectStore();
    }

    /**
     * 
     */
    public boolean existingStudy() {
        return this.existingStudy != null;
    }

    /**
     * TODO
     */
    public void initObjectStore() {
        try {
            String alias = "osw.production";
            Properties intermineProps = PropertiesUtil.getProperties();
            Properties noPrefixProps = PropertiesUtil.stripStart(alias, intermineProps);
            String osAlias = noPrefixProps.getProperty("os");
    
            this.os = ObjectStoreFactory.getObjectStore(osAlias);
        } catch(Exception e) {
            // TODO
            ;
        }
    }

    /**
     * TODO
     */
    public Item createAndStoreStudyIdentifier(Item study, String id, String identifierType, String identifierLink) throws Exception {
        Item studyIdentifier = null;

        if (!ConverterUtils.isNullOrEmptyOrBlank(id)) {
            this.createAndStoreClassItem(study, "StudyIdentifier", 
                new String[][]{{"identifierValue", id}, {"identifierType", identifierType},
                                {"identifierLink", identifierLink}});
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
                if (this.currentTrialID != null) {
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + this.currentTrialID + " - " + text + "\n");
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
        Item item = this.createClassItem(mainClassItem, className, kv);
        if (item != null) {
            store(item);
        }
        return item;
    }

    /**
     * Create item (instance) of a class. Works for all classes except the Study class.
     * 
     * @param mainClassItem the already created item of the main class to reference (Study)
     * @param className the name of the class to create an item of
     * @param kv array of field name - field value pairs to set class item attribute values
     * @return the created item
     */
    public Item createClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item classItem = createItem(className);

        // Get reference field of class (either "study" or "linkedStudy" for data objects)
        ReferenceDescriptor reference = this.getReferenceDescriptorOfClass(className);
        String referenceName = reference.getName();

        if (reference == null) {
            this.writeLog("createAndStoreClassItem(): couldn't find a known referenceName (study or dataobject) in class " + className);
        } else {
            // Get reverse reference field of class (=collection field, e.g. "studyFeatures")
            String reverseReferenceName = reference.getReverseReferenceFieldName();
    
            // Set class values from fieldName - value pairs passed as argument
            for (int j = 0; j < kv.length; j++) {
                if (kv[j].length != 2) {
                    throw new Exception("Key value tuple is not of length == 2");
                }
                classItem.setAttributeIfNotNull(kv[j][0], kv[j][1]);
            }
    
            classItem.setReference(referenceName, mainClassItem);
            mainClassItem.addToCollection(reverseReferenceName, classItem);
        }

        return classItem;
    }

    /**
     * TODO
     * only for classes that have 1 ref
     */
    public ReferenceDescriptor getReferenceDescriptorOfClass(String className) {
        ReferenceDescriptor rd = null;

        // Get class descriptor to get reference field
        ClassDescriptor cd = this.getModel().getClassDescriptorByName(className);
        ReferenceDescriptor[] rdArr = cd.getReferenceDescriptors().toArray(ReferenceDescriptor[]::new);

        // Find index of reference to non-CV class
        String refName = null;
        int i = 0;
        boolean found = false;
        while (!found && i < rdArr.length) {
            refName = rdArr[i].getName();

            if (refName.equalsIgnoreCase("study") || refName.equalsIgnoreCase("dataObject") || refName.equalsIgnoreCase("linkedStudy")) {
                found = true;
                rd = rdArr[i];
            }

            i++;
        }

        return rd;
    }

    /**
     * TODO
     */
    public String getReverseReferenceNameOfClass(String className) {
        String revRef = null;

        ReferenceDescriptor rd = this.getReferenceDescriptorOfClass(className);
        if (rd != null) {
            revRef = rd.getReverseReferenceFieldName();
        }

        return revRef;
    }

    /**
     * TODO
     */
    public Item createAndStoreObjectDate(Item dataObject, String dateStr, DateTimeFormatter dateFormatter, String dateType) throws Exception {
        Item objectDate = null;

        LocalDate date = ConverterUtils.getDateFromString(dateStr, dateFormatter);
        if (date != null) {
            objectDate = this.createAndStoreClassItem(dataObject, "ObjectDate", 
                new String[][]{{"dateType", dateType}, {"startDate", date.toString()}});
        }

        return objectDate;
    }

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
