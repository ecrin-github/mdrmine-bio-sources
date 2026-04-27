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

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;

import org.apache.commons.text.WordUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.intermine.xml.full.Attribute;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.Reference;
import org.jsoup.Jsoup;

/**
 * Class with utility functions for converter classes
 * 
 * @author
 */
public class ConverterUtils {
    public static final Map<String, String> PHASE_NUMBER_MAP = Map.of(
            "1", "1",
            "2", "2",
            "3", "3",
            "4", "4",
            "i", "1",
            "ii", "2",
            "iii", "3",
            "iv", "4");
    public static final DateTimeFormatter P_DATE_D_M_Y_SLASHES = DateTimeFormatter.ofPattern("d/M/uuuu");
    public static final DateTimeFormatter P_DATE_D_MWORD_Y_SPACES = DateTimeFormatter.ofPattern("d MMMM uuuu");
    public static final DateTimeFormatter P_DATE_MWORD_D_Y_HOUR = DateTimeFormatter.ofPattern("MMM d uuuu hh:mma");
    public static final DateTimeFormatter P_DATE_M_D_Y_TIME = DateTimeFormatter.ofPattern("M/d/uuuu hh:mm:ss");

    /*
     * Regex to Java converter: https://www.regexplanet.com/advanced/java/index.html
     */
    public static final Pattern P_PUBMED_ID = Pattern.compile(".*pubmed.*\\/([^?\\/]+).*");
    public static final Pattern P_ID_AT_END_OF_URL = Pattern.compile(".*\\/([^?\\/]+).*");
    // Both EUCTR and CTIS (they might have the same format)
    public static final Pattern P_EU_ID = Pattern
            .compile("(?:(CTIS)|(EUCTR))?(\\d{4}-\\d{6}-\\d{2})(?:-(\\d{2})|-(.*))?");
    public static final Pattern P_NCT_ID = Pattern.compile("NCT\\d{8}");
    public static final Pattern P_WHO_ID = Pattern.compile("U\\d{4}-\\d{4}-\\d{4}");
    public static final Pattern P_EMAIL = Pattern.compile("^[^@]+@[^.]+\\..+");
    // public static final Pattern P_ANZCTR_ID = Pattern.compile();
    // public static final Pattern P_CHICTR_ID = Pattern.compile();
    // public static final Pattern P_CRIS_ID = Pattern.compile();
    // public static final Pattern P_CTRI_ID = Pattern.compile();
    // public static final Pattern P_DRKS_ID = Pattern.compile();
    // public static final Pattern P_IRCT_ID = Pattern.compile();
    // public static final Pattern P_ISRCTN_ID = Pattern.compile();
    // public static final Pattern P_ITMCTR_ID = Pattern.compile();
    // public static final Pattern P_JRCT_ID = Pattern.compile();
    // public static final Pattern P_LBCTR_ID = Pattern.compile();
    // public static final Pattern P_TCTR_ID = Pattern.compile();
    // public static final Pattern P_PACTR_ID = Pattern.compile();
    // public static final Pattern P_REBEC_ID = Pattern.compile();
    // public static final Pattern P_REPEC_ID = Pattern.compile();
    // public static final Pattern P_RPCEC_ID = Pattern.compile();
    // public static final Pattern P_SLCTR_ID = Pattern.compile();

    /**
     * Check if a string is null, empty (after trim), or is equal to
     * "NULL".
     * 
     * @return true if null or empty or only contains whitespaces or is equal to
     *         "NULL", false otherwise
     */
    public static boolean isBlankOrNull(String s) {
        return (s == null || s.trim().isEmpty() || s.equalsIgnoreCase("NULL"));
    }

    /**
     * TODO
     * https://stackoverflow.com/a/43133958
     * 
     * @param string
     * @param numLines
     * @return
     */
    public static List<String> getLastLines(String string, int numLines) {
        List<String> lines = new ArrayList<>();
        int currentEndOfLine = string.length();
        if (string.endsWith("\n")) {
            currentEndOfLine = currentEndOfLine - "\n".length();
        }
        for (int i = 0; i < numLines; ++i) {
            int lastEndOfLine = currentEndOfLine;
            // lastIndexOf starts looking backwards from given index
            currentEndOfLine = string.lastIndexOf("\n", lastEndOfLine - 1);
            if (currentEndOfLine != -1) {
                String lastLine = string.substring(currentEndOfLine + 1, lastEndOfLine);
                lines.add(0, lastLine);
            } else {
                break;
            }
        }
        return lines;
    }

    /**
     * TODO
     * 
     * @param dateStr
     * @param dateFormatter
     * @return
     */
    public static LocalDate getDateFromString(String dateStr, DateTimeFormatter dateFormatter) {
        LocalDate parsedDate = null;
        if (!ConverterUtils.isBlankOrNull(dateStr)) {
            try {
                if (dateFormatter != null) {
                    parsedDate = LocalDate.parse(dateStr, dateFormatter);
                } else {
                    // ISO date format parsing
                    parsedDate = LocalDate.parse(dateStr);
                }
            } catch (DateTimeException e) {
                ;
            }
        }
        return parsedDate;
    }

    public static boolean booleanFromString(String boolStr) throws ParseException {
        if (!ConverterUtils.isBlankOrNull(boolStr)) {
            if (boolStr.equalsIgnoreCase("true") || boolStr.equalsIgnoreCase("false")) {
                return true;
            } else {
                throw new ParseException("Unexpected value in string to convert to boolean: " + boolStr, 0);
            }
        }

        return false;
    }

    public static boolean isYes(String s) {
        if (!ConverterUtils.isBlankOrNull(s) && s.equalsIgnoreCase("yes")) {
            return true;
        }
        return false;
    }

    public static String booleanToString(Boolean b) {
        String bs = null;
        if (b != null) {
            bs = b.toString();
        }
        return bs;
    }

    /**
     * Normalise word and add trailing s to unit.
     * 
     * @param u the unit to normalise
     * @return the normalised unit
     * @see #capitaliseFirstLetter()
     */
    public static String normaliseUnit(String u) {
        if (u.endsWith("s")) {
            return ConverterUtils.capitaliseFirstLetter(u, true);
        }
        return ConverterUtils.capitaliseFirstLetter(u, true) + "s";
    }

    /**
     * TODO
     */
    public static String normaliseStatus(String s) {
        if (s != null) {
            s = ConverterUtils.capitaliseFirstLetter(s.replace('_', ' '), true);
        }
        return s;
    }

    /**
     * TODO
     */
    public static String normaliseCondition(String c) {
        if (c != null) {
            return WordUtils.capitalizeFully(c.strip(), ' ', '-');
        }
        return c;
    }

    /**
     * TODO
     */
    public static String normaliseIntervention(String i) {
        if (i != null) {
            return WordUtils.capitalizeFully(i.strip(), ' ', '-');
        }
        return i;
    }

    /**
     * Uppercase first letter and lowercase the rest or not.
     * 
     * @param s the string to normalise
     * @param restToLowercase whether to convert to lowercase all characters after the first
     * @return the normalised string
     */
    public static String capitaliseFirstLetter(String s, boolean restToLowercase) {
        if (s.length() > 0) {
            if (restToLowercase) {
                s = s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
            } else {
                s = s.substring(0, 1).toUpperCase() + s.substring(1);
            }
        }
        return s;
    }

    /**
     * TODO
     * 
     * @param dateStr
     * @return
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
     * 
     * @param item
     * @param attrName
     * @return
     */
    public static String getAttrValue(Item item, String attrName) {
        String attrValue = null;
        if (item != null) {
            Attribute itemAttr = item.getAttribute(attrName);
            if (itemAttr != null) {
                attrValue = itemAttr.getValue();
            }
        }
        return attrValue;
    }

    /**
     * TODO
     * 
     * @param item
     * @param refName
     * @return
     */
    public static String getRefId(Item item, String refName) {
        String refId = null;
        if (item != null) {
            Reference itemRef = item.getReference(refName);
            if (itemRef != null) {
                refId = itemRef.getRefId();
            }
        }
        return refId;
    }

    /**
     * Concatenate text on a new line to study description field value.
     * 
     * @param study the study item to modify the description field of
     * @param text  the text to concatenate (or set, if the field's value is empty)
     *              to the study's description
     */
    public static void addToDescription(Item study, String text) {
        if (!ConverterUtils.isBlankOrNull(text)) {
            Attribute description = study.getAttribute("description");
            if (description != null) {
                String currentDesc = description.getValue();
                if (!ConverterUtils.isBlankOrNull(currentDesc)) {
                    study.setAttribute("description", currentDesc + "\n" + text);
                } else {
                    study.setAttribute("description", text);
                }
            }
        }
    }

    /**
     * Convert phase number (1-4) to digit string. Only returns a different string
     * if the input is in Roman numerals.
     * 
     * @param n the input digit string, possibly in roman numerals
     * @return the converted phase number
     */
    public static String convertPhaseNumber(String n) {
        return ConverterUtils.PHASE_NUMBER_MAP.get(n.toLowerCase());
    }

    /**
     * TODO
     */
    public static String constructMultiplePhasesString(String p1, String p2) {
        return ConverterUtils.convertPhaseNumber(p1) + "/" + ConverterUtils.convertPhaseNumber(p2);
    }

    public static String getAgeGroupStr(EnumSet<ConverterCVT.AgeGroup> ageGroups) {
        ArrayList<String> selectedGroups = new ArrayList<String>();

        if (ageGroups.contains(ConverterCVT.AgeGroup.InUtero)) {
            selectedGroups.add(ConverterCVT.AGE_GROUP_IN_UTERO);
        }
        if (ageGroups.contains(ConverterCVT.AgeGroup.Pediatric)) {
            selectedGroups.add(ConverterCVT.AGE_GROUP_PEDIATRIC);
        }
        if (ageGroups.contains(ConverterCVT.AgeGroup.Adult)) {
            selectedGroups.add(ConverterCVT.AGE_GROUP_ADULT);
        }
        if (ageGroups.contains(ConverterCVT.AgeGroup.OlderAdult)) {
            selectedGroups.add(ConverterCVT.AGE_GROUP_OLDER_ADULT);
        }

        return String.join(", ", selectedGroups);
    }

    /**
     * TODO
     * Note: "In utero" age group edge case is only present and therefore only handled in CTIS
     */
    public static String calculateAgeGroup(Item study) {
        EnumSet<ConverterCVT.AgeGroup> ageGroups = EnumSet.noneOf(ConverterCVT.AgeGroup.class);

        String minAge = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MIN_AGE);
        String minAgeUnit = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MIN_AGE_UNIT);
        String maxAge = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MAX_AGE);
        String maxAgeUnit = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MAX_AGE_UNIT);

        // Checking min age
        if (!ConverterUtils.isBlankOrNull(minAge) && !ConverterUtils.isBlankOrNull(minAgeUnit)) {
            if (!minAgeUnit.equals(ConverterCVT.AGE_UNIT_YEARS)) {  // If unit is not years, means it's a smaller unit
                ageGroups.add(ConverterCVT.AgeGroup.Pediatric);
            } else {
                if (NumberUtils.isParsable(minAge)) {
                    Float minAgeF = Float.parseFloat(minAge);
                    if (minAgeF < 18) {
                        ageGroups.add(ConverterCVT.AgeGroup.Pediatric);
                    } else if (minAgeF < 65) {
                        ageGroups.add(ConverterCVT.AgeGroup.Adult);
                    } else {
                        ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);
                    }
                } else {
                    // TODO: write log?
                }
            }

            // Checking max age
            if (!ConverterUtils.isBlankOrNull(maxAge) && !ConverterUtils.isBlankOrNull(maxAgeUnit)) {
                // If unit is not years it's a smaller unit, and child age group has already been added with min age
                if (maxAgeUnit.equals(ConverterCVT.AGE_UNIT_YEARS)) {
                    if (NumberUtils.isParsable(maxAge)) {
                        Float maxAgeF = Float.parseFloat(maxAge);
                        if (maxAgeF >= 18 && maxAgeF < 65) {
                            ageGroups.add(ConverterCVT.AgeGroup.Adult);
                        } else if (maxAgeF >= 65) {
                            ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);

                            // If minAge is of child age group and max age of older adult age group, need to add adult age group as well
                            if (ageGroups.contains(ConverterCVT.AgeGroup.Pediatric)) {
                                ageGroups.add(ConverterCVT.AgeGroup.Adult);
                            }
                        }
                    } else {
                        // TODO: write log?
                    }
                }
            } else {    // No max age, adding all groups older than the one added with minAge
                if (ageGroups.contains(ConverterCVT.AgeGroup.Pediatric)) {
                    ageGroups.add(ConverterCVT.AgeGroup.Adult);
                    ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);
                } else if (ageGroups.contains(ConverterCVT.AgeGroup.Pediatric)) {
                    ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);
                }   // Else OLDER_ADULT, nothing to add
            }
        } else {    // Checking max age with no min age
            if (!ConverterUtils.isBlankOrNull(maxAge) && !ConverterUtils.isBlankOrNull(maxAgeUnit)) {
                ageGroups.add(ConverterCVT.AgeGroup.Pediatric); // No min age, adding child age group in any case
                if (maxAgeUnit.equals(ConverterCVT.AGE_UNIT_YEARS)) {
                    if (NumberUtils.isParsable(maxAge)) {
                        Float maxAgeF = Float.parseFloat(maxAge);
                        if (maxAgeF >= 18) {
                            ageGroups.add(ConverterCVT.AgeGroup.Adult);

                            if (maxAgeF >= 65) {
                                ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);
                            }
                        }
                    } else {
                        // TODO: write log?
                    }
                }
            }
        }

        return ConverterUtils.getAgeGroupStr(ageGroups);
    }

    /**
     * TODO
     * Get title from a study item if there is any (publicTitle or scientificTitle or acronym)
     */
    public static String getStudyTitle(Item study) {
        String title = null;

        title = ConverterUtils.getAttrValue(study, "publicTitle");
        if (ConverterUtils.isBlankOrNull(title)) {
            title = ConverterUtils.getAttrValue(study, "scientificTitle");
            if (ConverterUtils.isBlankOrNull(title)) {
                title = ConverterUtils.getAttrValue(study, "acronym");
            }
        }

        return title;
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
     * WHO Note: unfortunately opencsv only transforms triple double-quoted values
     * into single double-quoted values,
     * so we have to remove the remaining quotes manually.
     * 
     * @param s the string to remove quotes from
     * @return the string without leading and trailing double quotes
     */
    public static String removeQuotes(String s) {
        if (s != null && s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
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
        if (s == null || s.length() == 0) {
            return false;
        }

        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                continue;
            }
            return false;
        }

        return true;
    }

    /**
     * TODO
     * 
     * @param str
     * @param charToReplace
     * @return
     */
    public static String capitaliseAndReplaceCharBySpace(final String str, final char charToReplace) {
        if (ConverterUtils.isBlankOrNull(str)) {
            return str;
        }
        String capitalised = WordUtils.capitalizeFully(str);
        return capitalised.replace(charToReplace, ' ');
    }

    /**
     * TODO
     * check P_EMAIL pattern, "weak" email string validation: [any]@[any].[any]
     */
    public static String filterNonEmailString(final String email) {
        if (!ConverterUtils.isBlankOrNull(email) && P_EMAIL.matcher(email).matches()) {
            return email;
        }
        return null;
    }
}
