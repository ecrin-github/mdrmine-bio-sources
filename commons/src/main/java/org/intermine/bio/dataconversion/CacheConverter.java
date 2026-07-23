package org.intermine.bio.dataconversion;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.model.bio.Study;
import org.intermine.model.bio.StudyIdentifier;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryCollectionReference;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.xml.full.Item;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.time.LocalDate;
import java.util.*;

public abstract class CacheConverter extends BaseConverter {
    private String countriesFP = "";
    private String countriesAltNamesFP = "";

    protected Item existingStudy; // Indicates if currently parsing an existing study (if not null)
    protected boolean newerLastUpdate; // When parsing existing EUCTR study, true if last update date more recent than
                                       // current one
    protected Item currentCountry; // When parsing existing EUCTR study, country associated with country code

    protected String TAXON_ID = "1312";
    protected String RESOLVER_CLASS_NAME = "Study";
    protected IdResolver trialIdResolver = IdResolverService.getClinicalTrialIdResolver(RESOLVER_CLASS_NAME);

    /* Saving all items for later modification and storing at the end */
    // Warning: map variable names need to match collection names in model (TODO: be
    // more precise)

    // Study and IDs
    protected Map<IDsHandler, Item> studies = new HashMap<IDsHandler, Item>(); // Cache of studies, key is IDsHandler
    protected IDsMap idsMap = IDsMap.getIDsMap();

    // Study-related classes
    protected Map<String, Set<Item>> studyConditions = new HashMap<String, Set<Item>>();
    protected Map<String, Item> allConditions = new HashMap<String, Item>();
    protected Map<String, Set<Item>> interventions = new HashMap<String, Set<Item>>();
    protected Map<CompositeKey, Item> allInterventions = new HashMap<CompositeKey, Item>();
    protected Map<String, Set<Item>> studyCountries = new HashMap<String, Set<Item>>();
    protected Map<String, Item> countriesMap = new HashMap<String, Item>(); // Key: country code, name, aliases (all
                                                                            // lowercase), Value: Country Item
    protected Map<String, Set<Item>> countries = new HashMap<String, Set<Item>>(); // TODO
    protected Map<String, Set<Item>> studyFeatures = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> studyIdentifiers = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> studySites = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> publications = new HashMap<String, Set<Item>>();

    // SOs
    protected Map<String, Set<Item>> resultsSummaries = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> protocols = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> statisticalAnalysisPlans = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> informedConsentForms = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> ethicsApprovalNotifications = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> individualParticipantData = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> biosamples = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> datasets = new HashMap<String, Set<Item>>();

    // Common to Study and SOs (key can be study id or SO id)
    protected Map<String, Set<Item>> organisations = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> people = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> relationships = new HashMap<String, Set<Item>>();

    public CacheConverter(ItemWriter writer, Model model, String dataSourceName,
            String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
        this.initObjectStore();
    }

    /**
     * Method called by InterMine
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /*
         * Opened BufferedReader is passed as argument (from
         * FileConverterTask.execute())
         */
        this.startLogging(this.dataSourceName);
        this.loadCountries();

        // Parsing in subclass
        this.parseData(reader);

        this.storeCountries();
        this.storeAllItems();
        this.stopLogging();
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    /**
     * TODO
     */
    protected abstract void parseData(Reader reader) throws Exception;

    /**
     * Set countries CV data path from the corresponding source property in
     * project.xml.
     * Method called by InterMine.
     * 
     * @param countriesFP the path to the countries data file
     */
    public void setCountriesFP(String countriesFP) {
        this.countriesFP = countriesFP;
    }

    /**
     * Set countries alternative names data path from the corresponding source
     * property in project.xml.
     * Method called by InterMine.
     * 
     * @param countriesAltNamesFP the path to the countries alternative names data
     *                            file
     */
    public void setCountriesAltNamesFP(String countriesAltNamesFP) {
        this.countriesAltNamesFP = countriesAltNamesFP;
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
    public void storeClassItem(Item mainClassItem, Item item) throws Exception {
        Map<String, Set<Item>> itemMap = this.getItemMapOfItem(mainClassItem, item);

        if (itemMap != null) {
            this.saveToItemMap(mainClassItem, itemMap, item);
        } else if (this.getClassDescriptor(mainClassItem).getSimpleName().equalsIgnoreCase("study")
                && this.getClassDescriptor(item).getSimpleName().equalsIgnoreCase("country")) {
            // TODO: temporary solution?
            itemMap = (Map<String, Set<Item>>) CacheConverter.class
                    .getDeclaredField("countries").get(this);
            this.saveToItemMap(mainClassItem, itemMap, item);
        } else {
            this.writeLog("Failed to save item to map (couldn't find map or collection from "
                    + this.getClassDescriptor(mainClassItem).getSimpleName() + " item and "
                    + this.getClassDescriptor(item).getSimpleName() + " item");
        }
    }

    @Override
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item item = this.createClassItem(mainClassItem, className, kv);

        if (item != null) {
            this.storeClassItem(mainClassItem, item);
        } else {
            this.writeLog("Failed to create item of class " + className + ", attributes: " + kv);
        }

        return item;
    }

    /**
     * TODO
     */
    public Map<String, Set<Item>> getItemMapOfItem(Item mainClassItem, Item item) throws Exception {
        // Get item map name from reference
        ReferenceDescriptor rd = this.getReferenceDescriptorInItemAOfItemB(mainClassItem, item);

        if (rd != null) {
            String mapName = rd.getName();
            return (Map<String, Set<Item>>) CacheConverter.class.getDeclaredField(mapName).get(this);
        }

        return null;
    }

    /**
     * TODO
     */
    public boolean studyHasItemStored(Item parentItem, Item item) throws Exception {
        boolean hasItemStored = false;

        Map<String, Set<Item>> itemMap = this.getItemMapOfItem(parentItem, item);
        String parentId = parentItem.getIdentifier();
        if (itemMap != null && itemMap.containsKey(parentId) && itemMap.get(parentId).contains(item)) {
            hasItemStored = true;
        }

        return hasItemStored;
    }

    /**
     * TODO
     * 
     * @param field name of field for comparison to find the item
     * @param value value for comparison to find the item, should be unique!
     */
    public <T> Item getItemFromItemMap(Item parentItem, Map<String, Set<Item>> itemMap, String field, T value) {
        Item searchedItem = null;

        String parentId = parentItem.getIdentifier();
        if (itemMap.containsKey(parentId)) {
            Set<Item> items = itemMap.get(parentId);
            for (Item item : items) {
                if (value.equals(ConverterUtils.getAttrValue(item, field))) {
                    searchedItem = item;
                    break;
                }
            }
        }

        return searchedItem;
    }

    /**
     * TODO
     * 
     * @throws Exception
     */
    public void storeAllItems() throws Exception {
        List<Map<String, Set<Item>>> itemMaps = Arrays.asList(
                this.studyConditions, this.studyCountries, this.studyFeatures, this.studyIdentifiers,
                this.publications, this.studySites, this.interventions, this.resultsSummaries,
                this.protocols, this.statisticalAnalysisPlans, this.informedConsentForms,
                this.ethicsApprovalNotifications, this.individualParticipantData, this.biosamples,
                this.organisations, this.people, this.relationships, this.datasets);

        this.writeLog("Storing all items");

        // Used to check for duplicates (right now, useful for Studies and
        // StudyConditions)
        HashSet<String> seenIds;

        for (Map<String, Set<Item>> itemMap : itemMaps) {
            seenIds = new HashSet<String>(); // New set for each map, to avoid constructing a huge set with all item IDs
            for (Set<Item> items : itemMap.values()) {
                for (Item item : items) {
                    if (!seenIds.contains(item.getIdentifier())) {
                        // this.writeLog("Storing item: " + item.toString());
                        store(item);
                        seenIds.add(item.getIdentifier());
                    }
                }
            }
        }

        seenIds = new HashSet<String>(); // For studies

        // TODO: check for duplicates? (as in, with the various studies IDs) and don't
        // store them
        for (Item study : this.studies.values()) {
            if (!seenIds.contains(study.getIdentifier())) {
                store(study);
                seenIds.add(study.getIdentifier());
            }
        }

        this.clearMaps();
    }

    /**
     * TODO
     */
    public void saveToItemMap(Item mainClassItem, Map<String, Set<Item>> itemMap, Item itemToAdd) {
        String mainClassItemId = mainClassItem.getIdentifier();
        Set<Item> items;

        if (!itemMap.containsKey(mainClassItemId)) {
            itemMap.put(mainClassItemId, new HashSet<Item>());
        }

        items = itemMap.get(mainClassItemId);
        items.add(itemToAdd);
    }

    public void clearMaps() {
        this.studies = null;
        this.idsMap = null;

        this.studyConditions = null;
        this.allConditions = null;
        this.interventions = null;
        this.allInterventions = null;
        this.studyCountries = null;
        this.countriesMap = null;
        this.countries = null;
        this.studyFeatures = null;
        this.studyIdentifiers = null;
        this.studySites = null;
        this.publications = null;

        this.resultsSummaries = null;
        this.protocols = null;
        this.statisticalAnalysisPlans = null;
        this.informedConsentForms = null;
        this.ethicsApprovalNotifications = null;
        this.individualParticipantData = null;
        this.biosamples = null;
        this.datasets = null;

        this.organisations = null;
        this.people = null;
        this.relationships = null;
    }

    /**
     * TODO
     * Does not work for studies with no ID
     */
    public void removeStudyAndLinkedItems(IDsHandler idsH) throws Exception {
        // Maps where key is or can be study ID (minus objects and study conditions and
        // interventions (different handling))
        List<Map<String, Set<Item>>> studyMaps = Arrays.asList(
                this.studyCountries, this.studyFeatures, this.studyIdentifiers,
                this.publications, this.studySites, this.organisations,
                this.people, this.relationships);

        // StudyObject maps
        List<Map<String, Set<Item>>> studyObjectMaps = Arrays.asList(
                this.resultsSummaries, this.protocols, this.statisticalAnalysisPlans,
                this.informedConsentForms, this.ethicsApprovalNotifications,
                this.individualParticipantData, this.biosamples);

        // Maps where item have a collection of studies, and therefore must be handled
        // differently
        List<Map<String, Set<Item>>> specialMaps = Arrays.asList(
                this.studyConditions, this.interventions);

        // Maps where key is or can be object ID
        List<Map<String, Set<Item>>> objectMaps = Arrays.asList(
                this.organisations, this.people, this.relationships, this.datasets);

        // Getting and removing study from studies map
        Item study = this.studies.remove(idsH);
        String studyId = study.getIdentifier();

        // Removing study's IDsHandler from idsMap
        for (ID id : idsH.getUids()) {
            if (this.idsMap.remove(id) == null) {
                this.writeLog("Attempted to remove ID from idsMap but failed: " + id);
            }
        }

        // Removing items linked to study
        for (Map<String, Set<Item>> itemMap : studyMaps) {
            itemMap.remove(studyId);
        }

        // Maps where need to remove study in the item's collection as well
        for (Map<String, Set<Item>> itemMap : specialMaps) {
            Set<Item> itemCollectionInStudy = itemMap.getOrDefault(studyId, null);
            if (itemCollectionInStudy != null) {
                for (Item item : itemCollectionInStudy) {
                    this.removeItemFromCollection(item, study);
                }
                itemMap.remove(studyId);
            }
        }

        // Removing objects linked to study to remove, and constructing a set of object
        // ids
        // to remove items linked to these objects
        Set<String> objectIds = new HashSet<String>();
        for (Map<String, Set<Item>> soMap : studyObjectMaps) {
            Set<Item> objects = soMap.get(studyId);
            if (objects != null) {
                for (Item object : objects) {
                    objectIds.add(object.getIdentifier());
                }
                soMap.remove(studyId); // Removing objects linked to study
            }
        }

        // Removing items linked to objects
        for (Map<String, Set<Item>> itemMap : objectMaps) {
            for (String objectId : objectIds) {
                itemMap.remove(objectId);
            }
        }
    }

    /**
     * TODO
     * populates countriesMap
     */
    public void loadCountries() throws Exception {
        if (this.countriesFP.equals("")) {
            throw new Exception("countriesFP property not set in mdrmine project.xml");
        }

        if (!(new File(this.countriesFP).isFile())) {
            throw new Exception("Countries file does not exist (path tested: " + this.countriesFP + " )");
        }

        HashMap<String, List<String>> altNames = this.loadAltCountryNames();

        FileReader in = new FileReader(this.countriesFP);
        BufferedReader br = new BufferedReader(in);

        final CSVParser parser = new CSVParserBuilder()
                .withSeparator('	')
                .build();
        final CSVReader csvReader = new CSVReaderBuilder(br)
                .withCSVParser(parser)
                .build();

        boolean skipNext = false;

        csvReader.readNext(); // Skip headers
        String[] lineValues = csvReader.readNext();

        while (lineValues != null) {
            if (!skipNext) {
                // Creating a Country Item for each line in the file
                // Fields order in file for indices: Country.isoAlpha2, Country.isoAlpha3, null,
                // null, Country.name, Country.capital, null, null, Country.continent,
                // Country.tld, null, null, null, null, null, null, Country.geonameId, null,
                // null
                Item country = this.createClassItem("Country",
                        new String[][] { { "isoAlpha2", lineValues[0] }, { "isoAlpha3", lineValues[1] },
                                { "name", lineValues[4] },
                                { "capital", lineValues[5] }, { "continent", lineValues[8] }, { "tld", lineValues[9] },
                                { "geonameId", lineValues[16] } });

                // Adding entries in map to find Country Item based on various values
                if (!ConverterUtils.isBlankOrNull(lineValues[0])) {
                    this.countriesMap.put(lineValues[0].toLowerCase(), country);
                }
                if (!ConverterUtils.isBlankOrNull(lineValues[1])) {
                    this.countriesMap.put(lineValues[1].toLowerCase(), country);
                }
                if (!ConverterUtils.isBlankOrNull(lineValues[4])) {
                    this.countriesMap.put(lineValues[4].toLowerCase(), country);
                }

                // Adding alternative names from other file
                if (altNames != null) {
                    List<String> aliases = altNames.getOrDefault(lineValues[0].toLowerCase(), null);
                    if (aliases == null) {
                        this.writeLog("Warning: couldn't find any alias for country " + lineValues[4]
                                + " with iso code " + lineValues[0].toLowerCase());
                    } else {
                        for (String alias : aliases) {
                            this.countriesMap.put(alias.toLowerCase(), country);
                        }
                    }
                }
            } else {
                skipNext = false;
            }
            try {
                lineValues = csvReader.readNext();
            } catch (CsvMalformedLineException e) {
                this.writeLog("Found malformed line, skipping it: " + e);
                lineValues = new String[0];
                skipNext = true;
            }
        }

        csvReader.close();
    }

    /**
     * TODO
     */
    public HashMap<String, List<String>> loadAltCountryNames() throws Exception {
        HashMap<String, List<String>> altNames = null;

        if (this.countriesAltNamesFP.equals("")) {
            this.writeLog(
                    "Warning: countriesAltNamesFP property is not set in mdrmine project.xml, countries mapping will be worse");
        } else {
            if (!(new File(this.countriesAltNamesFP).isFile())) {
                this.writeLog("Warning: countries alternative names file does not exist (path tested: "
                        + this.countriesAltNamesFP + " ), countries mapping will be worse");
            } else {
                altNames = new HashMap<String, List<String>>();

                FileReader in = new FileReader(this.countriesAltNamesFP);
                BufferedReader br = new BufferedReader(in);

                final CSVParser parser = new CSVParserBuilder()
                        .withSeparator(',')
                        .build();
                final CSVReader csvReader = new CSVReaderBuilder(br)
                        .withCSVParser(parser)
                        .build();

                boolean skipNext = false;

                csvReader.readNext(); // Skip headers
                String[] lineValues = csvReader.readNext();

                while (lineValues != null) {
                    // TODO: check if line values are not empty? (should not be)
                    if (!skipNext) {
                        String isoCode = lineValues[0].toLowerCase();
                        if (altNames.getOrDefault(isoCode, null) == null) {
                            altNames.put(isoCode, new ArrayList<String>());
                        }
                        altNames.get(isoCode).add(lineValues[1].toLowerCase());
                    } else {
                        skipNext = false;
                    }
                    try {
                        lineValues = csvReader.readNext();
                    } catch (CsvMalformedLineException e) {
                        this.writeLog("Found malformed line, skipping it: " + e);
                        lineValues = new String[0];
                        skipNext = true;
                    }
                }

                csvReader.close();
            }
        }

        return altNames;
    }

    /**
     * TODO
     */
    public void storeCountries() throws Exception {
        // Store countries (no duplicates)
        HashSet<String> seenIds = new HashSet<String>();
        for (Item country : this.countriesMap.values()) {
            if (!seenIds.contains(country.getIdentifier())) {
                store(country);
                seenIds.add(country.getIdentifier());
            }
        }
    }

    /**
     * TODO
     * 
     * 2-letter ISO code
     */
    public Item getCountry(String value) throws Exception {
        Item country = null;

        if (countriesMap.isEmpty()) {
            throw new RuntimeException(
                    "The list of Country items is empty, you likely forgot to call loadCountries() at the start of your parser");
        }

        if (!ConverterUtils.isBlankOrNull(value)) {
            value = value.toLowerCase();
            if (this.countriesMap.containsKey(value)) {
                country = this.countriesMap.get(value);
            } else {
                this.writeLog("Couldn't match country string '" + value + "' to a CV country");
            }
        }
        return country;
    }

    /**
     * TODO
     * Note: unused but keeping it in case
     * 
     * @throws Exception
     */
    public void loadPreviousSourcesStudyIds() throws Exception {
        ClassDescriptor cdStudy = this.getModel().getClassDescriptorByName("Study");
        if (cdStudy == null) {
            throw new RuntimeException("This model does not contain a Study class");
        }

        ClassDescriptor cdStudyId = this.getModel().getClassDescriptorByName("StudyIdentifier");
        if (cdStudyId == null) {
            throw new RuntimeException("This model does not contain a StudyIdentifier class");
        }

        Query q = new Query();
        QueryClass qcStudy = new QueryClass(cdStudy.getType());

        q.addFrom(qcStudy);
        q.addToSelect(qcStudy);
        q.addToOrderBy(qcStudy);

        QueryClass qcStudyIdentifier = new QueryClass(cdStudyId.getType());
        q.addFrom(qcStudyIdentifier);
        q.addToSelect(qcStudyIdentifier);

        QueryCollectionReference ref1 = new QueryCollectionReference(qcStudy, "studyIdentifiers");
        ContainsConstraint cc1 = new ContainsConstraint(ref1, ConstraintOp.CONTAINS, qcStudyIdentifier);

        q.setConstraint(cc1);

        Results res = this.os.execute(q);
        Iterator<Object> resIter = res.iterator();

        Study currentStudy = null;
        ArrayList<StudyIdentifier> studyIds = new ArrayList<StudyIdentifier>();

        while (resIter.hasNext()) {
            ResultsRow rr = (ResultsRow) resIter.next();

            Study study = (Study) rr.get(0);

            if (rr.size() > 1) {
                if (currentStudy == null) { // First study
                    currentStudy = study;
                } else if (study.getId() != currentStudy.getId()) { // Current study is a new one
                    if (!ConverterUtils.isBlankOrNull(currentStudy.getPrimaryIdentifier()) && studyIds.size() > 0) {
                        // Constructing an IDsHandler from the previous study IDs before moving on to
                        // current study
                        // TODO
                        // this.addCachedStudyToIdsMap(studyIds, currentStudy.getPrimaryIdentifier(),
                        // "test");
                        this.writeLog("Constructed idshandler 1");
                    } else {
                        this.writeLog(
                                "Study with no/empty primaryIdentifier or no unique IDs, this log line should never appear: "
                                        + study.toString());
                    }

                    // New study
                    studyIds = new ArrayList<StudyIdentifier>();
                    currentStudy = study;
                }

                // In all cases, add studyId to current studyIds list
                StudyIdentifier studyId = (StudyIdentifier) rr.get(1);
                if (studyId.getUnique()) {
                    this.writeLog("Previous study ID: " + studyId.getValue());
                    studyIds.add(studyId);
                }
            } else {
                this.writeLog("Study with no IDs: " + study.toString());
            }
        }

        // Constructing an IDsHandler with the last study IDs
        if (currentStudy != null && studyIds.size() > 0) {
            // this.addCachedStudyToIdsMap(studyIds, currentStudy.getPrimaryIdentifier(),
            // "test");
            this.writeLog("Constructed idshandler 2");
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @return
     * @throws Exception
     */
    public IDsHandler getIDsHandlerFromStudy(Item study) throws Exception {
        IDsHandler idsH = null;

        if (study != null) {
            String primaryId = ConverterUtils.getAttrValue(study, "primaryIdentifier");
            if (!ConverterUtils.isBlankOrNull(primaryId)) {
                HashSet<String> ids = new HashSet<String>();

                Set<Item> studyIds = this.studyIdentifiers.getOrDefault(study.getIdentifier(), Set.of());
                for (Item studyId : studyIds) {
                    String unique = ConverterUtils.getAttrValue(studyId, "unique");
                    if (unique != null && ConverterUtils.booleanFromString(unique)) {
                        String id = ConverterUtils.getAttrValue(studyId, "value");
                        if (!ConverterUtils.isBlankOrNull(id)) {
                            ids.add(id);
                        } else {
                            this.writeLog("Found a StudyIdentifier with unique true but no ID: " + studyId.toString());
                        }
                    }
                }

                idsH = new IDsHandler(this.dataSourceName, ids);
            } else {
                this.writeLog("Warning: tried to add a study with no primaryIdentifier to idsMap: " + study.toString());
            }
        }

        return idsH;
    }

    /**
     * TODO
     * 
     * @param idsH
     * @return
     */
    public String getStudyPrimaryId(IDsHandler idsH) {
        String primaryId = null;

        for (ID id : idsH.getUids()) {
            Set<String> ids = this.trialIdResolver.resolveId(TAXON_ID, RESOLVER_CLASS_NAME, id.getId());
            if (ids.size() > 0) {
                if (ids.size() > 1) {
                    this.writeLog("Error: found multiple ID resolver entries for id: " + id.getId());
                }
                primaryId = ids.iterator().next();
                break;
            }
        }

        return primaryId;
    }

    /**
     * TODO
     * 
     * @param idsH
     * @return
     */
    public Item getOrCreateStudyWithIDs(IDsHandler idsH) throws Exception {
        // TODO: tests? to test merging
        this.existingStudy = null;

        Item study = null;

        IDsHandler storedHandler = null;

        if (idsH != null & idsH.hasAnyUid()) {
            String primaryId = this.getStudyPrimaryId(idsH);

            /* Attempting to resolve with IdResolver */
            if (!ConverterUtils.isBlankOrNull(primaryId)) {
                idsH.setPrimaryIdentifier(primaryId);
                Set<String> otherUids = this.trialIdResolver.getSynonyms(TAXON_ID, RESOLVER_CLASS_NAME, primaryId);
                // TODO: might be costly
                idsH.addIds(otherUids);
            }

            /* Checking for existing studies */
            Set<IDsHandler> storedHandlers = ConverterUtils.getMatchingIDs(this.idsMap, idsH);

            if (storedHandlers.size() > 0) {
                if (storedHandlers.size() > 1) {
                    this.writeLog("Found multiple matching handlers from this source, picking one of them");

                    for (IDsHandler handler : storedHandlers) {
                        if (storedHandler == null) {
                            storedHandler = handler;
                        } else {
                            // TODO: ideally local merge with previous study
                            this.removeStudyAndLinkedItems(handler);

                            // Adding all other IDs to current IDsHandler
                            if (handler.uids != null) {
                                idsH.addUids(handler.uids);
                                idsH.addNonUids(handler.nonUids);
                            }
                        }
                    }
                } else { // One study
                    this.writeLog("Found one matching handler from same source");
                    storedHandler = storedHandlers.iterator().next();
                }
            }
        }

        /* Creating or using existing study */
        if (storedHandler == null) { // New study
            study = this.createItem("Study");

            // Setting primaryIdentifier
            if (!ConverterUtils.isBlankOrNull(idsH.primaryIdentifier)) { // ID from IdResolver
                study.setAttributeIfNotNull("primaryIdentifier", idsH.primaryIdentifier);
            } else { // Otherwise, pick a UID if there is any
                if (idsH.hasAnyUid()) {
                    study.setAttributeIfNotNull("primaryIdentifier", idsH.getAnyUid().getId());
                }
            }

            // Creating StudyIdentifiers from IDs and adding entries to idsMap
            if (idsH.uids != null) {
                for (ID id : idsH.uids) {
                    this.createAndStoreStudyIdentifier(study, id.getId(), id.getSource(), id.getType(),
                            id.getUnique());
                    this.idsMap.add(id, idsH);
                }
            }

            if (idsH.nonUids != null) {
                for (ID id : idsH.nonUids) {
                    this.createAndStoreStudyIdentifier(study, id.getId(), id.getSource(), id.getType(),
                            id.getUnique());
                }
            }

            // Cache study
            this.studies.put(idsH, study);
        } else { // Existing study
            this.existingStudy = this.studies.get(storedHandler);
            study = this.existingStudy;

            // Attempting to set a UID as primaryIdentifier is there is none
            if (ConverterUtils.isBlankOrNull(ConverterUtils.getAttrValue(study, "primaryIdentifier"))
                    && idsH.hasAnyUid()) {
                study.setAttributeIfNotNull("primaryIdentifier", idsH.getAnyUid().getId());
            }

            // Creating StudyIdentifiers from IDs that do not exist already in the existing
            // study and adding or replacing entries to idsMap
            if (idsH.uids != null) {
                for (ID id : idsH.uids) {
                    if (!storedHandler.hasUid(id)) {
                        this.createAndStoreStudyIdentifier(study, id.getId(), id.getSource(), id.getType(),
                                id.getUnique());
                    }
                    // In any case, entries in the idsMap need to be added or replaced
                    this.idsMap.put(id, storedHandler);
                }
            }

            if (idsH.nonUids != null) {
                for (ID id : idsH.nonUids) {
                    if (!storedHandler.hasNonUid(id)) {
                        this.createAndStoreStudyIdentifier(study, id.getId(), id.getSource(), id.getType(),
                                id.getUnique());
                    }
                }
            }

            // Adding added IDs to storedHandler (merging)
            storedHandler.mergeHandlers(idsH);
        }

        return study;
    }

    /**
     * TODO
     * 
     * @param study
     * @param startDate
     */
    public void setStudyStartDate(Item study, LocalDate startDate) {
        if (startDate != null) {
            boolean setDate = false;
            if (!this.existingStudy()) { // If not parsing an already existing study
                setDate = true;
            } else { // Checking if the parsed start date is later than the already set one (if it
                     // exists)
                String existingDateStr = ConverterUtils.getAttrValue(study, "startDate");
                if (!ConverterUtils.isBlankOrNull(existingDateStr)
                        && startDate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) > 0) {
                    setDate = true;
                }
            }
            if (setDate) {
                study.setAttributeIfNotNull("startDate", startDate.toString());
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param endDate
     */
    public void setStudyEndDate(Item study, LocalDate endDate) {
        if (endDate != null) {
            boolean setDate = false;
            if (!this.existingStudy()) { // If not parsing an already existing study
                setDate = true;
            } else { // Checking if the parsed end date is later than the already set one (if it
                     // exists)
                String existingDateStr = ConverterUtils.getAttrValue(study, "endDate");
                if (!ConverterUtils.isBlankOrNull(existingDateStr)
                        && endDate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) > 0) {
                    setDate = true;
                }
            }
            if (setDate) {
                study.setAttributeIfNotNull("endDate", endDate.toString());
            }
        }
    }

    /**
     * TODO
     */
    public Item linkStudyToStudyCondition(Item study, String term, String meddraCode, String meshCode,
            String meshTreeNumber) throws Exception {
        Item studyCondition = null;

        term = ConverterUtils.normaliseCondition(term);

        if (!ConverterUtils.isBlankOrNull(term)) {
            if (this.allConditions.containsKey(term)) {
                studyCondition = this.allConditions.get(term);
            } else { // Create StudyCondition
                studyCondition = this.createClassItem(study, "StudyCondition",
                        new String[][] { { "term", term }, { "meddraCode", meddraCode },
                                { "meshCode", meshCode }, { "meshTreeNumber", meshTreeNumber } });

                // Add to all StudyConditions map
                this.allConditions.put(term, studyCondition);
            }

            if (studyCondition != null && !this.studyHasItemStored(study, studyCondition)) {
                // Add StudyCondition to collection in Study
                this.handleReferencesAndCollections(study, studyCondition);
                // Storing in cache, even if the StudyCondition already existed, because Studies
                // and its linked items can be removed from item maps later
                this.storeClassItem(study, studyCondition);
            }
        }

        return studyCondition;
    }

    /**
     * TODO
     */
    public Item linkStudyToIntervention(Item study, String type, String name, String meshCode, String meshTreeNumber)
            throws Exception {
        Item intervention = null;

        // Type should already be more or less normalised
        name = ConverterUtils.normaliseIntervention(name);

        if (!ConverterUtils.isBlankOrNull(type) || !ConverterUtils.isBlankOrNull(name)) {
            CompositeKey key = new CompositeKey(type, name);

            if (this.allInterventions.containsKey(key)) {
                intervention = this.allInterventions.get(key);
            } else { // Create Intervention
                intervention = this.createClassItem(study, "Intervention",
                        new String[][] { { "type", type }, { "name", name },
                                { "meshCode", meshCode }, { "meshTreeNumber", meshTreeNumber } });

                // Add to all interventions map
                this.allInterventions.put(key, intervention);
            }

            if (intervention != null && !this.studyHasItemStored(study, intervention)) {
                // Add Intervention to collection in Study
                this.handleReferencesAndCollections(study, intervention);
                // Storing in cache, even if the Intervention already existed, because Studies
                // and its linked items can be removed from item maps later
                this.storeClassItem(study, intervention);
            }
        }

        return intervention;
    }

    /**
     * TODO
     * 
     * Method could be optimised with identifier/name studycountry maps
     */
    public Item getOrCreateStudyCountry(Item study, String countryName) throws Exception {
        Item studyCountry = null;

        if (!ConverterUtils.isBlankOrNull(countryName)) {
            Item country = this.getCountry(countryName);

            if (this.studyCountries.containsKey(study.getIdentifier())) {
                Set<Item> studyCountries = this.studyCountries.get(study.getIdentifier());

                if (country != null) {
                    for (Item sc : studyCountries) {
                        String countryReference = ConverterUtils.getRefId(sc, "country");
                        // If country item was not null we only need to check for country item equality
                        // (not country name)
                        if (countryReference != null && countryReference == country.getIdentifier()) {
                            studyCountry = sc;
                            break;
                        }
                    }
                } else {
                    for (Item sc : studyCountries) {
                        String countryNameToTest = ConverterUtils.getAttrValue(sc, "countryName");
                        if (!ConverterUtils.isBlankOrNull(countryNameToTest)
                                && countryName.equalsIgnoreCase(countryNameToTest)) {
                            studyCountry = sc;
                            break;
                        }
                    }
                }
            }

            // Create StudyCountry
            if (studyCountry == null) {
                studyCountry = createAndStoreStudyCountry(study, country, countryName, null, null, null, null);
            }
        }

        return studyCountry;
    }
}
