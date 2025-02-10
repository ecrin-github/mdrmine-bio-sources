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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EuctrMainInfo {
    @JacksonXmlProperty(localName = "trial_id")
    private String trialId;
    private String utrn;
    @JacksonXmlProperty(localName = "reg_name")
    private String regName;
    @JacksonXmlProperty(localName = "date_registration")
    private String dateRegistration;
    @JacksonXmlProperty(localName = "primary_sponsor")
    private String primarySponsor;
    @JacksonXmlProperty(localName = "public_title")
    private String publicTitle;
    private String acronym;
    @JacksonXmlProperty(localName = "scientific_title")
    private String scientificTitle;
    @JacksonXmlProperty(localName = "scientific_acronym")
    private String scientificAcronym;
    @JacksonXmlProperty(localName = "date_enrolment")
    private String dateEnrolment;
    @JacksonXmlProperty(localName = "type_enrolment")
    private String typeEnrolment;
    @JacksonXmlProperty(localName = "target_size")
    private String targetSize;
    @JacksonXmlProperty(localName = "recruitment_status")
    private String recruitmentStatus;
    @JacksonXmlProperty(localName = "url")
    private String url;
    @JacksonXmlProperty(localName = "study_type")
    private String studyType;
    @JacksonXmlProperty(localName = "study_design")
    private String studyDesign;
    private String phase;
    @JacksonXmlProperty(localName = "hc_freetext")
    private String hcFreetext;
    @JacksonXmlProperty(localName = "i_freetext")
    private String iFreetext;
    @JacksonXmlProperty(localName = "results_actual_enrolment")
    private String resultsActualEnrolment;
    @JacksonXmlProperty(localName = "results_date_completed")
    private String resultsDateCompleted;
    @JacksonXmlProperty(localName = "results_url_link")
    private String resultsUrlLink;
    @JacksonXmlProperty(localName = "results_summary")
    private String resultsSummary;
    @JacksonXmlProperty(localName = "results_date_posted")
    private String resultsDatePosted;
    @JacksonXmlProperty(localName = "results_date_first_publication")
    private String resultsDateFirst_Publication;
    @JacksonXmlProperty(localName = "results_baseline_char")
    private String resultsBaselineChar;
    @JacksonXmlProperty(localName = "results_participant_flow")
    private String resultsParticipantFlow;
    @JacksonXmlProperty(localName = "results_adverse_events")
    private String resultsAdverseEvents;
    @JacksonXmlProperty(localName = "results_outcome_measures")
    private String resultsOutcomeMeasures;
    @JacksonXmlProperty(localName = "results_url_protocol")
    private String resultsUrlProtocol;
    @JacksonXmlProperty(localName = "results_IPD_plan")
    private String resultsIPDPlan;
    @JacksonXmlProperty(localName = "results_IPD_description")
    private String resultsIPDDescription;
}