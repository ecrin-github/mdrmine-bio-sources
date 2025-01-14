package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024 MDRMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.time.format.DateTimeFormatter;
import java.time.DateTimeException;
import java.time.LocalDate;

import org.intermine.xml.full.Attribute;
import org.intermine.xml.full.Item;

import org.jsoup.Jsoup;



/**
 * Class with utility functions for converter classes
 * @author
 */
public class ConverterUtils
{
    /**
     * Check if a string is null, empty, only contains whitespaces, or is equal to "NULL".
     * 
     * @return true if null or empty or only contains whitespaces or is equal to "NULL", false otherwise
     */
    public static boolean isNullOrEmptyOrBlank(String s) {
        return (s == null || s.isEmpty() || s.equalsIgnoreCase("NULL") || s.isBlank());
    }

    /**
     * TODO
     */
    public static LocalDate getDateFromString(String dateStr, DateTimeFormatter dateFormatter) {
        LocalDate parsedDate = null;
        try {
            if (dateFormatter != null) {
                parsedDate = LocalDate.parse(dateStr, dateFormatter);
            } else {
                // ISO date format parsing
                parsedDate = LocalDate.parse(dateStr);
            }
        } catch(DateTimeException e) {
            ;
        }
        return parsedDate;
    }

    /**
     * Normalise word and add trailing s to unit.
     * 
     * @param u the unit to normalise
     * @return the normalised unit
     * @see #normaliseWord()
     */
    public static String normaliseUnit(String u) {
        return ConverterUtils.normaliseWord(u) + "s";
    }

    /**
     * Uppercase first letter and lowercase the rest.
     * 
     * @param w the word to normalise
     * @return the normalised word
     */
    public static String normaliseWord(String w) {
        if (w.length() > 0) {
            w = w.substring(0,1).toUpperCase() + w.substring(1).toLowerCase();
        }
        return w;
    }
    
    /**
     * TODO
     */
    public static String getYearFromISODateString(String dateStr) {
        String year = null;

        LocalDate parsedDate = ConverterUtils.getDateFromString(dateStr, null);
        if (parsedDate != null) {
            year = String.valueOf(parsedDate.getYear());
        }

        return year;
    }

    /**
     * TODO
     */
    public static String getDisplayTitleFromStudy(Item study) {
        String studyDisplayTitle = null;
        Attribute studyDisplayTitleAttr = study.getAttribute("displayTitle");
        if (studyDisplayTitleAttr != null) {
            studyDisplayTitle = studyDisplayTitleAttr.getValue();
        }
        return studyDisplayTitle;
    }

    /**
     * Unescape HTML4 characters.
     * 
     * @param s the string potentially containing escaped HTML4 characters
     * @return the unescaped string
     */
    public static String unescapeHtml(String s) {
        return Jsoup.parse(s).text();
    }

    /**
     * Remove leading and trailing double quotes from a string.
     * WHO Note: unfortunately opencsv only transforms triple double-quoted values into single double-quoted values, 
     * so we have to remove the remaining quotes manually.
     * 
     * @param s the string to remove quotes from
     * @return the string without leading and trailing double quotes
     */
    public static String removeQuotes(String s) {
        if (s != null && s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"') {
            return s.substring(1, s.length()-1);
        }
        return s;
    }

    /**
     * Test if a string is a positive whole number.
     * 
     * @param s the string to test
     * @return true if string is a positive whole number, false otherwise
     */
    public static boolean isPosWholeNumber(String s) {
        if (s.length() == 0) { return false; }
        
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                continue;
            }
            return false;
        }

        return true;
    }
}
