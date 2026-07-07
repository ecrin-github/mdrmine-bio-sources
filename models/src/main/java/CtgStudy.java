package org.intermine.bio.dataconversion;

import java.util.List;

import com.alibaba.fastjson2.annotation.JSONField;

import lombok.Getter;

public class CtgStudy {
    @Getter public ProtocolSection protocolSection;
    @Getter public ResultsSection resultsSection;
    @Getter public AnnotationSection annotationSection;
    @Getter public DocumentSection documentSection;
    @Getter public DerivedSection derivedSection;
    @Getter public boolean hasResults;
}

/* ProtocolSection */

class ProtocolSection {
    @Getter public IdentificationModule identificationModule;
    @Getter public StatusModule statusModule;
    @Getter public SponsorCollaboratorsModule sponsorCollaboratorsModule;
    @Getter public OversightModule oversightModule;
    @Getter public DescriptionModule descriptionModule;
    @Getter public ConditionsModule conditionsModule;
    @Getter public DesignModule designModule;
    @Getter public ArmsInterventionsModule armsInterventionsModule;
    @Getter public OutcomesModule outcomesModule;
    @Getter public EligibilityModule eligibilityModule;
    @Getter public ContactsLocationsModule contactsLocationsModule;
    @Getter public ReferencesModule referencesModule;
    @Getter public IpdSharingStatementModule ipdSharingStatementModule;
}

class IdentificationModule {
    @Getter public String nctId;
    @Getter public List<String> nctIdAliases;
    @Getter public OrgStudyIdInfo orgStudyIdInfo;
    @Getter public List<SecondaryIdInfo> secondaryIdInfos;
    @Getter public String briefTitle;
    @Getter public String officialTitle;
    @Getter public String acronym;
    @Getter public Organization organization;
}

class OrgStudyIdInfo {
    @Getter public String id;
    @Getter public String type;
    @Getter public String link;
}

class SecondaryIdInfo {
    @Getter public String id;
    @Getter public String type;
    @Getter public String domain;
    @Getter public String link;
}

class Organization {
    @Getter public String fullName;
    @JSONField(name="class")
    @Getter public String clazz;
}

class StatusModule {
    @Getter public String statusVerifiedDate;
    @Getter public String overallStatus;
    @Getter public String lastKnownStatus;
    @Getter public String delayedPosting;
    @Getter public String whyStopped;
    @Getter public ExpandedAccessInfo expandedAccessInfo;
    @Getter public DateStruct startDateStruct;
    @Getter public DateStruct primaryCompletionDateStruct;
    @Getter public DateStruct completionDateStruct;
    @Getter public String studyFirstSubmitDate;
    @Getter public String studyFirstSubmitQcDate;
    @Getter public DateStruct studyFirstPostDateStruct;
    @Getter public String resultsWaived;
    @Getter public String resultsFirstSubmitDate;
    @Getter public String resultsFirstSubmitQcDate;
    @Getter public DateStruct resultsFirstPostDateStruct;
    @Getter public String dispFirstSubmitDate;
    @Getter public String dispFirstSubmitQcDate;
    @Getter public DateStruct dispFirstPostDateStruct;
    @Getter public String lastUpdateSubmitDate;
    @Getter public DateStruct lastUpdatePostDateStruct;
}

class ExpandedAccessInfo {
    @Getter public String hasExpandedAccess;
    @Getter public String nctId;
    @Getter public String statusForNctId;
}

class DateStruct {
    @Getter public String date;
    @Getter public String type;
}

class SponsorCollaboratorsModule {
    @Getter public ResponsibleParty responsibleParty;
    @Getter public LeadSponsor leadSponsor;
    @Getter public List<Collaborator> collaborators;
}

class ResponsibleParty {
    @Getter public String type;
    @Getter public String investigatorFullName;
    @Getter public String investigatorTitle;
    @Getter public String investigatorAffiliation;
    @Getter public String oldNameTitle;
    @Getter public String oldOrganization;
}

class LeadSponsor {
    @Getter public String name;
    @JSONField(name="class")
    @Getter public String clazz;
}

class Collaborator {
    @Getter public String name;
    @JSONField(name="class")
    @Getter public String clazz;
}

class OversightModule {
    @Getter public String oversightHasDmc;
    @Getter public String isFdaRegulatedDrug;
    @Getter public String isFdaRegulatedDevice;
    @Getter public String isUnapprovedDevice;
    @Getter public String isPpsd;
    @Getter public String isUsExport;
    @Getter public String fdaaa801Violation;
}

class DescriptionModule {
    @Getter public String briefSummary;
    @Getter public String detailedDescription;
}

class ConditionsModule {
    @Getter public List<String> conditions;
    @Getter public List<String> keywords;
}

class DesignModule {
    @Getter public String studyType;
    @Getter public String nPtrsToThisExpAccNctId;
    @Getter public ExpandedAccessTypes expandedAccessTypes;
    @Getter public String patientRegistry;
    @Getter public String targetDuration;
    @Getter public List<String> phases;
    @Getter public DesignInfo designInfo;
    @Getter public BioSpec bioSpec;
    @Getter public EnrollmentInfo enrollmentInfo;
}

class ExpandedAccessTypes {
    @Getter public String individual;
    @Getter public String intermediate;
    @Getter public String treatment;
}

class DesignInfo {
    @Getter public String allocation;
    @Getter public String interventionModel;
    @Getter public String interventionModelDescription;
    @Getter public String primaryPurpose;
    @Getter public String observationalModel;
    @Getter public String timePerspective;
    @Getter public MaskingInfo maskingInfo;
}

class MaskingInfo {
    @Getter public String masking;
    @Getter public String maskingDescription;
    @Getter public List<String> whoMasked;
}

class BioSpec {
    @Getter public String retention;
    @Getter public String description;
}

class EnrollmentInfo {
    @Getter public String count;
    @Getter public String type;
}

class ArmsInterventionsModule {
    @Getter public List<ArmGroup> armGroups;
    @Getter public List<Intervention> interventions;
}

class ArmGroup {
    @Getter public String label;
    @Getter public String type;
    @Getter public String description;
    @Getter public List<String> interventionNames;
}

class Intervention {
    @Getter public String type;
    @Getter public String name;
    @Getter public String description;
    @Getter public List<String> armGroupLabels;
    @Getter public List<String> otherNames;
}

class OutcomesModule {
    @Getter public List<Outcome> primaryOutcomes;
    @Getter public List<Outcome> secondaryOutcomes;
    @Getter public List<Outcome> otherOutcomes;
}

class Outcome {
    @Getter public String measure;
    @Getter public String description;
    @Getter public String timeFrame;
}

class EligibilityModule {
    @Getter public String eligibilityCriteria;
    @Getter public String healthyVolunteers;
    @Getter public String sex;
    @Getter public String genderBased;
    @Getter public String genderDescription;
    @Getter public String minimumAge;
    @Getter public String maximumAge;
    @Getter public List<String> stdAges;
    @Getter public String studyPopulation;
    @Getter public String samplingMethod;
}

class ContactsLocationsModule {
    @Getter public List<CentralContact> centralContacts;
    @Getter public List<OverallOfficial> overallOfficials;
    @Getter public List<Location> locations;
}

class CentralContact {
    @Getter public String name;
    @Getter public String role;
    @Getter public String phone;
    @Getter public String phoneExt;
    @Getter public String email;
}

class OverallOfficial {
    @Getter public String name;
    @Getter public String affiliation;
    @Getter public String role;
}

class Location {
    @Getter public String facility;
    @Getter public String status;
    @Getter public String city;
    @Getter public String state;
    @Getter public String zip;
    @Getter public String country;
    @Getter public List<LocationContact> contacts;
    @Getter public String geoPoint;
}

class LocationContact {
    @Getter public String name;
    @Getter public String role;
    @Getter public String phone;
    @Getter public String phoneExt;
    @Getter public String email;
}

class ReferencesModule {
    @Getter public List<Reference> references;
    @Getter public List<SeeAlsoLink> seeAlsoLinks;
    @Getter public List<AvailIpd> availIpds;
}

class Reference {
    @Getter public String pmid;
    @Getter public String type;
    @Getter public String citation;
    @Getter public List<Retraction> retractions;
}

class Retraction {
    @Getter public String pmid;
    @Getter public String source;
}

class SeeAlsoLink {
    @Getter public String label;
    @Getter public String url;
}

class AvailIpd {
    @Getter public String id;
    @Getter public String type;
    @Getter public String url;
    @Getter public String comment;
}

class IpdSharingStatementModule {
    @Getter public String ipdSharing;
    @Getter public String description;
    @Getter public List<String> infoTypes;
    @Getter public String timeFrame;
    @Getter public String accessCriteria;
    @Getter public String url;
}

/* ResultsSection */

class ResultsSection {
    @Getter public ParticipantFlowModule participantFlowModule;
    @Getter public BaselineCharacteristicsModule baselineCharacteristicsModule;
    @Getter public OutcomeMeasuresModule outcomeMeasuresModule;
    @Getter public AdverseEventsModule adverseEventsModule;
    @Getter public MoreInfoModule moreInfoModule;
}

class ParticipantFlowModule {
    @Getter public String preAssignmentDetails;
    @Getter public String recruitmentDetails;
    @Getter public String typeUnitsAnalyzed;
    @Getter public List<ResultGroup> groups;
    @Getter public List<Period> periods;
}

class ResultGroup {
    @Getter public String id;
    @Getter public String title;
    @Getter public String description;
}

class Period {
    @Getter public String title;
    @Getter public List<Milestone> milestones;
    @Getter public List<DropWithdraw> dropWithdraws;
}

class Milestone {
    @Getter public String type;
    @Getter public String comment;
    @Getter public List<Achievement> achievements;
}

class Achievement {
    @Getter public String groupId;
    @Getter public String comment;
    @Getter public String numSubjects;
    @Getter public String numUnits;
}

class DropWithdraw {
    @Getter public String type;
    @Getter public String comment;
    @Getter public List<Reason> reasons;
}

class Reason {
    @Getter public String groupId;
    @Getter public String comment;
    @Getter public String numSubjects;
}

class BaselineCharacteristicsModule {
    @Getter public String populationDescription;
    @Getter public String typeUnitsAnalyzed;
    @Getter public List<ResultGroup> groups;
    @Getter public List<Denom> denoms;
    @Getter public List<Measure> measures;
}

class Denom {
    @Getter public String units;
    @Getter public List<Count> counts;
}

class Count {
    @Getter public String groupId;
    @Getter public String value;
}

class Measure {
    @Getter public String title;
    @Getter public String description;
    @Getter public String populationDescription;
    @Getter public String paramType;
    @Getter public String dispersionType;
    @Getter public String unitOfMeasure;
    @Getter public String calculatePct;
    @Getter public String denomUnitsSelected;
    @Getter public List<Denom> denoms;
    @Getter public List<MeasureClass> classes;
}

class MeasureClass {
    @Getter public String title;
    @Getter public List<Denom> denoms;
    @Getter public List<Category> categories;
}

class Category {
    @Getter public String title;
    @Getter public List<Measurement> measurements;
}

class Measurement {
    @Getter public String groupId;
    @Getter public String value;
    @Getter public String spread;
    @Getter public String lowerLimit;
    @Getter public String upperLimit;
    @Getter public String comment;
}

class OutcomeMeasuresModule {
    @Getter public List<OutcomeMeasure> outcomeMeasures;
}

class OutcomeMeasure {
    @Getter public String type;
    @Getter public String title;
    @Getter public String description;
    @Getter public String populationDescription;
    @Getter public String reportingStatus;
    @Getter public String anticipatedPostingDate;
    @Getter public String paramType;
    @Getter public String dispersionType;
    @Getter public String unitOfMeasure;
    @Getter public String calculatePct;
    @Getter public String timeFrame;
    @Getter public String typeUnitsAnalyzed;
    @Getter public String denomUnitsSelected;
    @Getter public List<ResultGroup> groups;
    @Getter public List<Denom> denoms;
    @Getter public List<MeasureClass> classes;
    @Getter public List<Analysis> analyses;
}

class Analysis {
    @Getter public String paramType;
    @Getter public String paramValue;
    @Getter public String dispersionType;
    @Getter public String dispersionValue;
    @Getter public String statisticalMethod;
    @Getter public String statisticalComment;
    @Getter public String pValue;
    @Getter public String pValueComment;
    @Getter public String ciNumSides;
    @Getter public String ciPctValue;
    @Getter public String ciLowerLimit;
    @Getter public String ciUpperLimit;
    @Getter public String ciLowerLimitComment;
    @Getter public String ciUpperLimitComment;
    @Getter public String estimateComment;
    @Getter public String testedNonInferiority;
    @Getter public String nonInferiorityType;
    @Getter public String nonInferiorityComment;
    @Getter public String otherAnalysisDescription;
    @Getter public String groupDescription;
    @Getter public List<String> groupIds;
}

class AdverseEventsModule {
    @Getter public String frequencyThreshold;
    @Getter public String timeFrame;
    @Getter public String description;
    @Getter public String allCauseMortalityComment;
    @Getter public List<EventGroup> eventGroups;
    @Getter public List<Event> seriousEvents;
    @Getter public List<Event> otherEvents;
}

class EventGroup {
    @Getter public String id;
    @Getter public String title;
    @Getter public String description;
    @Getter public String deathsNumAffected;
    @Getter public String deathsNumAtRisk;
    @Getter public String seriousNumAffected;
    @Getter public String seriousNumAtRisk;
    @Getter public String otherNumAffected;
    @Getter public String otherNumAtRisk;
}

class Event {
    @Getter public String term;
    @Getter public String organSystem;
    @Getter public String sourceVocabulary;
    @Getter public String assessmentType;
    @Getter public String notes;
    @Getter public List<EventStat> stats;
}

class EventStat {
    @Getter public String groupId;
    @Getter public String numEvents;
    @Getter public String numAffected;
    @Getter public String numAtRisk;
}

class MoreInfoModule {
    @Getter public LimitationsAndCaveats limitationsAndCaveats;
    @Getter public CertainAgreement certainAgreement;
    @Getter public PointOfContact pointOfContact;
}

class LimitationsAndCaveats {
    @Getter public String description;
}

class CertainAgreement {
    @Getter public String piSponsorEmployee;
    @Getter public String restrictionType;
    @Getter public String restrictiveAgreement;
    @Getter public String otherDetails;
}

class PointOfContact {
    @Getter public String title;
    @Getter public String organization;
    @Getter public String email;
    @Getter public String phone;
    @Getter public String phoneExt;
}

/* AnnotationSection */

class AnnotationSection {
    @Getter public AnnotationModule annotationModule;
}

class AnnotationModule {
    @Getter public UnpostedAnnotation unpostedAnnotation;
    @Getter public ViolationAnnotation violationAnnotation;
}

class UnpostedAnnotation {
    @Getter public String unpostedResponsibleParty;
    @Getter public List<UnpostedEvent> unpostedEvents;
}

class UnpostedEvent {
    @Getter public String type;
    @Getter public String date;
    @Getter public String dateUnknown;
}

class ViolationAnnotation {
    @Getter public List<ViolationEvent> violationEvents;
}

class ViolationEvent {
    @Getter public String type;
    @Getter public String description;
    @Getter public String creationDate;
    @Getter public String issuedDate;
    @Getter public String releaseDate;
    @Getter public String postedDate;
}

/* DocumentSection */

class DocumentSection {
    @Getter public LargeDocumentModule largeDocumentModule;
}

class LargeDocumentModule {
    @Getter public String noSap;
    @Getter public List<LargeDoc> largeDocs;
}

class LargeDoc {
    @Getter public String typeAbbrev;
    @Getter public boolean hasProtocol;
    @Getter public boolean hasSap;
    @Getter public boolean hasIcf;
    @Getter public String label;
    @Getter public String date;
    @Getter public String uploadDate;
    @Getter public String filename;
    @Getter public Long size;
}

/* DerivedSection */

class DerivedSection {
    @Getter public MiscInfoModule miscInfoModule;
    @Getter public ConditionBrowseModule conditionBrowseModule;
    @Getter public InterventionBrowseModule interventionBrowseModule;
}

class MiscInfoModule {
    @Getter public String versionHolder;
    @Getter public List<String> removedCountries;
    @Getter public SubmissionTracking submissionTracking;
}

class SubmissionTracking {
    @Getter public String estimatedResultsFirstSubmitDate;
    @Getter public FirstMcpInfo firstMcpInfo;
    @Getter public List<SubmissionInfo> submissionInfos;
}

class FirstMcpInfo {
    @Getter public DateStruct postDateStruct;
}

class SubmissionInfo {
    @Getter public String releaseDate;
    @Getter public String unreleaseDate;
    @Getter public String unreleaseDateUnknown;
    @Getter public String resetDate;
    @Getter public String mcpReleaseN;
}

class ConditionBrowseModule {
    @Getter public List<Mesh> meshes;
    @Getter public List<Mesh> ancestors;
    @Getter public List<BrowseLeaf> browseLeaves;
    @Getter public List<BrowseBranch> browseBranches;
}

class Mesh {
    @Getter public String id;
    @Getter public String term;
}

class BrowseLeaf {
    @Getter public String id;
    @Getter public String name;
    @Getter public String asFound;
    @Getter public String relevance;
}

class BrowseBranch {
    @Getter public String abbrev;
    @Getter public String name;
}

class InterventionBrowseModule {
    @Getter public List<Mesh> meshes;
    @Getter public List<Mesh> ancestors;
    @Getter public List<BrowseLeaf> browseLeaves;
    @Getter public List<BrowseBranch> browseBranches;
}
