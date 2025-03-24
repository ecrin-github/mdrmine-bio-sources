package org.intermine.bio.dataconversion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.bio.Country;
import org.intermine.xml.full.Item;



public abstract class CacheConverter extends BaseConverter {

    protected Item existingStudy;    // Indicates if currently parsing an existing study (if not null)
    protected boolean newerLastUpdate; // When parsing existing EUCTR study, true if last update date more recent than current one
    protected Country currentCountry; // When parsing existing EUCTR study, country associated with country code

    /* Saving all items for later modification and storing at the end  */
    // Cache of studies, key is primary identifier (not Item or DB id)
    protected Map<String, Item> studies = new HashMap<String, Item>();
    // Study-related classes
    protected Map<String, List<Item>> studyConditions = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyCountries = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyFeatures = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyICDs = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyIdentifiers = new HashMap<String, List<Item>>();
    // DOs
    protected Map<String, List<Item>> objects = new HashMap<String, List<Item>>();
    // DO-related classes
    protected Map<String, List<Item>> objectDates = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> objectDescriptions = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> objectIdentifiers = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> objectInstances = new HashMap<String, List<Item>>();
    // Common to Study and DOs (key can be study id or DO id)
    // TODO: all items lists? 
    protected Map<String, List<Item>> locations = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> organisations = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> people = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> relationships = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> titles = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> topics = new HashMap<String, List<Item>>();

    public CacheConverter(ItemWriter writer, Model model, String dataSourceName,
                             String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
        this.initObjectStore();
    }
    
    public CacheConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence) {
        super(writer, model, dataSourceName, dataSetTitle, licence);
        this.initObjectStore();
    }
    
    public CacheConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence, boolean storeOntology) {
        super(writer, model, dataSourceName, dataSetTitle, licence, storeOntology);
        this.initObjectStore();
    }
    
    public CacheConverter(ItemWriter writer, Model model) {
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
    public void setStudyStartDate(Item study, LocalDate startDate) {
        if (startDate != null) {
            boolean setDate = false;
            if (!this.existingStudy()) {  // If not parsing an already existing study
                setDate = true;
            } else {    // Checking if the parsed start date is later than the already set one (if it exists)
                String existingDateStr = ConverterUtils.getValueOfItemAttribute(study, "startDate");
                if (!ConverterUtils.isNullOrEmptyOrBlank(existingDateStr)
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
     * @param field name of field for comparison to find the item
     * @param value value for comparison to find the item, should be unique!
     */
    public Item getItemFromItemMap(Item parentItem, Map<String, List<Item>> itemMap, String field, String value) {
        Item searchedItem = null;

        String parentId = parentItem.getIdentifier();
        if (itemMap.containsKey(parentId)) {
            List<Item> items = itemMap.get(parentId);
            for (Item item: items) {
                if (value.equals(ConverterUtils.getValueOfItemAttribute(item, field))) {
                    searchedItem = item;
                    break;
                }
            }
        }

        return searchedItem;
    }

    /**
     * TODO
     * @throws Exception
     */
    public void storeAllItems() throws Exception {
        List<Map<String, List<Item>>> itemMaps = Arrays.asList(
            this.studyConditions, this.studyCountries, this.studyFeatures, this.studyICDs, this.studyIdentifiers, this.locations, 
            this.organisations, this.people, this.relationships, this.titles, this.topics, this.objects, this.relationships,
            this.objectDates, this.objectDescriptions, this.objectIdentifiers, this.objectInstances
        );

        for (Map<String, List<Item>> itemMap: itemMaps) {
            for (List<Item> items: itemMap.values()) {
                for (Item item: items) {
                    store(item);
                }
            }
        }

        for (Item study: this.studies.values()) {
            store(study);
        }
    }

    /**
     * TODO
     */
    public void saveToItemMap(Item mainClassItem, Map<String, List<Item>> itemMap, Item itemToAdd) {
        String mainClassItemId = mainClassItem.getIdentifier();
        List<Item> itemList;

        if (!itemMap.containsKey(mainClassItemId)) {
            itemMap.put(mainClassItemId, new ArrayList<Item>());
        }

        itemList = itemMap.get(mainClassItemId);
        itemList.add(itemToAdd);
    }

    public abstract Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception;

}
