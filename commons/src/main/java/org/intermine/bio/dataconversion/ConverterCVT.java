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
 * TODO: to other file format somehow?
 * @author
 */
public class ConverterCVT
{
    /* Registries */
    public static final String R_EUCTR = "EUCTR";

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
    public static final String FEATURE_T_PHASE = "Phase";   // MDR ID: 20
    public static final String FEATURE_T_PRIMARY_PURPOSE = "Primary purpose";   // MDR ID: 21
    public static final String FEATURE_T_ALLOCATION = "Allocation";   // MDR ID: 22
    public static final String FEATURE_T_INTERVENTION_MODEL = "Intervention model";   // MDR ID: 23
    public static final String FEATURE_T_MASKING = "Masking";   // MDR ID: 24
    public static final String FEATURE_T_TIME_PERSPECTIVE = "Time perspective";   // MDR ID: 31
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
    public static final String TOPIC_TYPE_CHEMICAL_AGENT = "Chemical / Agent";

    /* Objects */
    public static final String O_TYPE_TRIAL_REGISTRY_ENTRY = "Trial registry entry";
    public static final String O_TYPE_TRIAL_REGISTRY_RESULTS_SUMMARY = "Trial registry results summary";
    public static final String O_TYPE_STUDY_PROTOCOL = "Study protocol";
    public static final String O_TYPE_ETHICS_APPROVAL_NOTIFICATION = "Ethics approval notification";
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
    public static final String DATE_TYPE_AVAILABLE = "Available";
    public static final String DATE_TYPE_CREATED = "Created";
    public static final String DATE_TYPE_UPDATED = "Updated";
    public static final String DATE_TYPE_ISSUED = "Issued";

    /* Both */
    public static final String TITLE_UNKNOWN = "Unknown study title";
    public static final String TITLE_TYPE_PUBLIC = "Public title";
    public static final String TITLE_TYPE_SCIENTIFIC = "Scientific title";
    public static final String TITLE_TYPE_ACRONYM = "Acronym or abbreviation";
    public static final String ID_TYPE_TRIAL_REGISTRY = "Trial registry ID";
    public static final String ID_TYPE_SPONSOR = "Sponsor's ID";

    /* External CVs */
    public static final String CV_MEDDRA = "MedDRA";
    public static final String CV_MESH = "MeSH";
    public static final String CV_MESH_TREE = "MeSH Tree";
}
