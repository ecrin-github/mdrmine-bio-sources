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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.intermine.model.bio.StudyIdentifier;

/**
 * TODO
 * 
 * @author
 */
public class IDsHandler {
    public static int handlersNb = 0;
    private int id;
    public String dataSource = null;
    public String primaryIdentifier = null;
    public Set<ID> uids = new HashSet<ID>();
    public Set<ID> nonUids = new HashSet<ID>();

    public IDsHandler(String dataSource) {
        this.dataSource = dataSource;
        this.setInternalId();
    }

    public IDsHandler(String dataSource, String id) {
        this(dataSource, ConverterUtils.createID(id));
    }

    /**
     * TODO
     * Sets primaryIdentifier if id is unique
     * 
     * @param dataSource
     * @param id
     */
    public IDsHandler(String dataSource, ID id) {
        this(dataSource, id, false);
    }

    /**
     * TODO
     * To be used by ID File Task
     * 
     * @param dataSource
     * @param id
     * @param setIdAsPrimary
     */
    public IDsHandler(String dataSource, String id, boolean setIdAsPrimary) {
        this(dataSource, ConverterUtils.createID(id), setIdAsPrimary);
    }

    /**
     * TODO
     * To be used by ID File Task
     * 
     * @param dataSource
     * @param id
     * @param setIdAsPrimary
     */
    public IDsHandler(String dataSource, ID id, boolean setIdAsPrimary) {
        this.dataSource = dataSource;

        if (id != null) {
            this.addId(id);
            if (id.getUnique() && setIdAsPrimary) {
                this.setPrimaryIdentifier(id.getId());
            }
        }

        this.setInternalId();
    }

    /**
     * TODO
     * Note: don't use this constructor if there is an ID that should be
     * primaryIdentifier
     * 
     * @param dataSource
     * @param ids
     */
    public IDsHandler(String dataSource, Set<String> ids) {
        this.dataSource = dataSource;

        if (ids != null) {
            for (String id : ids) {
                this.addId(id);
            }
        }

        this.pickPrimaryIdentifier();
        this.setInternalId();
    }

    public void setPrimaryIdentifier(String primaryId) {
        this.primaryIdentifier = primaryId;
    }

    private void setInternalId() {
        IDsHandler.handlersNb++;
        this.id = IDsHandler.handlersNb;
    }

    /**
     * TODO
     */
    private void pickPrimaryIdentifier() {
        // TODO: error should be thrown before in constructors if dataSource null or
        // unrecognized
        if (!ConverterUtils.isBlankOrNull(this.dataSource) && this.uids.size() > 0) {
            for (ID uid : this.uids) {
                if (this.dataSource.equals(ConverterCVT.SOURCE_NAME_WHO)
                        || (this.dataSource.equals(ConverterCVT.SOURCE_NAME_CTG)
                                && uid.getSource().equals(ConverterCVT.ID_SOURCE_CTG))
                        || (this.dataSource.equals(ConverterCVT.SOURCE_NAME_CTIS)
                                && uid.getSource().equals(ConverterCVT.ID_SOURCE_CTIS))
                        || (this.dataSource.equals(ConverterCVT.SOURCE_NAME_EUCTR)
                                && uid.getSource().equals(ConverterCVT.ID_SOURCE_EUCTR))) {
                    this.setPrimaryIdentifier(uid.getId());
                    break;
                } else if (this.primaryIdentifier == null) {
                    this.setPrimaryIdentifier(uid.getId());
                }
            }
        }
    }

    public ID addId(ID id) {
        if (id != null && !ConverterUtils.dummyIDs.contains(id.getId())) {
            // Checking for dummy IDs or garbage IDs
            if (id.getUnique()) {
                this.uids.add(id);
            } else if (!ConverterUtils.P_ID_GARBAGE.matcher(id.getId()).matches()) {
                this.nonUids.add(id);
            }
        }
        return id;
    }

    public ID addId(String id) {
        if (!ConverterUtils.isBlankOrNull(id)) {
            ID idObj = ConverterUtils.createID(id);
            return this.addId(idObj);
        }
        return null;
    }

    public String addId(StudyIdentifier id) {
        if (id != null) {
            ID idObj = new ID(id.getValue(), id.getSource(), id.getType(), id.getUnique());
            this.addId(idObj);
        }
        return null;
    }

    public Set<ID> addUids(Set<ID> uids) {
        if (uids != null) {
            this.uids.addAll(uids);
        }
        return this.uids;
    }

    public Set<ID> addNonUids(Set<ID> nonUids) {
        if (nonUids != null) {
            this.nonUids.addAll(nonUids);
        }
        return this.nonUids;
    }

    public void addIds(Set<String> ids) {
        if (ids != null) {
            for (String id : ids) {
                this.addId(id);
            }
        }
    }

    public Set<ID> getUids() {
        return this.uids;
    }

    public String getIdFileString() {
        String primaryId = this.primaryIdentifier;
        if (primaryId == null) {
            primaryId = this.getAnyUid().getId();
        }

        StringBuilder uidsSb = new StringBuilder();
        uidsSb.append(primaryId);
        uidsSb.append("\t");

        boolean first = true;
        for (ID uid : this.uids) {
            if (first) {
                first = false;
            } else {
                uidsSb.append(",");
            }
            uidsSb.append(uid.getId());
        }

        return uidsSb.toString();
    }

    public String getNonUidFileString() {
        return this.nonUids.stream()
                .map(ID::getId)
                .collect(Collectors.joining(","));
    }

    public boolean hasAnyUid() {
        return this.uids.size() > 0;
    }

    public boolean hasUid(ID uid) {
        return this.uids.contains(uid);
    }

    public boolean hasNonUid(ID nonUid) {
        return this.nonUids.contains(nonUid);
    }

    public String removeId(String id) {
        if (!ConverterUtils.isBlankOrNull(id)) {
            ID idObj = ConverterUtils.createID(id);
            if (idObj.getUnique()) {
                this.uids.remove(idObj);
            } else {
                this.nonUids.remove(idObj);
            }
        }
        return null;
    }

    public void mergeHandlers(IDsHandler idsHToMerge) {
        this.addUids(idsHToMerge.uids);
        this.addNonUids(idsHToMerge.nonUids);

        // TODO primaryId if both current source and no hit in idresolver
    }

    /**
     * Get any unique ID from the IDsHandler, or null if no IDs are present
     * 
     * @return the unique ID
     */
    public ID getAnyUid() {
        ID uid = null;

        if (this.uids.size() > 0) {
            uid = this.uids.iterator().next();
        }

        return uid;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("IDsHandler [");
        sb.append("id: ");
        sb.append(this.id);
        sb.append(", dataSource: ");
        sb.append(this.dataSource);
        sb.append(", primaryIdentifier: ");
        if (this.primaryIdentifier != null) {
            sb.append(this.primaryIdentifier);
        }
        sb.append(", uids: ");
        sb.append(this.uids);
        sb.append(", nonuids: ");
        sb.append(this.nonUids);
        sb.append("]");

        return sb.toString();
    }
}
