package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.WordUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;

/**
 * 
 * @author
 */
public class CtgConverter extends CacheConverter {
    //
    private static final String DATASET_TITLE = "CTG-Studies";
    private static final String DATA_SOURCE_NAME = "CTG";
    private static final String DATA_SOURCE_DESC = "ClinicalTrials.gov (NIH)";
    private static final String RESULTS_TAB_URL_SUFFIX = "?tab=results";

    private static final String ALLOCATION_NOT_APPLICABLE = "NA";
    private static final String ENROLLMENT_ACTUAL = "ACTUAL";
    private static final String PRINCIPAL_INVESTIGATOR = "PRINCIPAL_INVESTIGATOR";
    private static final String SPONSOR = "SPONSOR";
    private static final String SPONSOR_INVESTIGATOR = "SPONSOR_INVESTIGATOR";

    private static final Pattern P_PHASE = Pattern.compile("(NA)|(early_)?phase(\\d)(?:\\|phase(\\d))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DOC = Pattern.compile("(.*?),\\h*(http\\S+)\\h*", Pattern.CASE_INSENSITIVE);

    private Map<String, Integer> fieldsToInd;
    private HashSet<String> storedPKs = new HashSet<String>(); // Storing all NCT, EUCTR, and CTIS id to avoid duplicate
                                                               // errors

    /**
     * Constructor
     * 
     * @param writer the ItemWriter used to handle the resultant items
     * @param model  the Model
     */
    public CtgConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * TODO
     * 
     * @param reader
     * @throws Exception
     */
    public void parseData(Reader reader) throws Exception {
        BufferedReader br = (BufferedReader) reader;

        // CtgStudy object reader
        ObjectReader<CtgStudy> objectReader = JSONFactory.getDefaultObjectReaderProvider()
                .getObjectReader(CtgStudy.class);

        br.readLine(); // JSON array start

        String line;
        while ((line = br.readLine()) != null) {
            if (!line.equals("]")) {
                try {
                    CtgStudy jsonStudy = JSON.parseObject(line, CtgStudy.class);
                    this.parseAndStoreTrial(jsonStudy);
                } catch (JSONException e) {
                    this.writeLog("Failed to read JSON study: " + e);
                }
            }
        }
    }

    public void parseAndStoreTrial(CtgStudy ctgStudy) throws Exception {
        Item study = createItem("Study");

        if (this.parseTrialIDs(study, ctgStudy)) {
            /* Study titles */
            this.parseStudyTitle(study, ctgStudy);

            /* Study status */
            this.parseStatus(study, ctgStudy);

            /* Study description */
            this.parseBriefSummary(study, ctgStudy);

            /* Study conditions */
            this.parseConditions(study, ctgStudy);

            /* Study topics */
            this.parseInterventions(study, ctgStudy);

            /* Primary outcomes */
            this.parsePrimaryOutcomes(study, ctgStudy);

            /* Secondary outcomes */
            this.parseSecondaryOutcomes(study, ctgStudy);

            /* Study sponsor (Organisations, Persons) */
            this.parseSponsor(study, ctgStudy);

            /* Study collaborators (Organisations, Persons) */
            this.parseCollaborators(study, ctgStudy);

            /* Gender */
            this.parseGender(study, ctgStudy);

            /* Min/max age */
            this.parseAge(study, ctgStudy);

            /* StudyFeature: phase */
            this.parsePhases(study, ctgStudy);

            /* Study planned/actual enrolment */
            this.parseEnrolment(study, ctgStudy);

            /* Study type */
            this.parseStudyType(study, ctgStudy);

            /* Study features */
            this.parseStudyDesign(study, ctgStudy);

            /* Study start date */
            this.parseStartDate(study, ctgStudy);

            /* Study end date */
            this.parseCompletionDates(study, ctgStudy);

            /* Trial registry entry SO */
            this.createAndStoreRegistryEntrySO(study, ctgStudy);

            /* Trial results summary SO */
            this.createAndStoreResultsSummarySO(study, ctgStudy);

            /* Study locations */
            this.parseLocations(study, ctgStudy);

            /* Various StudyObjects */
            this.parseStudyDocuments(study, ctgStudy);

            /* Plan to share IPD, DSS, IPD SO */
            this.parseIPD(study, ctgStudy);

            /* PubMed publications */
            this.parseReferences(study, ctgStudy);

            // TODO: seeAlsoLinks?

            // Store study in cache
            if (!this.existingStudy()) {
                this.studies.put(this.currentTrialID, study);
            }

        }

        this.currentTrialID = null;

    }

    /**
     * TODO
     * 
     * @param study
     * @param ctgStudy
     * @return
     * @throws Exception
     */
    public boolean parseTrialIDs(Item study, CtgStudy ctgStudy) throws Exception {
        boolean continueParsing = true;

        if (ctgStudy.protocolSection == null) {
            continueParsing = false;
            this.writeLog("Warning: found study with no protocol section");
        } else {

            IdentificationModule idModule = ctgStudy.protocolSection.identificationModule;

            if (idModule != null) {
                String nctID = idModule.nctId;/* Trial ID */
                // TODO: trials have nctIdAliases...

                // Not parsing study if trialID is blank
                // TODO: if studies with blank trial IDs exist, check otherIDs then?
                if (ConverterUtils.isBlankOrNull(nctID)) {
                    continueParsing = false;
                } else {
                    // NCT ID
                    if (storedPKs.contains(nctID)) { // Trial with an ID that is already linked to another study
                        continueParsing = false;
                        this.writeLog("NCT ID already exists: " + nctID);
                    } else {
                        this.currentTrialID = nctID;
                        study.setAttributeIfNotNull("nctID", nctID);
                    }

                    /* Secondary IDs (EUCTR, CTIS, Protocol code, etc.) */
                    if (continueParsing && idModule.secondaryIdInfos != null && idModule.secondaryIdInfos.size() > 0) {
                        boolean ctisIdSet = false;
                        boolean euctrIdSet = false;
                        List<String> euIds = new ArrayList<String>();
                        List<String> otherIds = new ArrayList<String>(); // IDs to be added as "StudyIdentifier" items
                                                                         // later

                        // TODO: now we have adidtional info for secondary IDs, inferring id type can be
                        // improved now

                        // Adding secondaryIDs and trialID into one set
                        Set<String> ids = idModule.secondaryIdInfos.stream()
                                .map(secId -> secId.id)
                                .map(String::strip)
                                .collect(Collectors.toSet());

                        Iterator<String> idsIter = ids.iterator();
                        while (idsIter.hasNext() && continueParsing) {
                            String otherID = idsIter.next();

                            Matcher mEu = ConverterUtils.P_EU_ID.matcher(otherID);
                            if (mEu.matches()) {
                                String ctisPrefix = mEu.group(1);
                                String euctrPrefix = mEu.group(2);
                                String euId = mEu.group(3);
                                String ctisSuffix = mEu.group(4);
                                String euctrSuffix = mEu.group(5);

                                if (storedPKs.contains(euId)) {
                                    continueParsing = false;
                                    this.writeLog("EU ID already exists: " + euId + "; raw id: " + otherID);
                                } else {
                                    if (ctisPrefix != null || ctisSuffix != null) { // CTIS ID
                                        if (euctrPrefix == null && euctrSuffix == null) {
                                            // Setting CTIS ID without prefix and suffix
                                            study.setAttributeIfNotNull("primaryIdentifier", euId);
                                            ctisIdSet = true;
                                        } else {
                                            this.writeLog(
                                                    "CTIS ID matched but also has EUCTR ID characteristics: "
                                                            + otherID);
                                        }
                                    } else if (euctrPrefix != null || euctrSuffix != null) { // EUCTR ID
                                        // Setting EUCTR ID without prefix and suffix
                                        study.setAttributeIfNotNull("euctrID", euId);
                                        euctrIdSet = true;

                                        if (euctrSuffix != null) {
                                            this.writeLog("EUCTR ID matched and suffix is not null: " + euctrSuffix);
                                        }
                                    } else { // Undistinguishable ID
                                        if (ctisIdSet) {
                                            if (!euctrIdSet) {
                                                study.setAttributeIfNotNull("euctrID", euId);
                                                euctrIdSet = true;
                                            } else {
                                                this.writeLog(
                                                        "Found an EU id but both CTIS and EUCTR ID are already set, id: "
                                                                + euId + "; list of all IDs: " + ids);
                                            }
                                        } else if (ctisIdSet) {
                                            study.setAttributeIfNotNull("primaryIdentifier", euId);
                                            ctisIdSet = true;
                                        } else {
                                            euIds.add(euId);
                                        }
                                    }
                                }
                            } else {
                                otherIds.add(otherID);
                            }
                        }

                        if (continueParsing) {
                            // Adding other IDs as StudyIdentifier items
                            for (String otherID : otherIds) {
                                // TODO: infer ID type
                                this.createAndStoreClassItem(study, "StudyIdentifier",
                                        new String[][] { { "identifierValue", otherID } });
                            }

                            // Handling undistinguishable EU IDs
                            if (euIds.size() > 0) {
                                if (euIds.size() > 2) {
                                    this.writeLog(
                                            "More than 2 EU IDs found: " + euIds + "; list of all IDs: " + ids);
                                } else if (euIds.size() == 2) {
                                    if (ctisIdSet || euctrIdSet) {
                                        this.writeLog(
                                                "2 EU IDs found but CTIS ID or EUCTR ID has already been set: " + euIds
                                                        + "; list of all IDs: " + ids);
                                    } else {
                                        String id1 = euIds.get(0);
                                        String id2 = euIds.get(1);

                                        // Assuming that the more recent ID (year + sequential part after) is the CTIS
                                        // ID, and the other is the EUCTR ID
                                        if (id1.compareTo(id2) > 0) {
                                            study.setAttributeIfNotNull("primaryIdentifier", id1);
                                            study.setAttributeIfNotNull("euctrID", id2);
                                        } else {
                                            study.setAttributeIfNotNull("primaryIdentifier", id2);
                                            study.setAttributeIfNotNull("euctrID", id1);
                                        }
                                    }
                                } else { // 1 ID
                                    if (ctisIdSet && euctrIdSet) {
                                        this.writeLog(
                                                "1 EU ID found but both CTIS and EUCTR IDs have already been set: "
                                                        + euIds
                                                        + "; list of all IDs: " + ids);
                                    } else {
                                        String id1 = euIds.get(0);

                                        // Note: if both ctisID and euctrID have not been set before, we populate both
                                        // fields hoping for a merge to correct the fields later
                                        if (!ctisIdSet) {
                                            study.setAttributeIfNotNull("primaryIdentifier", id1);
                                        }
                                        if (!euctrIdSet) {
                                            study.setAttributeIfNotNull("euctrID", id1);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (continueParsing) {
                        nctID = ConverterUtils.getAttrValue(study, "nctID");
                        String euctrID = ConverterUtils.getAttrValue(study, "euctrID");
                        String ctisID = ConverterUtils.getAttrValue(study, "primaryIdentifier");

                        for (String id : new String[] { nctID, ctisID, euctrID }) {
                            if (!ConverterUtils.isBlankOrNull(id)) {
                                this.storedPKs.add(id);
                            }
                        }
                    }
                }
            }
        }

        return continueParsing;
    }

    /**
     * TODO
     * 
     * @param study
     * @param ctgStudy
     */
    public void parseStudyTitle(Item study, CtgStudy ctgStudy) throws Exception {
        IdentificationModule idModule = ctgStudy.protocolSection.identificationModule;

        // TODO: check for "-", "_", ".", etc.?

        // Constructing displayTitle the same way as on the CTG website:
        // "briefTitle (acronym)"
        StringBuilder titleSb = new StringBuilder();

        if (idModule != null) {

            /* Brief (public) title */
            if (!ConverterUtils.isBlankOrNull(idModule.briefTitle)) {
                titleSb.append(idModule.briefTitle);

                this.createAndStoreClassItem(study, "Title",
                        new String[][] { { "text", idModule.briefTitle },
                                { "type", ConverterCVT.TITLE_TYPE_PUBLIC } });
            }

            /* Official (scientific) title */
            if (!ConverterUtils.isBlankOrNull(idModule.officialTitle)) {
                // Only setting officialTitle to displayTitle if no briefTitle
                if (ConverterUtils.isBlankOrNull(titleSb.toString())) {
                    titleSb.append(idModule.officialTitle);
                }

                this.createAndStoreClassItem(study, "Title",
                        new String[][] { { "text", idModule.officialTitle },
                                { "type", ConverterCVT.TITLE_TYPE_SCIENTIFIC } });
            }

            /* Acronym */
            if (!ConverterUtils.isBlankOrNull(idModule.acronym)) {
                if (ConverterUtils.isBlankOrNull(titleSb.toString())) {
                    titleSb.append(idModule.acronym);
                } else {
                    titleSb.append(" (" + idModule.acronym + ")");
                }

                this.createAndStoreClassItem(study, "Title",
                        new String[][] { { "text", idModule.acronym }, { "type", ConverterCVT.TITLE_TYPE_ACRONYM } });
            }
        }

        // Unknown title if not set before
        if (ConverterUtils.isBlankOrNull(titleSb.toString())) {
            titleSb.append(ConverterCVT.TITLE_UNKNOWN);
        }

        study.setAttributeIfNotNull("displayTitle", titleSb.toString());
    }

    /**
     * TODO
     * 
     * @param study
     * @param ctgStudy
     */
    public void parseStatus(Item study, CtgStudy ctgStudy) {
        StatusModule statusModule = ctgStudy.protocolSection.statusModule;

        if (statusModule != null) {
            if (!ConverterUtils.isBlankOrNull(statusModule.overallStatus)) {
                // TODO: proper normalisation
                String cleanedStatus = ConverterUtils.capitaliseAndReplaceCharBySpace(statusModule.overallStatus,
                        '_');
                if (cleanedStatus.equals("Active not recruiting")) { // Temporary
                    cleanedStatus = ConverterCVT.STATUS_ACTIVE_NOT_RECRUITING;
                }
                study.setAttributeIfNotNull("status", cleanedStatus);
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param ctgStudy
     */
    public void parseBriefSummary(Item study, CtgStudy ctgStudy) {
        DescriptionModule descModule = ctgStudy.protocolSection.descriptionModule;

        if (descModule != null) {
            study.setAttribute("description", descModule.briefSummary);
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseConditions(Item study, CtgStudy ctgStudy) throws Exception {
        ConditionsModule condModule = ctgStudy.protocolSection.conditionsModule;

        if (condModule != null) {
            if (condModule.conditions != null && condModule.conditions.size() > 0) {

                /*
                 * Getting MeSH terms corresponding to conditions from the conditionBrowseModule
                 * (if any)
                 */
                // TODO: condition values don't necessary exactly match MeSH terms (NCT00133718)
                HashMap<String, String> meshTerms = new HashMap<String, String>();

                if (Optional.ofNullable(ctgStudy.derivedSection)
                        .map(DerivedSection::getConditionBrowseModule)
                        .map(ConditionBrowseModule::getMeshes)
                        .isPresent()) {
                    for (Mesh mesh : ctgStudy.derivedSection.conditionBrowseModule.meshes) {
                        meshTerms.put(mesh.term.toLowerCase(), mesh.id);
                    }
                }

                // Probably not needed, but just in case
                Set<String> studyConditions = condModule.conditions.stream()
                        .map(String::strip)
                        .collect(Collectors.toSet());

                /* StudyConditions */
                Iterator<String> conditionsIter = studyConditions.iterator();
                while (conditionsIter.hasNext()) {
                    String condition = conditionsIter.next();
                    String meshId = meshTerms.getOrDefault(condition.toLowerCase(), null);
                    if (meshId != null) {
                        this.linkStudyToStudyCondition(study, condition, meshId, ConverterCVT.CV_MESH);
                    } else {
                        this.linkStudyToStudyCondition(study, condition, null, null);
                    }
                }
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseInterventions(Item study, CtgStudy ctgStudy) throws Exception {
        ArmsInterventionsModule armsModule = ctgStudy.protocolSection.armsInterventionsModule;

        if (armsModule != null) {
            // TODO: set Study.interventions?

            if (armsModule.interventions != null && armsModule.interventions.size() > 0) {
                /*
                 * Getting MeSH terms corresponding to interventions from the
                 * interventionBrowseModule (if any)
                 */
                // TODO: intervention values don't necessary exactly match MeSH terms
                HashMap<String, String> meshTerms = new HashMap<String, String>();

                if (Optional.ofNullable(ctgStudy.derivedSection)
                        .map(DerivedSection::getInterventionBrowseModule)
                        .map(InterventionBrowseModule::getMeshes)
                        .isPresent()) {
                    for (Mesh mesh : ctgStudy.derivedSection.interventionBrowseModule.meshes) {
                        meshTerms.put(mesh.term.toLowerCase(), mesh.id);
                    }
                }

                // There can be duplicates with different description/armGroupLabels,
                // treating them as duplicates for now (NCT04745767)
                Set<String> seenInterventionNames = new HashSet<String>();

                /* Topics */
                Iterator<Intervention> interventionsIter = armsModule.interventions.iterator();
                while (interventionsIter.hasNext()) {

                    Intervention intervention = interventionsIter.next();
                    if (!ConverterUtils.isBlankOrNull(intervention.name)
                            && !seenInterventionNames.contains(intervention.name.toLowerCase())) {
                        String interventionType = ConverterUtils.capitaliseAndReplaceCharBySpace(intervention.type,
                                '_');

                        String meshId = meshTerms.getOrDefault(intervention.name.toLowerCase(), null);
                        if (meshId != null) {
                            this.createAndStoreClassItem(study, "Topic",
                                    new String[][] { { "type", interventionType }, { "value", intervention.name },
                                            { "ctType", ConverterCVT.CV_MESH }, { "ctCode", meshId } });
                        } else {
                            this.createAndStoreClassItem(study, "Topic",
                                    new String[][] { { "type", interventionType }, { "value", intervention.name } });
                        }

                        seenInterventionNames.add(intervention.name.toLowerCase());
                    }
                }
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parsePrimaryOutcomes(Item study, CtgStudy ctgStudy) {
        OutcomesModule outcomesModule = ctgStudy.protocolSection.outcomesModule;

        if (outcomesModule != null) {
            if (outcomesModule.primaryOutcomes != null && outcomesModule.primaryOutcomes.size() > 0) {
                StringBuilder poSb = new StringBuilder();

                boolean firstOutcome = true;
                for (Outcome po : outcomesModule.primaryOutcomes) {
                    if (!firstOutcome) {
                        poSb.append(" ");
                    } else {
                        firstOutcome = false;
                    }

                    poSb.append(po.measure);

                    if (!ConverterUtils.isBlankOrNull(po.description)) {
                        poSb.append(": ");
                        poSb.append(po.description);
                    }

                    if (!ConverterUtils.isBlankOrNull(po.timeFrame)) {
                        if (ConverterUtils.isBlankOrNull(po.description)) {
                            poSb.append(": ");
                        }
                        poSb.append(", ");
                        poSb.append(po.timeFrame);
                    }

                    if (!poSb.toString().endsWith(".")) {
                        poSb.append(".");
                    }
                }

                study.setAttributeIfNotNull("primaryOutcome", poSb.toString());
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parseSecondaryOutcomes(Item study, CtgStudy ctgStudy) {
        OutcomesModule outcomesModule = ctgStudy.protocolSection.outcomesModule;

        if (outcomesModule != null) {
            StringBuilder oSb = new StringBuilder();

            if (outcomesModule.secondaryOutcomes != null && outcomesModule.secondaryOutcomes.size() > 0) {
                boolean firstOutcome = true;
                for (Outcome so : outcomesModule.secondaryOutcomes) {
                    if (!firstOutcome) {
                        oSb.append(" ");
                    } else {
                        firstOutcome = false;
                    }

                    oSb.append(so.measure);

                    if (!ConverterUtils.isBlankOrNull(so.description)) {
                        oSb.append(": ");
                        oSb.append(so.description);
                    }

                    if (!ConverterUtils.isBlankOrNull(so.timeFrame)) {
                        if (ConverterUtils.isBlankOrNull(so.description)) {
                            oSb.append(": ");
                        }
                        oSb.append(", ");
                        oSb.append(so.timeFrame);
                    }

                    if (!oSb.toString().endsWith(".")) {
                        oSb.append(".");
                    }
                }
            }

            // TODO: should otherOutcomes be included in secondaryOutcomes?
            // separate field in model?
            if (outcomesModule.otherOutcomes != null && outcomesModule.otherOutcomes.size() > 0) {
                if (oSb.toString().length() > 0) {
                    oSb.append(" ");
                }

                boolean firstOutcome = true;
                for (Outcome oo : outcomesModule.otherOutcomes) {
                    if (!firstOutcome) {
                        oSb.append(" ");
                    } else {
                        firstOutcome = false;
                    }

                    oSb.append(oo.measure);

                    if (!ConverterUtils.isBlankOrNull(oo.description)) {
                        oSb.append(": ");
                        oSb.append(oo.description);
                    }

                    if (!ConverterUtils.isBlankOrNull(oo.timeFrame)) {
                        if (ConverterUtils.isBlankOrNull(oo.description)) {
                            oSb.append(": ");
                        }
                        oSb.append(", ");
                        oSb.append(oo.timeFrame);
                    }

                    if (!oSb.toString().endsWith(".")) {
                        oSb.append(".");
                    }
                }
            }

            study.setAttributeIfNotNull("secondaryOutcomes", oSb.toString());
        }
    }

    public static String getEntityType(String type) {
        String entityType = null;

        switch ((type != null) ? type : ConverterCVT.UNKNOWN) {
            case "FED":
                entityType = ConverterCVT.ORG_TYPE_FEDERAL_US;
                break;
            case "OTHER_GOV":
                entityType = ConverterCVT.ORG_TYPE_GOVERNMENTAL;
                break;
            case "INDUSTRY":
                entityType = ConverterCVT.ORG_TYPE_INDUSTRY;
                break;
            case "NETWORK":
                entityType = ConverterCVT.ORG_TYPE_NETWORK;
                break;
            case "NIH":
                entityType = ConverterCVT.ORG_TYPE_NIH;
                break;
            case "OTHER":
                entityType = ConverterCVT.ORG_TYPE_OTHER;
                break;
            default: // AMBIG, INDIV, UNKNOWN
                entityType = ConverterCVT.UNKNOWN;
                break;
        }

        return entityType;
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseSponsor(Item study, CtgStudy ctgStudy) throws Exception {
        SponsorCollaboratorsModule sponsorModule = ctgStudy.protocolSection.sponsorCollaboratorsModule;

        if (sponsorModule != null) {
            boolean addedSponsor = false;
            if (sponsorModule.responsibleParty != null) {
                if (sponsorModule.responsibleParty.type != null) {
                    if (sponsorModule.responsibleParty.type.equalsIgnoreCase(PRINCIPAL_INVESTIGATOR)) {
                        // Responsible party is PI and sponsor is their organisation
                        Item piPerson = this.createAndStoreClassItem(study, "Person",
                                new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_PRINCIPAL_INVESTIGATOR }, {
                                        "fullName", sponsorModule.responsibleParty.investigatorFullName } });

                        if (sponsorModule.leadSponsor != null) {
                            String sponsorType = CtgConverter.getEntityType(sponsorModule.leadSponsor.clazz);
                            Item sponsorOrg = this.createClassItem(study, "Organisation",
                                    new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_SPONSOR },
                                            { "name", sponsorModule.leadSponsor.name },
                                            { "type", sponsorType } });
                            addedSponsor = true;

                            // PI affiliation
                            this.handleReferencesAndCollections(piPerson, sponsorOrg);
                            store(sponsorOrg);
                        }
                    } else if (sponsorModule.responsibleParty.type.equalsIgnoreCase(SPONSOR_INVESTIGATOR)) {
                        // PI is also sponsor
                        Item piPerson = this.createAndStoreClassItem(study, "Person",
                                new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_SPONSOR_INVESTIGATOR }, {
                                        "fullName", sponsorModule.responsibleParty.investigatorFullName } });
                        addedSponsor = true;
                        // TODO responsibleParty.investigatorAffiliation to create an Organisation item
                    } else if (!sponsorModule.responsibleParty.type.equalsIgnoreCase(SPONSOR)) {
                        this.writeLog("Warning: Unknown responsibleParty type: " + sponsorModule.responsibleParty.type);
                    }
                    // Else: sponsor handled below
                } else {
                    // TODO: handle this case
                    this.writeLog("Warning: unhandled responsibleParty case (likely oldNameTitle + oldOrganization)");
                }
            }

            if (!addedSponsor && sponsorModule.leadSponsor != null) {
                if (sponsorModule.responsibleParty == null) {
                    this.writeLog("Warning: found study with no responsibleParty but a leadSponsor");
                }

                if (!ConverterUtils.isBlankOrNull(sponsorModule.leadSponsor.name)) {
                    String sponsorType = CtgConverter.getEntityType(sponsorModule.leadSponsor.clazz);

                    // INDIV class is always a Person, however there are others that mix people and
                    // organisation together: AMBIG, OTHER, UNKNOWN
                    // Since most sponsor of these classes are Organisation, assuming they are
                    // organisation for now (TODO: better separation)
                    if (sponsorModule.leadSponsor.clazz != null
                            && sponsorModule.leadSponsor.clazz.equalsIgnoreCase("INDIV")) {
                        this.createAndStoreClassItem(study, "Person",
                                new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_SPONSOR },
                                        { "fullName", sponsorModule.leadSponsor.name } });
                    } else {
                        this.createAndStoreClassItem(study, "Organisation",
                                new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_SPONSOR },
                                        { "name", sponsorModule.leadSponsor.name },
                                        { "type", sponsorType } });
                    }
                } else {
                    this.writeLog(
                            "Warning: found leadSponsor with no/empty name: " + sponsorModule.leadSponsor.name + " "
                                    + sponsorModule.leadSponsor.clazz);
                }
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseCollaborators(Item study, CtgStudy ctgStudy) throws Exception {
        SponsorCollaboratorsModule sponsorModule = ctgStudy.protocolSection.sponsorCollaboratorsModule;

        if (sponsorModule != null) {
            if (sponsorModule.collaborators != null && sponsorModule.collaborators.size() > 0) {
                for (Collaborator c : sponsorModule.collaborators) {
                    if (!ConverterUtils.isBlankOrNull(c.name)) {
                        String collaboratorType = CtgConverter.getEntityType(c.clazz);

                        // INDIV class is always a Person, however there are others that mix people and
                        // organisation together: AMBIG, OTHER, UNKNOWN
                        // Since most sponsor of these classes are Organisation, assuming they are
                        // organisation for now (TODO: better separation)
                        if (c.clazz != null && c.clazz.equalsIgnoreCase("INDIV")) {
                            this.createAndStoreClassItem(study, "Person",
                                    new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_COLLABORATING_PERSON },
                                            { "fullName", c.name } });
                        } else {
                            this.createAndStoreClassItem(study, "Organisation",
                                    new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_COLLABORATING_ORG },
                                            { "name", c.name },
                                            { "type", collaboratorType } });
                        }
                    } else {
                        this.writeLog(
                                "Warning: found collaborator with no/empty name: " + c.name + " " + c.clazz);
                    }
                }
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parseGender(Item study, CtgStudy ctgStudy) {
        EligibilityModule eligModule = ctgStudy.protocolSection.eligibilityModule;

        // TODO: do something with eligModule.genderDescription?
        if (eligModule != null) {
            if (!ConverterUtils.isBlankOrNull(eligModule.sex)) {
                if (eligModule.sex.equalsIgnoreCase(ConverterCVT.GENDER_ALL)) {
                    study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_ALL);
                } else if (eligModule.sex.equalsIgnoreCase(ConverterCVT.GENDER_WOMEN)) {
                    study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_WOMEN);
                } else if (eligModule.sex.equalsIgnoreCase(ConverterCVT.GENDER_MEN)) {
                    study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_MEN);
                } else {
                    this.writeLog("Unknown gender value: " + eligModule.sex);
                }
            }
        }

    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parseAge(Item study, CtgStudy ctgStudy) {
        EligibilityModule eligModule = ctgStudy.protocolSection.eligibilityModule;

        if (eligModule != null) {
            if (!ConverterUtils.isBlankOrNull(eligModule.minimumAge)) {
                String[] minAgeSplit = eligModule.minimumAge.split(" ");

                if (minAgeSplit.length > 1) {
                    study.setAttributeIfNotNull("minAge", minAgeSplit[0]);
                    study.setAttributeIfNotNull("minAgeUnit",
                            minAgeSplit[1] + (minAgeSplit[1].endsWith("s") ? "" : "s"));
                } else {
                    this.writeLog("Warning: failed to split minimum age string: " + eligModule.minimumAge);
                }
            }
            if (!ConverterUtils.isBlankOrNull(eligModule.maximumAge)) {
                String[] maxAgeSplit = eligModule.maximumAge.split(" ");

                if (maxAgeSplit.length > 1) {
                    study.setAttributeIfNotNull("maxAge", maxAgeSplit[0]);
                    study.setAttributeIfNotNull("maxAgeUnit",
                            maxAgeSplit[1] + (maxAgeSplit[1].endsWith("s") ? "" : "s"));
                } else {
                    this.writeLog("Warning: failed to split maximum age string: " + eligModule.maximumAge);
                }
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parsePhases(Item study, CtgStudy ctgStudy) throws Exception {
        DesignModule designModule = ctgStudy.protocolSection.designModule;

        if (designModule != null) {
            if (designModule.phases != null && designModule.phases.size() > 0) {
                // Reusing CSV code by re-constructing CSV value
                String phasesStr = String.join("|", designModule.phases);

                Matcher mPhase = P_PHASE.matcher(phasesStr);
                if (mPhase.matches()) {
                    String phaseValue = "";

                    String na = mPhase.group(1);
                    String early = mPhase.group(2);
                    String p1 = mPhase.group(3);
                    String p2 = mPhase.group(4);

                    if (na != null) { // Not applicable
                        phaseValue = ConverterCVT.NOT_APPLICABLE;
                    } else {
                        if (early != null) {
                            phaseValue = ConverterCVT.FEATURE_V_EARLY_PHASE_1;
                        } else if (p2 == null) { // One phase number
                            phaseValue = ConverterUtils.convertPhaseNumber(p1);
                        } else { // Two phase numbers
                            phaseValue = ConverterUtils.constructMultiplePhasesString(p1, p2);
                        }
                    }

                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] { { "featureType", ConverterCVT.FEATURE_T_PHASE },
                                    { "featureValue", ConverterCVT.FEATURE_T_PHASE + " " + phaseValue } });
                } else {
                    this.writeLog("Failed to match phase value: " + phasesStr);
                }
            }
        }

    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parseEnrolment(Item study, CtgStudy ctgStudy) {
        if (Optional.ofNullable(ctgStudy.protocolSection.designModule)
                .map(DesignModule::getEnrollmentInfo)
                .map(EnrollmentInfo::getCount)
                .isPresent()) {

            EnrollmentInfo enrollmentInfo = ctgStudy.protocolSection.designModule.enrollmentInfo;

            if (!ConverterUtils.isBlankOrNull(enrollmentInfo.type)
                    && enrollmentInfo.type.equalsIgnoreCase(ENROLLMENT_ACTUAL)) {
                study.setAttributeIfNotNull("actualEnrolment", enrollmentInfo.count);
            } else { // If enrollmentInfo.count is "ESTIMATED" or absent,
                     // we default to plannedEnrolment
                study.setAttributeIfNotNull("plannedEnrolment", enrollmentInfo.count);
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parseStudyType(Item study, CtgStudy ctgStudy) {
        DesignModule designModule = ctgStudy.protocolSection.designModule;

        if (designModule != null) {
            if (!ConverterUtils.isBlankOrNull(designModule.studyType)) {
                study.setAttributeIfNotNull("type",
                        ConverterUtils.capitaliseAndReplaceCharBySpace(designModule.studyType, '_'));
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseStudyDesign(Item study, CtgStudy ctgStudy) throws Exception {
        DesignModule designModule = ctgStudy.protocolSection.designModule;

        if (designModule != null) {
            DesignInfo designInfo = designModule.designInfo;

            if (designInfo != null) {
                // StudyFeature: Allocation
                if (!ConverterUtils.isBlankOrNull(designInfo.allocation)) {
                    String allocation = "";
                    if (designInfo.allocation.equalsIgnoreCase(ALLOCATION_NOT_APPLICABLE)) {
                        allocation = ConverterCVT.NOT_APPLICABLE;
                    } else {
                        allocation = ConverterUtils.capitaliseAndReplaceCharBySpace(designInfo.allocation, '_');
                    }

                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] {
                                    { "featureType", ConverterCVT.FEATURE_T_ALLOCATION },
                                    { "featureValue", allocation } });
                }

                // StudyFeature: Interventional model
                // TODO: use designInfo.interventionModelDescription somewhere?
                if (!ConverterUtils.isBlankOrNull(designInfo.interventionModel)) {
                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] {
                                    { "featureType", ConverterCVT.FEATURE_T_INTERVENTION_MODEL },
                                    { "featureValue", ConverterUtils
                                            .capitaliseAndReplaceCharBySpace(designInfo.interventionModel, '_') } });
                }

                // StudyFeature: Masking
                // TODO: use designInfo.maskingInfo.maskingDescription somewhere?
                if (designInfo.maskingInfo != null && !ConverterUtils.isBlankOrNull(designInfo.maskingInfo.masking)) {
                    StringBuilder maskingSb = new StringBuilder();
                    maskingSb.append(
                            ConverterUtils.capitaliseAndReplaceCharBySpace(designInfo.maskingInfo.masking, '_'));
                    maskingSb.append(" ");

                    if (designInfo.maskingInfo.whoMasked != null && designInfo.maskingInfo.whoMasked.size() > 0) {
                        maskingSb.append("(");

                        boolean first = true;
                        for (String masked : designInfo.maskingInfo.whoMasked) {
                            if (first) {
                                first = false;
                            } else {
                                maskingSb.append(", ");
                            }
                            maskingSb.append(ConverterUtils.capitaliseAndReplaceCharBySpace(masked, '_'));
                        }

                        maskingSb.append(")");
                    }
                    String maskingValue = designInfo.maskingInfo.masking;
                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] {
                                    { "featureType", ConverterCVT.FEATURE_T_MASKING },
                                    { "featureValue", maskingSb.toString() } });
                }

                // StudyFeature: Observational model
                if (!ConverterUtils.isBlankOrNull(designInfo.observationalModel)) {
                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] {
                                    { "featureType", ConverterCVT.FEATURE_T_OBSERVATIONAL_MODEL },
                                    { "featureValue", ConverterUtils
                                            .capitaliseAndReplaceCharBySpace(designInfo.observationalModel, '_') } });
                }

                // StudyFeature: Primary purpose
                // TODO: keep ECT capitalised
                if (!ConverterUtils.isBlankOrNull(designInfo.primaryPurpose)) {
                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] {
                                    { "featureType", ConverterCVT.FEATURE_T_PRIMARY_PURPOSE },
                                    { "featureValue", ConverterUtils
                                            .capitaliseAndReplaceCharBySpace(designInfo.primaryPurpose, '_') } });
                }

                // StudyFeature: Time perspective
                if (!ConverterUtils.isBlankOrNull(designInfo.timePerspective)) {
                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] {
                                    { "featureType", ConverterCVT.FEATURE_T_TIME_PERSPECTIVE },
                                    { "featureValue", ConverterUtils
                                            .capitaliseAndReplaceCharBySpace(designInfo.timePerspective, '_') } });
                }
            }
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parseStartDate(Item study, CtgStudy ctgStudy) {
        if (Optional.ofNullable(ctgStudy.protocolSection.statusModule)
                .map(StatusModule::getStartDateStruct)
                .map(DateStruct::getDate)
                .isPresent()) {

            String startDateStr = CtgConverter
                    .normaliseDateString(ctgStudy.protocolSection.statusModule.startDateStruct.date);
            study.setAttributeIfNotNull("startDate", startDateStr);
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     */
    public void parseCompletionDates(Item study, CtgStudy ctgStudy) {
        String studyEndDateStr = null;

        if (Optional.ofNullable(ctgStudy.protocolSection.statusModule)
                .map(StatusModule::getCompletionDateStruct)
                .map(DateStruct::getDate)
                .isPresent()) {
            // Last data collection (all outcome measures) date
            studyEndDateStr = CtgConverter
                    .normaliseDateString(ctgStudy.protocolSection.statusModule.completionDateStruct.date);
        } else if (Optional.ofNullable(ctgStudy.protocolSection.statusModule)
                .map(StatusModule::getPrimaryCompletionDateStruct)
                .map(DateStruct::getDate)
                .isPresent()) {
            // Last primary outcome measure data collection date
            studyEndDateStr = CtgConverter
                    .normaliseDateString(ctgStudy.protocolSection.statusModule.primaryCompletionDateStruct.date);
        }

        study.setAttributeIfNotNull("endDate", studyEndDateStr);
    }

    /**
     * TODO
     *
     * @param study
     * @throws Exception
     */
    public void createAndStoreRegistryEntrySO(Item study, CtgStudy ctgStudy)
            throws Exception {
        String nctID = ConverterUtils.getAttrValue(study, "nctID");
        if (!ConverterUtils.isBlankOrNull(nctID)) {
            String dateCreated = null;
            String datePublished = null;
            String dateUpdated = null;
            StatusModule statusModule = ctgStudy.protocolSection.statusModule;

            // SO dates
            if (statusModule != null) {
                // Registry entry record created by sponsor/investigator and submitted to CTG
                // date
                if (!ConverterUtils.isBlankOrNull(statusModule.studyFirstSubmitDate)) {
                    dateCreated = statusModule.studyFirstSubmitDate;
                }

                // Registry entry record posted on CTG date
                if (statusModule.studyFirstPostDateStruct != null
                        && !ConverterUtils.isBlankOrNull(statusModule.studyFirstPostDateStruct.date)) {
                    datePublished = statusModule.studyFirstPostDateStruct.date;
                }

                // Registry entry record last update date
                if (statusModule.lastUpdatePostDateStruct != null
                        && !ConverterUtils.isBlankOrNull(statusModule.lastUpdatePostDateStruct.date)) {
                    dateUpdated = statusModule.lastUpdatePostDateStruct.date;
                }
            }

            String studyDisplayTitle = ConverterUtils.getAttrValue(study, "displayTitle");
            String doDisplayTitle;
            if (!ConverterUtils.isBlankOrNull(studyDisplayTitle)) {
                doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TITLE_REGISTRY_ENTRY;
            } else {
                doDisplayTitle = ConverterCVT.O_TITLE_REGISTRY_ENTRY;
            }

            // Publication year
            String publicationYear = null;
            if (datePublished != null && datePublished.length() >= 4) {
                publicationYear = datePublished.substring(0, 4);
            }

            /* Trial registry entry SO */
            // TODO: publication year?
            this.createAndStoreClassItem(study, "StudyObject",
                    new String[][] { { "type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY },
                            { "dateCreated", dateCreated },
                            { "datePublished", datePublished },
                            { "dateUpdated", dateUpdated },
                            { "publicationYear", publicationYear },
                            { "accessUrl", ConverterCVT.CTG_STUDY_BASE_URL + nctID },
                            { "accessType", ConverterCVT.O_ACCESS_TYPE_PUBLIC },
                            { "urlTargetType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT },
                            { "displayTitle", doDisplayTitle } });
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void createAndStoreResultsSummarySO(Item study, CtgStudy ctgStudy)
            throws Exception {
        String nctID = ConverterUtils.getAttrValue(study, "nctID");
        if (ctgStudy.hasResults && !ConverterUtils.isBlankOrNull(nctID)) {
            String dateCreated = null;
            String datePublished = null;
            String dateUpdated = null;
            StatusModule statusModule = ctgStudy.protocolSection.statusModule;

            // SO dates
            if (statusModule != null) {
                // Summary results record created by sponsor/investigator and submitted to CTG
                // date
                if (!ConverterUtils.isBlankOrNull(statusModule.resultsFirstSubmitDate)) {
                    dateCreated = statusModule.resultsFirstSubmitDate;
                }

                // Summary results record posted on CTG date
                if (statusModule.resultsFirstPostDateStruct != null
                        && !ConverterUtils.isBlankOrNull(statusModule.resultsFirstPostDateStruct.date)) {
                    datePublished = statusModule.resultsFirstPostDateStruct.date;
                }

                // Study record last update date, may or may not concern results summary info
                if (statusModule.lastUpdatePostDateStruct != null
                        && !ConverterUtils.isBlankOrNull(statusModule.lastUpdatePostDateStruct.date)) {
                    dateUpdated = statusModule.lastUpdatePostDateStruct.date;
                }
            }

            // Display title
            String studyDisplayTitle = ConverterUtils.getAttrValue(study,
                    "displayTitle");
            String doDisplayTitle;
            if (!ConverterUtils.isBlankOrNull(studyDisplayTitle)) {
                doDisplayTitle = studyDisplayTitle + " - " +
                        ConverterCVT.O_TITLE_RESULTS_SUMMARY;
            } else {
                doDisplayTitle = ConverterCVT.O_TITLE_RESULTS_SUMMARY;
            }

            // Publication year
            String publicationYear = null;
            if (datePublished != null && datePublished.length() >= 4) {
                publicationYear = datePublished.substring(0, 4);
            }

            /* Results summary SO */
            this.createAndStoreClassItem(study, "StudyObject",
                    new String[][] { { "displayTitle", doDisplayTitle },
                            { "dateCreated", dateCreated },
                            { "datePublished", datePublished },
                            { "dateUpdated", dateUpdated },
                            { "publicationYear", publicationYear },
                            { "accessUrl", ConverterCVT.CTG_STUDY_BASE_URL + nctID + RESULTS_TAB_URL_SUFFIX },
                            { "accessType", ConverterCVT.O_ACCESS_TYPE_PUBLIC },
                            { "urlTargetType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT },
                            { "type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_RESULTS_SUMMARY } });
        }
    }

    /**
     * TODO
     *
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseLocations(Item study, CtgStudy ctgStudy) throws Exception {
        ContactsLocationsModule contactsLocationsModule = ctgStudy.protocolSection.contactsLocationsModule;

        // TODO: location contacts
        if (contactsLocationsModule != null) {
            List<Location> locations = contactsLocationsModule.locations;
            if (locations != null && locations.size() > 0) {
                for (Location loc : locations) {
                    String facility = ConverterUtils.capitaliseAndReplaceCharBySpace(loc.facility, '_');
                    Item location = this.createClassItem(study, "Location",
                            new String[][] { { "countryName", loc.country },
                                    { "cityName", loc.city },
                                    { "facility", facility },
                                    { "status", loc.status } });

                    Item country = this.getCountry(loc.country);
                    if (country != null) {
                        this.handleReferencesAndCollections(country, location);
                    }
                    store(location);
                }
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param displayTitle
     * @param objectType
     * @param url
     * @param dateCreated
     * @param datePublished
     * @throws Exception
     */
    public void createAndStoreStudyDocument(Item study, String displayTitle, String objectType, String url,
            String dateCreated, String datePublished)
            throws Exception {
        this.createAndStoreClassItem(study, "StudyObject",
                new String[][] { { "type", objectType },
                        { "accessUrl", url },
                        { "accessType", ConverterCVT.O_ACCESS_TYPE_PUBLIC },
                        { "dateCreated", dateCreated },
                        { "datePublished", datePublished },
                        { "displayTitle", displayTitle } });
    }

    /**
     * TODO
     *
     * @param study
     * @param studyDocuments
     * @throws Exception
     */
    public void parseStudyDocuments(Item study, CtgStudy ctgStudy) throws Exception {
        String nctID = ConverterUtils.getAttrValue(study, "nctID");

        if (!ConverterUtils.isBlankOrNull(nctID) && nctID.length() >= 2) {
            if (Optional.ofNullable(ctgStudy.documentSection)
                    .map(DocumentSection::getLargeDocumentModule)
                    .map(LargeDocumentModule::getLargeDocs)
                    .isPresent()) {

                String nctSubDir = nctID.substring(nctID.length() - 2);

                List<LargeDoc> studyDocuments = ctgStudy.documentSection.largeDocumentModule.largeDocs;
                for (LargeDoc studyDoc : studyDocuments) {
                    if (!ConverterUtils.isBlankOrNull(studyDoc.filename)) {
                        String datePublished = null;
                        if (!ConverterUtils.isBlankOrNull(studyDoc.uploadDate) && studyDoc.uploadDate.length() >= 10) { // YYYY-MM-DD
                            datePublished = studyDoc.uploadDate.substring(0, 10);
                        }

                        String url = ConverterCVT.CTG_DOCUMENT_BASE_URL + nctSubDir + "/" + nctID + "/"
                                + studyDoc.filename;

                        // TODO: decide if one SO with multiple types or one SO per type
                        if (studyDoc.hasIcf) {
                            this.createAndStoreStudyDocument(study, studyDoc.label, ConverterCVT.O_TYPE_ICF, url,
                                    studyDoc.date, datePublished);
                        }
                        if (studyDoc.hasProtocol) {
                            this.createAndStoreStudyDocument(study, studyDoc.label, ConverterCVT.O_TYPE_PROT, url,
                                    studyDoc.date, datePublished);
                        }
                        if (studyDoc.hasSap) {
                            this.createAndStoreStudyDocument(study, studyDoc.label, ConverterCVT.O_TYPE_SAP, url,
                                    studyDoc.date, datePublished);
                        }
                    } else {
                        this.writeLog("Found a study document with no filename: " + studyDoc.uploadDate);
                    }
                }
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseIPD(Item study, CtgStudy ctgStudy) throws Exception {
        if (Optional.ofNullable(ctgStudy.protocolSection)
                .map(ProtocolSection::getIpdSharingStatementModule)
                .isPresent()) {
            IpdSharingStatementModule ipdModule = ctgStudy.protocolSection.ipdSharingStatementModule;

            String planToShareIPD = null;
            if (!ConverterUtils.isBlankOrNull(ipdModule.ipdSharing)) {
                planToShareIPD = WordUtils.capitalizeFully(ipdModule.ipdSharing);

                if (ConverterUtils.isYes(planToShareIPD)) {
                    StringBuilder dssSb = new StringBuilder();

                    if (!ConverterUtils.isBlankOrNull(ipdModule.description)) {
                        dssSb.append(ipdModule.description);

                        if (!ipdModule.description.endsWith(".")) {
                            dssSb.append(".");
                        }
                    }

                    if (ipdModule.infoTypes != null && ipdModule.infoTypes.size() > 0) {
                        dssSb.append(" Supporting documents that will be shared: ");
                        for (int i = 0; i < ipdModule.infoTypes.size(); i++) {
                            dssSb.append(ipdModule.infoTypes.get(i));
                            if (i < ipdModule.infoTypes.size() - 1) {
                                dssSb.append(", ");
                            } else {
                                dssSb.append(".");
                            }
                        }
                    }

                    if (!ConverterUtils.isBlankOrNull(ipdModule.timeFrame)) {
                        dssSb.append(" Time frame: ");
                        dssSb.append(ipdModule.timeFrame);

                        if (!ipdModule.timeFrame.endsWith(".")) {
                            dssSb.append(".");
                        }
                    }

                    if (!ConverterUtils.isBlankOrNull(ipdModule.accessCriteria)) {
                        dssSb.append(" Access criteria : ");
                        dssSb.append(ipdModule.accessCriteria);

                        if (!ipdModule.accessCriteria.endsWith(".")) {
                            dssSb.append(".");
                        }
                    }

                    study.setAttributeIfNotNull("dataSharingStatement", dssSb.toString());

                    /* StudyObject: IPD */
                    String ipdUrl = ipdModule.url;
                    String objectId = null;

                    // Attempting to find IPD URL in AvailIpd list
                    if (ConverterUtils.isBlankOrNull(ipdUrl)) {
                        if (Optional.ofNullable(ctgStudy.protocolSection)
                                .map(ProtocolSection::getReferencesModule)
                                .map(ReferencesModule::getAvailIpds)
                                .map(availIpds -> !availIpds.isEmpty())
                                .orElse(false)) {

                            for (AvailIpd availIpd : ctgStudy.protocolSection.referencesModule.availIpds) {

                                if (!ConverterUtils.isBlankOrNull(availIpd.type)
                                        && !ConverterUtils.isBlankOrNull(availIpd.url)
                                        && (availIpd.type.toLowerCase().contains("individual")
                                                || availIpd.type.toLowerCase().equals("ipd"))) {

                                    if (ConverterUtils.isBlankOrNull(ipdUrl)) {
                                        ipdUrl = availIpd.url;
                                        objectId = availIpd.id;
                                    } else if (ipdUrl.equalsIgnoreCase(availIpd.url)) {
                                        this.writeLog(
                                                "Warning: found a different IPD URL in a study which already has one: "
                                                        + availIpd.url);
                                    }
                                }
                            }
                        }
                    }

                    if (!ConverterUtils.isBlankOrNull(ipdUrl)) {
                        Item ipdDO = this.createAndStoreClassItem(study, "StudyObject",
                                new String[][] { { "displayTitle", ConverterCVT.O_TYPE_IPD },
                                        { "objectId", objectId },
                                        { "type", ConverterCVT.O_TYPE_IPD },
                                        { "accessType", ConverterCVT.O_ACCESS_TYPE_CASE_BY_CASE_DOWNLOAD },
                                        { "accessUrl", ipdUrl } });
                    }
                } else { // Other fields are empty
                    study.setAttributeIfNotNull("dataSharingStatement", ipdModule.description);
                }
            }

            study.setAttributeIfNotNull("planToShareIPD", planToShareIPD);
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param ctgStudy
     * @throws Exception
     */
    public void parseReferences(Item study, CtgStudy ctgStudy) throws Exception {
        if (Optional.ofNullable(ctgStudy.protocolSection)
                .map(ProtocolSection::getReferencesModule)
                .map(ReferencesModule::getReferences)
                .map(references -> !references.isEmpty())
                .orElse(false)) {
            for (Reference ref : ctgStudy.protocolSection.referencesModule.references) {
                if (!ConverterUtils.isBlankOrNull(ref.pmid)) {
                    this.createAndStoreClassItem(study, "Publication",
                            new String[][] { { "pubMedId", ref.pmid } });
                }
            }
        }
    }

    /**
     * Add -01 prefix to date only composed of year + month
     * 
     * @param dateString
     * @return
     */
    public static String normaliseDateString(String dateString) {
        if (!ConverterUtils.isBlankOrNull(dateString) && dateString.length() == 7) {
            dateString = dateString + "-01";
        }
        return dateString;
    }

    /**
     * Clean value and strip
     * 
     * @param s
     * @return
     */
    public String cleanValue(String s) {
        return this.cleanValue(s, true);
    }

    public String cleanValue(String s, boolean strip) {
        // TODO: unescape HTML
        if (strip) {
            // return ConverterUtils.unescapeHtml(ConverterUtils.removeQuotes(s)).strip();
            return ConverterUtils.removeQuotes(s).strip();
        }
        return ConverterUtils.removeQuotes(s);
    }
}
