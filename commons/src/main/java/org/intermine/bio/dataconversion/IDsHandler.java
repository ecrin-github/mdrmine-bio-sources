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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.text.WordUtils;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.CollectionDescriptor;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.model.bio.Country;
import org.intermine.model.bio.Study;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.util.PropertiesUtil;
import org.intermine.xml.full.Item;



/**
 * TODO
 * @author
 */
public class IDsHandler
{
    private Logger logger = null;
    public static int handlersNb = 0;
    private int id;
    public String ctisID = "";
    public String nctID = "";
    public String euctrID = "";

    public IDsHandler(Logger logger) {
        this.logger = logger;

        IDsHandler.handlersNb++;
        this.id = IDsHandler.handlersNb;
    }
    
    // No need to pass logger here since 
    public IDsHandler(Logger logger, String ctisID, String nctID, String euctrID) {
        this.logger = logger;

        IDsHandler.handlersNb++;
        this.id = IDsHandler.handlersNb;

        if (ctisID != null) {
            this.ctisID = ctisID;
        }
        if (nctID != null) {
            this.nctID = nctID;
        }
        if (euctrID != null) {
            this.euctrID = euctrID;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj.getClass() != this.getClass()) {
            return false;
        }

        final IDsHandler other = (IDsHandler) obj;
        if (this.id != other.id) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = getClass().hashCode();
        hash = 31 * hash + this.id;
        return hash;
    }

    public String[] getIDsList() {
        return new String[]{this.ctisID, this.euctrID, this.nctID};
    }

    /**
     * TODO
     * not guaranteed to update any IDs, updating only if relevant
     */
    public void updateIDs(String ctisID, String nctID, String euctrID) {
        /* Handling various cases regarding CTIS and EUCTR IDs, as they can be equal (both in previous/existing study IDs and parsed IDs) */
        if (!ConverterUtils.isNullOrEmptyOrBlank(ctisID) && !ConverterUtils.isNullOrEmptyOrBlank(euctrID)) {
            if (ctisID.equalsIgnoreCase(euctrID)) { // previous/existing study CTIS and EUCTR ID are the same
                if (this.ctisID.isEmpty() && this.euctrID.isEmpty()) {    // Parsed IDs are both empty, setting both of them
                    this.ctisID = ctisID;
                    this.euctrID = euctrID;
                } else {    // At least one of the parsed IDs is not empty
                    if (this.ctisID.equalsIgnoreCase(this.euctrID)) {    // Parsed CTIS and EUCTR ID are the same
                        if (!this.ctisID.equalsIgnoreCase(ctisID)) { // Checking if all 4 IDs are not identical (else nothing to do)
                            // Assuming that the more recent ID (year + sequential part after) is the CTIS ID, and the other is the EUCTR ID
                            this.logger.writeLog("Parsed CTIS and EUCTR IDs are identical (" + this.ctisID + 
                                ") but different to previous/existing study CTIS and EUCTR IDs (" + ctisID + ", identical too), setting more recent ID as CTIS");
                            if (ctisID.compareTo(this.ctisID) > 0) {
                                this.ctisID = ctisID;
                            } else {
                                this.euctrID = euctrID;
                            }
                        }
                    } else {    // Previous IDs are identical but parsed IDs are different
                        if (!this.ctisID.isEmpty() && !this.euctrID.isEmpty()) {  // Both parsed IDs are not empty (and different)
                            if (!ctisID.equalsIgnoreCase(this.ctisID) && !ctisID.equalsIgnoreCase(this.euctrID)) {
                                // Logging if parsed IDs are not empty and different, and don't match with the previous IDs, this probably shouldn't happen
                                // e.g. previous: CTIS2016 EUCTR2016; parsed: CTIS2014 EUCTR2012
                                this.logger.writeLog("Warning: both parsed CTIS (" + this.ctisID + ") and EUCTR (" + this.euctrID + ") IDs are different" +
                                "from previous/existing study IDs: " + ctisID + " (CTIS and EUCTR IDs are identical)");
                            }   // Else nothing to do, e.g. previous: CTIS2016 EUCTR2016; parsed: CTIS2016 EUCTR2014
                        
                        } else if (this.ctisID.isEmpty()) {  // CTIS ID only is empty
                            // Parsed CTIS ID is empty, previous/existing study CTIS ID is set and different from parsed EUCTR ID
                            // If it's the same, we know parsed EUCTR ID is specifically only an EUCTR ID and should not be set to CTIS ID
                            if (!ctisID.equalsIgnoreCase(this.euctrID)) {    // e.g. previous: CTIS2016 EUCTR2016; parsed: CTIS() EUCTR2014
                                this.ctisID = ctisID;
                            }   // Else not setting CTIS ID, e.g. previous: CTIS2016 EUCTR2016; parsed: CTIS() EUCTR2016
                        
                        } else {    // EUCTR ID only is empty
                            // Same case as above but for EUCTR ID
                            if (!euctrID.equalsIgnoreCase(this.ctisID)) {    // e.g. previous: CTIS2014 EUCTR2014; parsed: CTIS2016 EUCTR()
                                this.euctrID = euctrID;
                            }   // Else not setting EUCTR ID, e.g. previous: CTIS2016 EUCTR2016; parsed: CTIS2016 EUCTR()
                        }
                    }
                }
            } else {    // Previous/existing study CTIS and EUCTR ID are different, setting both of them
                if (!this.ctisID.isEmpty() && !ctisID.equals(this.ctisID)) {  // Logging if we are overwriting an existing and different ID
                    this.logger.writeLog("ctisID about to be set (" + ctisID + ") is different than parsed ID it is replacing (" + this.ctisID + ")");
                }
                if (!this.euctrID.isEmpty() && !euctrID.equals(this.euctrID)) {
                    this.logger.writeLog("euctrID about to be set (" + euctrID + ") is different than parsed ID it is replacing (" + this.euctrID + ")");
                }
                this.ctisID = ctisID;
                this.euctrID = euctrID;
            }
        } else {    // One of the previous IDs is empty
            // TODO: should really overwrite IDs if different or not? probably yes
            if (!ConverterUtils.isNullOrEmptyOrBlank(ctisID)) { // Previous/existing study EUCTR ID is empty
                // Case where all IDs are identical but one of the previous/existing study IDs is empty, meaning it's carrying the info that the ID set
                // is specifically only for this field (in this case CTIS), so we set the parsed ID to match the empty previous/existing study ID
                // e.g. previous: CTIS2016 EUCTR(); parsed: CTIS2016 EUCTR2016
                if (ctisID.equalsIgnoreCase(this.ctisID) && this.ctisID.equalsIgnoreCase(this.euctrID)) {
                    this.euctrID = "";
                } else {
                    // TODO: should log case where previous/existing study EUCTR ID and parsed CTIS ID are empty, and the other 2 IDs are identical
                    if (!this.ctisID.isEmpty() && !ctisID.equals(this.ctisID)) {  // Logging if we are overwriting an existing and different ID
                        this.logger.writeLog("ctisID about to be set (" + ctisID + ") is different than parsed ID it is replacing (" + this.ctisID + ")");
                    }
                    this.ctisID = ctisID;
                }
            } else if (!ConverterUtils.isNullOrEmptyOrBlank(euctrID)) { // previous/existing study CTIS ID is empty
                // Same case as above but with empty previous/existing study CTIS ID instead
                if (euctrID.equalsIgnoreCase(this.euctrID) && this.euctrID.equalsIgnoreCase(this.ctisID)) {
                    this.ctisID = "";
                } else {
                    if (!this.euctrID.isEmpty() && !euctrID.equals(this.euctrID)) {
                        this.logger.writeLog("euctrID about to be set (" + euctrID + ") is different than parsed ID it is replacing (" + this.euctrID + ")");
                    }
                    this.euctrID = euctrID;
                }
            }
        }

        // Handling NCT ID
        if (!ConverterUtils.isNullOrEmptyOrBlank(nctID)) {
            if (!this.nctID.isEmpty() && !nctID.equals(this.nctID)) {
                this.logger.writeLog("nctID about to be set (" + nctID + ") is different than parsed ID it is replacing (" + this.nctID + ") (should not happen?)");
            }
            this.nctID = nctID;
        }
    }
}
