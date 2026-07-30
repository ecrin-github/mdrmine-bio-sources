package org.intermine.bio.dataconversion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IDsMap {
    private static IDsMap instance = null;
    // Key: any unique study ID
    private Map<String, IDsHandler> idsMap = new HashMap<String, IDsHandler>();

    /**
     * Singleton class
     */
    private IDsMap() {
        IDsMap.instance = this;
    }

    /**
     * TODO
     * 
     * @return
     */
    public static IDsMap getIDsMap() {
        if (IDsMap.instance == null) {
            IDsMap.instance = new IDsMap();
        }

        return IDsMap.instance;
    }

    /**
     * TODO
     * 
     * @param id
     * @return
     */
    public IDsHandler get(ID id) {
        if (id != null) {
            return this.get(id.getId());
        }
        return null;
    }

    /**
     * TODO
     * 
     * @param id
     * @return
     */
    public IDsHandler get(String id) {
        return this.idsMap.getOrDefault(id, null);
    }

    /**
     * TODO
     * 
     * @return
     */
    public Set<IDsHandler> getAllIDsHandlers() {
        return new HashSet<IDsHandler>(this.idsMap.values());
    }

    /**
     * TODO
     * 
     * @param id
     * @param idsH
     * @return
     * @throws Exception
     */
    public boolean add(ID id, IDsHandler idsH) throws Exception {
        boolean added = false;

        if (id != null && idsH != null) {
            String idStr = id.getId();
            if (!ConverterUtils.isBlankOrNull(idStr)) {
                if (this.idsMap.containsKey(idStr)) {
                    throw new Exception("Tried to add an ID in idsMap but there is already an entry");
                } else {
                    this.idsMap.put(idStr, idsH);
                    added = true;
                }
            }
        }

        return added;
    }

    /**
     * TODO
     * 
     * @param id
     * @param idsH
     * @return
     * @throws Exception
     */
    public boolean put(ID id, IDsHandler idsH) throws Exception {
        boolean put = false;

        if (id != null && idsH != null) {
            String idStr = id.getId();
            if (!ConverterUtils.isBlankOrNull(idStr)) {
                this.idsMap.put(idStr, idsH);
                put = true;
            }
        }

        return put;
    }

    /**
     * TODO
     * 
     * @param id
     * @return
     */
    public IDsHandler remove(String id) {
        return this.idsMap.remove(id);
    }

    /**
     * TODO
     * 
     * @param id
     * @return
     */
    public IDsHandler remove(ID id) {
        return this.idsMap.remove(id.getId());
    }

    /**
     * TODO
     * 
     * @param id
     * @return
     */
    public boolean containsId(ID id) {
        if (id != null) {
            return this.containsId(id.getId());
        }
        return false;
    }

    /**
     * TODO
     * 
     * @param id
     * @return
     */
    public boolean containsId(String id) {
        return this.idsMap.containsKey(id);
    }

    /**
     * TODO
     * 
     * @return
     */
    public int size() {
        return this.idsMap.size();
    }
}
