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

 import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;





@Getter
@Setter
@JacksonXmlRootElement
@NoArgsConstructor
@AllArgsConstructor
public class EuctrTrial {
    @JacksonXmlProperty(localName = "main")
    private EuctrMainInfo mainInfo;

    @JacksonXmlProperty(localName = "contact")
    @JacksonXmlElementWrapper(localName = "contacts")
    private List<EuctrContact> contacts = new ArrayList<EuctrContact>();

    @JacksonXmlElementWrapper(localName = "countries")
    @JacksonXmlProperty(localName = "country2")
    private List<String> countries = new ArrayList<String>();

    @JacksonXmlProperty(localName = "criteria")
    private EuctrCriteria criteria;

    // Note: normally not a list
    @JacksonXmlElementWrapper(localName = "health_condition_code")
    @JacksonXmlProperty(localName = "hc_code")
    private List<String> healthConditionCodes = new ArrayList<String>();

    @JacksonXmlElementWrapper(localName = "health_condition_keyword")
    @JacksonXmlProperty(localName = "hc_keyword")
    private List<String> healthConditionKeywords = new ArrayList<String>();

    // Note: always empty?
    @JacksonXmlElementWrapper(localName = "intervention_code")
    @JacksonXmlProperty(localName = "i_code")
    private List<String> interventionCodes = new ArrayList<String>();

    // Note: always empty?
    @JacksonXmlElementWrapper(localName = "intervention_keyword")
    @JacksonXmlProperty(localName = "i_keyword")
    private List<String> interventionKeywords = new ArrayList<String>();

    @JacksonXmlElementWrapper(localName = "primary_outcome")
    @JacksonXmlProperty(localName = "prim_outcome")
    private List<String> primaryOutcomes = new ArrayList<String>();

    @JacksonXmlElementWrapper(localName = "secondary_outcome")
    @JacksonXmlProperty(localName = "sec_outcome")
    private List<String> secondaryOutcomes = new ArrayList<String>();

    @JacksonXmlElementWrapper(localName = "secondary_sponsor")
    @JacksonXmlProperty(localName = "sponsor_name")
    private List<String> secondarySponsors = new ArrayList<String>();

    @JacksonXmlElementWrapper(localName = "secondary_ids")
    @JacksonXmlProperty(localName = "secondary_id")
    private List<EuctrSecondaryId> secondaryIds = new ArrayList<EuctrSecondaryId>();

    @JacksonXmlElementWrapper(localName = "source_support")
    @JacksonXmlProperty(localName = "source_name")
    private List<String> sourceSupport = new ArrayList<String>();

    @JacksonXmlElementWrapper(localName = "ethics_reviews")
    @JacksonXmlProperty(localName = "ethics_review")
    private List<EuctrEthicsReview> ethicsReviews = new ArrayList<EuctrEthicsReview>();
}