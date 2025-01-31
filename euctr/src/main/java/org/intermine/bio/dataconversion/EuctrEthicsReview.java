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
public class EuctrEthicsReview {
    private String status;
    @JacksonXmlProperty(localName = "approval_date")
    private String approvalDate;
    @JacksonXmlProperty(localName = "contact_name")
    private String contactName;
    @JacksonXmlProperty(localName = "contact_address")
    private String contactAddress;
    @JacksonXmlProperty(localName = "contact_phone")
    private String contactPhone;
    @JacksonXmlProperty(localName = "contact_email")
    private String contactEmail;
}