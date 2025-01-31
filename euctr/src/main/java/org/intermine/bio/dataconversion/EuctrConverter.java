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

import java.io.FileReader;
import java.io.Reader;
import java.util.List;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;



/**
 * 
 * @author
 */
public class EuctrConverter extends BaseConverter
{
    //
    private static final String DATASET_TITLE = "EUCTR_allfile";
    private static final String DATA_SOURCE_NAME = "EUCTR";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public EuctrConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        this.startLogging("euctr");

        XMLInputFactory xi = XMLInputFactory.newInstance();
        XMLStreamReader xr = xi.createXMLStreamReader(reader);
        XmlMapper xm = new XmlMapper();
        int eventType;

        // TODO: jackson XML library + woodstox

        while (xr.hasNext()) {
            eventType = xr.next();
            switch (eventType) {
                case XMLEvent.START_ELEMENT:
                    this.writeLog("process(): start_element name: " + xr.getLocalName());
                    if (xr.getLocalName().toLowerCase().equals("trial")) {
                        EuctrTrial trial = xm.readValue(xr, EuctrTrial.class);
                        this.parseAndStoreTrial(trial);
                    }
                    break;
                case XMLEvent.CHARACTERS:
                    this.writeLog("process(): element value (CHARACTERS):" + xr.getText());
                    break;
                case XMLEvent.ATTRIBUTE:
                    // TODO
                    this.writeLog("process(): found attribute");
                    break;
                case XMLEvent.START_DOCUMENT:
                    this.writeLog("File encoding: " + xr.getEncoding());
                    break;
                default:
                    break;
            }
        }

        xr.close();

        this.stopLogging();
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    /**
     * TODO
     */
    public void parseAndStoreTrial(EuctrTrial trial) throws Exception {
        Item study = createItem("Study");
        EuctrMainInfo mainInfo = trial.getMainInfo();
        String mainId = mainInfo.getTrialId();
        this.createAndStoreStudyIdentifier(study, mainId, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, null);

        String publicTitle = mainInfo.getPublicTitle();
        study.setAttributeIfNotNull("displayTitle", "EUCTR" + publicTitle);
        
        List<EuctrContact> contacts = trial.getContacts();
        if (contacts.size() > 0) {
            this.createAndStoreClassItem(study, "StudyPeople", 
                new String[][]{{"personFullName", contacts.get(0).getFirstname()}});
        }

        List<String> countries = trial.getCountries();
        if (countries.size() > 0) {
            this.createAndStoreClassItem(study, "StudyCountry", 
                new String[][]{{"countryName", countries.get(0)}});
        }

        EuctrCriteria criteria = trial.getCriteria();
        String ic = criteria.getInclusionCriteria();
        study.setAttributeIfNotNull("iec", ic);

        store(study);
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