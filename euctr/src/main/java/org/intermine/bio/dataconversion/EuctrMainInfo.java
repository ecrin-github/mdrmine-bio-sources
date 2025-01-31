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
    private String reg_name;
    private String date_registration;
    private String primary_sponsor;
    @JacksonXmlProperty(localName = "public_title")
    private String publicTitle;
    private String acronym;
    private String scientific_title;
    private String scientific_acronym;
    private String date_enrolment;
    private String type_enrolment;
    private String target_size;
    private String recruitment_status;
    private String url;
    private String study_type;
    private String study_design;
    private String phase;
    private String hc_freetext;
    private String i_freetext;
    private String results_actual_enrolment;
    private String results_date_completed;
    private String results_url_link;
    private String results_summary;
    private String results_date_posted;
    private String results_date_first_publication;
    private String results_baseline_char;
    private String results_participant_flow;
    private String results_adverse_events;
    private String results_outcome_measures;
    private String results_url_protocol;
    private String results_IPD_plan;
    private String results_IPD_description;
}