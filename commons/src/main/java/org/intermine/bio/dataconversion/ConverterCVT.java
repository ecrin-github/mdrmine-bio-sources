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



/**
 * Class with MDR model CV terms
 * @author
 */
public class ConverterCVT
{
    /* Registries */
    public static final String R_ANZCTR = "ANZCTR";    // Australia + New Zealand
    public static final String R_CHICTR = "ChiCTR";    // China
    public static final String R_CRIS = "CRis"; // South Korea
    public static final String R_CTIS = "CTIS"; // EU (new)
    public static final String R_CTG = "CTG";   // US
    public static final String R_CTRI = "CTRI"; // India
    public static final String R_DRKS = "DRKS"; // German
    public static final String R_EUCTR = "EUCTR";   // EU (old)
    public static final String R_ICTRP = "ICTRP"; // WHO (not a registry, used for ID)
    public static final String R_IRCT = "IRCT"; // Iran
    public static final String R_ISRCTN = "ISRCTN"; // UK
    public static final String R_ITMCTR = "ITMCTR";    // Traditional medicine
    public static final String R_JRCT = "jRCT"; // Japan
    public static final String R_LBCTR = "LBCTR";   // Lebanon
    public static final String R_TCTR = "TCTR";  // Thailand
    public static final String R_PACTR = "PACTR"; // Pan African
    public static final String R_REBEC = "ReBec";   // Brazil
    public static final String R_REPEC = "REPEC"; // Peru
    public static final String R_RPCEC = "RPCEC";   // Cuba
    public static final String R_SLCTR = "SLCTR";   // Sri Lanka

    /* Studies */
    public static final String NOT_APPLICABLE = "N/A";
    public static final String UNKNOWN = "Unknown";
    public static final String NONE = "None";
    public static final String NOT_STATED = "Not stated";
    public static final String TYPE_INTERVENTIONAL = "Interventional";
    public static final String TYPE_OBSERVATIONAL = "Observational";
    public static final String TYPE_BASIC_SCIENCE = "Basic science";
    public static final String TYPE_EXPANDED_ACCESS = "Expanded access";
    public static final String TYPE_OTHER = "Other";
    public static final String STATUS_ACTIVE_NOT_RECRUITING = "Active, not recruiting";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_SUSPENDED = "Suspended";
    public static final String FEATURE_T_PHASE = "Phase";   // MDR ID: 20
    public static final String FEATURE_T_PRIMARY_PURPOSE = "Primary purpose";   // MDR ID: 21
    public static final String FEATURE_T_ALLOCATION = "Allocation";   // MDR ID: 22
    public static final String FEATURE_T_INTERVENTION_MODEL = "Intervention model";   // MDR ID: 23
    public static final String FEATURE_T_MASKING = "Masking";   // MDR ID: 24
    public static final String FEATURE_T_TIME_PERSPECTIVE = "Time perspective";   // MDR ID: 31
    public static final String FEATURE_V_EARLY_PHASE_1 = "Early Phase 1";
    public static final String FEATURE_V_RETROSPECTIVE = "Retrospective";
    public static final String FEATURE_V_RANDOMISED = "Randomised";
    public static final String FEATURE_V_NONRANDOMISED = "Nonrandomised";
    public static final String FEATURE_V_SINGLE_BLIND = "Single blind";
    public static final String FEATURE_V_DOUBLE_BLIND = "Double blind";
    public static final String FEATURE_V_NO_BLINDING = "None (Open Label)";
    public static final String FEATURE_V_SINGLE_GROUP = "Single group assignment";
    public static final String FEATURE_V_PARALLEL = "Parallel assignment";
    public static final String FEATURE_V_CROSSOVER = "Crossover assignment";
    public static final String FEATURE_V_FACTORIAL = "Factorial assignment";
    public static final String SPONSOR_NONE = "No sponsor";
    public static final String GENDER_ALL = "All";
    public static final String GENDER_WOMEN = "Female";
    public static final String GENDER_MEN = "Male";
    public static final String AGE_IN_UTERO = "In utero";
    public static final String AGE_UNIT_YEARS = "Years";
    public static final String TOPIC_TYPE_CHEMICAL_AGENT = "Chemical / Agent";

    /* Objects */
    public static final String O_TYPE_TRIAL_REGISTRY_ENTRY = "Trial registry entry";
    public static final String O_TYPE_TRIAL_REGISTRY_RESULTS_SUMMARY = "Trial registry results summary";
    public static final String O_TYPE_STUDY_PROTOCOL = "Study protocol";
    public static final String O_TYPE_ETHICS_APPROVAL_NOTIFICATION = "Ethics approval notification";
    public static final String O_TYPE_INFORMED_CONSENT_FORM = "Informed consent form";
    public static final String O_TYPE_STATISTICAL_ANALYSIS_PLAN = "Statistical analysis plan";
    public static final String O_ACCESS_TYPE_PUBLIC = "Public";
    public static final String O_CLASS_TEXT = "Text";
    public static final String O_RESOURCE_TYPE_PDF = "PDF";
    public static final String O_RESOURCE_TYPE_WORD_DOC = "Word doc";
    public static final String O_RESOURCE_TYPE_WEB_TEXT = "Web text";
    public static final String O_TITLE_RESULTS_SUMMARY = "Results summary";
    public static final String O_TITLE_REGISTRY_ENTRY = "Registry web page";
    public static final String CONTRIBUTOR_TYPE_PUBLIC_CONTACT = "Public contact";
    public static final String CONTRIBUTOR_TYPE_SCIENTIFIC_CONTACT = "Scientific contact";
    public static final String CONTRIBUTOR_TYPE_SPONSOR = "Sponsor";
    public static final String CONTRIBUTOR_TYPE_STUDY_FUNDER = "Study funder";
    public static final String CONTRIBUTOR_TYPE_COLLABORATING_ORG = "Collaborating organisation";
    public static final String DATE_TYPE_AVAILABLE = "Available";
    public static final String DATE_TYPE_CREATED = "Created";
    public static final String DATE_TYPE_UPDATED = "Updated";
    public static final String DATE_TYPE_ISSUED = "Issued";

    /* Both */
    public static final String TITLE_UNKNOWN = "Unknown title";
    public static final String TITLE_TYPE_PUBLIC = "Public title";
    public static final String TITLE_TYPE_SCIENTIFIC = "Scientific title";
    public static final String TITLE_TYPE_ACRONYM = "Acronym or abbreviation";
    public static final String ID_TYPE_TRIAL_REGISTRY = "Trial registry ID";
    public static final String ID_TYPE_SPONSOR = "Sponsor's ID";

    /* External CVs */
    public static final String CV_MEDDRA = "MedDRA";
    public static final String CV_MESH = "MeSH";
    public static final String CV_MESH_TREE = "MeSH Tree";

    /* Data source names */
    public static final String SOURCE_NAME_WHO = "International Clinical Trials Registry Platform (ICTRP)";
    public static final String SOURCE_NAME_CTG = "ClinicalTrials.gov";
    public static final String SOURCE_NAME_CTIS = "Clinical Trials Information System (CTIS)";
    public static final String SOURCE_NAME_EUCTR = "EU Clinical Trials Register";
    public static final String SOURCE_NAME_BIOLINCC = "Biologic Specimen and Data Repository Information Coordinating Center (BioLINCC)";
}
