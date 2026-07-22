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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.xml.full.Attribute;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.Reference;
import org.intermine.xml.full.ReferenceList;
import org.jsoup.Jsoup;

import com.alibaba.fastjson2.JSON;

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
    public static final Set<String> dummyIDs = Set.of("NCT00000000", "NCT12345678", "ISRCTN00000000", "ISRCTN12345678",
            "U0000-0000-0000", "U1234-1234-1234");
    public static final Pattern P_ANZCTR_ID = Pattern.compile(".*ACTRN(?:\\s|0)?(\\d{14}).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_CHICTR_ID = Pattern.compile(".*(Chi(M)?CTR[^\\s,;]+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_CRIS_ID = Pattern.compile(".*\\b(KCT\\d+)[\\s]*$", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_CTRI_ID = Pattern.compile(".*(CTRI\\/\\d{4}\\/\\d{2,3}\\/\\d+).*",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern P_DRKS_ID = Pattern.compile(".*DRKS.*(\\d{8}).*", Pattern.CASE_INSENSITIVE);
    // Both EUCTR and CTIS (they might have the same format)
    public static final Pattern P_EU_ID = Pattern
            .compile(".*(?:(CTIS)|(EUCTR))?(\\d{4}-\\d{6}-\\d{2})(?:-(\\d{2})|-(.*))?.*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_IRCT_ID = Pattern.compile(".*(IRCT\\d+N\\d+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_ISRCTN_ID = Pattern.compile(".*ISRCTN.*(\\d{8}).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_ITMCTR_ID = Pattern.compile("ITMCTR\\d+", Pattern.CASE_INSENSITIVE);
    // Includes UMIN, jRCT and all JPRN IDs
    public static final Pattern P_JPRN_ID = Pattern.compile(".*((?:UMIN|jRCTs?)\\d+)|(JPRN-(?!JPRN)[^\\s]*).*",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern P_LBCTR_ID = Pattern.compile("LBCTR\\d+", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_NCT_ID = Pattern.compile(".*(NCT\\d{8}).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_NTR_ID = Pattern.compile(".*\\b(NTR|NL)[\\s:]*?(\\d+)\\b.*",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern P_OMON_ID = Pattern.compile(".*\\b(OMON\\d{5})\\b.*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_PACTR_ID = Pattern.compile(".*(PACTR\\s*\\d+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_REBEC_ID = Pattern.compile("^(RBR-\\w+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_REPEC_ID = Pattern.compile(".*\\b(PER-\\d+-\\d+(?:-[A-Z])?)",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern P_RPCEC_ID = Pattern.compile("RPCEC\\d+", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_SLCTR_ID = Pattern.compile(".*(SLCTR\\/\\d{4}\\/\\d+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_SNCTP_ID = Pattern.compile(".*(SNCTP\\d+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_TCTR_ID = Pattern.compile(".*\\b(TCTR\\d+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_UTN_ID = Pattern.compile(".*(U\\d{4}-\\d{4}-\\d{4}).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern P_PUBMED_ID = Pattern.compile(".*pubmed.*\\/([^?\\/]+).*");
    public static final Pattern P_ID_AT_END_OF_URL = Pattern.compile(".*\\/([^?\\/]+).*");
    public static final Pattern P_ID_GARBAGE = Pattern.compile(
            "(?:[^\\w\\n]+)?(?:ND|no|[-\\/.]|ooo|N[\\/.]?A\\.?|NIL.*|NONE.*|NOT\\s+.*|.*aangevraagd.*)",
            Pattern.CASE_INSENSITIVE);
    // TODO: SNCTP
    public static final List<Pattern> REGISTRY_ID_PATTERNS = Arrays.asList(
            P_NCT_ID, P_EU_ID, P_UTN_ID, P_ISRCTN_ID, P_ANZCTR_ID, P_CTRI_ID, P_DRKS_ID, P_JPRN_ID, P_REBEC_ID,
            P_CHICTR_ID, P_CRIS_ID, P_RPCEC_ID, P_IRCT_ID, P_PACTR_ID, P_REPEC_ID, P_LBCTR_ID, P_SLCTR_ID, P_TCTR_ID,
            P_ITMCTR_ID, P_SNCTP_ID, P_NTR_ID, P_OMON_ID);

    public static final Pattern P_EMAIL = Pattern.compile("^[^@]+@[^.]+\\..+");

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
     */
    public static ClassDescriptor getClassDescriptor(Model model, Item item) {
        if (item == null) {
            System.err.println("Error: called getClassDescriptor() with a null item");
            return null;
        }
        return model.getClassDescriptorByName(item.getClassName());
    }

    /**
     * TODO
     */
    public static Set<ClassDescriptor> getAllDescriptors(Model model, Item item) {
        ClassDescriptor itemCD = ConverterUtils.getClassDescriptor(model, item);
        if (itemCD == null) {
            System.err.println("Error: getClassDescriptor() return a null ClassDescriptor");
            return null;
        } else {
            Set<ClassDescriptor> allCDs = itemCD.getAllSuperDescriptors();
            if (allCDs != null) {
                allCDs.add(itemCD);
            } else {
                System.err.println(
                        "Error: getAllSuperDescriptors() returned null and not an empty Set (shouldn't happen?)");
            }
            return allCDs;
        }
    }

    /**
     * TODO
     * 
     * @param idsMap
     * @param idsH
     * @return
     */
    public static Set<IDsHandler> getMatchingIDs(IDsMap idsMap, IDsHandler idsH) {
        Set<IDsHandler> matchingHandlers = new HashSet<IDsHandler>();

        if (idsH != null) {
            for (ID id : idsH.uids) {
                if (idsMap.containsId(id)) {
                    matchingHandlers.add(idsMap.get(id));
                }
            }
        }

        return matchingHandlers;
    }

    /**
     * TODO
     * 
     * @param idsMap
     * @param idsH
     * @return
     * @throws Exception
     */
    public static boolean addToIdsMap(IDsMap idsMap, IDsHandler idsH) throws Exception {
        boolean added = false;

        // Only proceeding if there is at least one unique id
        if (idsH.uids.size() > 0) {
            added = true;

            Set<IDsHandler> matchingHandlers = ConverterUtils.getMatchingIDs(idsMap, idsH);

            if (matchingHandlers.size() == 0) {
                // No matches, simply adding entries to idsMap
                for (ID id : idsH.uids) {
                    idsMap.add(id, idsH);
                }
            } else if (matchingHandlers.size() == 1) {
                // One match, adding new IDs to IDsHandler and new entries to idsMap
                IDsHandler matchedIdsH = matchingHandlers.iterator().next();

                for (ID id : idsH.uids) {
                    if (!matchedIdsH.hasUid(id)) {
                        matchedIdsH.addId(id);
                        idsMap.add(id, matchedIdsH);
                    }
                }
            } else {
                // Multiple matches, merging IDsHandler
                IDsHandler mergedHandler = null;
                for (IDsHandler idsHToMerge : matchingHandlers) {
                    if (mergedHandler == null) {
                        mergedHandler = idsHToMerge;
                    } else {
                        mergedHandler.mergeHandlers(idsHToMerge);
                    }
                }

                // Replacing/adding entries in idsMap
                for (ID id : mergedHandler.uids) {
                    idsMap.put(id, mergedHandler);
                }
            }
        }

        return added;
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
     * InterMine does not provide such a method
     * Note: items (reference ids) in collections are stored in a list instead of a
     * set for some reason
     */
    public static boolean removeItemFromCollection(Model model, Item item, Item itemToRemove) throws Exception {
        boolean success = false;

        if (item == null) {
            throw new Exception("Item with collection is null");
        }

        if (itemToRemove == null) {
            throw new Exception("Item to remove from collection is null");
        }

        ReferenceDescriptor rd = ConverterUtils.getReferenceDescriptorInItemAOfItemB(model, item, itemToRemove);
        if (rd == null) {
            throw new Exception(
                    "Couldn't find collection of "
                            + ConverterUtils.getClassDescriptor(model, itemToRemove).getSimpleName()
                            + " in " + ConverterUtils.getClassDescriptor(model, item).getSimpleName());
        }
        if (!rd.isCollection()) {
            throw new Exception("Item to remove from collection is null");
        }

        String collectionName = rd.getName();
        ReferenceList collection = item.getCollection(collectionName);
        if (collection == null) { // Shouldn't be null since getCollection() throws error before
            throw new Exception(
                    "Attempted to remove an item from an empty (uninitialised) collection (" + collectionName + ")");
        }

        List<String> refIds = collection.getRefIds();
        String itemToRemoveId = itemToRemove.getIdentifier();

        if (refIds == null || refIds.size() == 0) {
            throw new Exception("Attempted to remove an item from an empty collection (" + collectionName + ")");
        }

        Iterator<String> it = refIds.iterator();
        while (it.hasNext()) {
            if (itemToRemoveId.equals(it.next())) {
                it.remove();
                success = true;
                break;
            }
        }

        // TODO: exception if not found?
        if (success) {
            item.setCollection(collectionName, refIds);
        }

        return success;
    }

    /**
     * TODO
     * Items order shouldn't matter
     */
    public static void handleReferencesAndCollections(Model model, Item itemA, Item itemB) throws Exception {
        if (itemA != null && itemB != null) {
            ReferenceDescriptor rdInAOfB = ConverterUtils.getReferenceDescriptorInItemAOfItemB(model, itemA, itemB); // Can
                                                                                                                     // be
                                                                                                                     // a
            // CollectionDescriptor

            // Reference in itemA to itemB
            if (rdInAOfB != null) {
                if (rdInAOfB.isCollection()) {
                    itemA.addToCollection(rdInAOfB.getName(), itemB);
                } else {
                    itemA.setReference(rdInAOfB.getName(), itemB);
                }

                // Reference in itemB to itemA
                ReferenceDescriptor rdInBOfA = rdInAOfB.getReverseReferenceDescriptor();
                if (rdInBOfA != null) {
                    if (rdInBOfA.isCollection()) {
                        itemB.addToCollection(rdInBOfA.getName(), itemA);
                    } else {
                        itemB.setReference(rdInBOfA.getName(), itemA);
                    }
                } else {
                    System.err.println("handleReferencesAndCollections(): shouldn't happen");
                }
            } else {
                System.err.println("handleReferencesAndCollections(): Failed to find reference in "
                        + ConverterUtils.getClassDescriptor(model, itemA).getSimpleName()
                        + " class of " + ConverterUtils.getClassDescriptor(model, itemB).getSimpleName() + " class");
            }
        }
    }

    /**
     * TODO
     * Note: ReferenceDescriptor here also includes CollectionDescriptor sub-class
     */
    public static ReferenceDescriptor getReferenceDescriptorInItemAOfItemB(Model model, Item itemA, Item itemB)
            throws Exception {
        ReferenceDescriptor foundRD = null;

        Set<ReferenceDescriptor> rds = Stream
                .concat(ConverterUtils.getClassDescriptor(model, itemA).getAllReferenceDescriptors().stream(),
                        ConverterUtils.getClassDescriptor(model, itemA).getAllCollectionDescriptors().stream())
                .collect(Collectors.toSet());
        Iterator<ReferenceDescriptor> rdsIter = rds.iterator();

        // B ClassDescriptor + super classes CDs (for now not more useful than just B CD
        // but who knows)
        Set<ClassDescriptor> bCDs = ConverterUtils.getAllDescriptors(model, itemB);

        while (rdsIter.hasNext()) {
            ReferenceDescriptor rd = rdsIter.next();
            // Note: will not work as intended in case a Class has both a reference and a
            // collection of the same Class
            if (bCDs.contains(rd.getReferencedClassDescriptor())) {
                foundRD = rd;
                break;
            }
        }

        return foundRD;
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
     * @param s               the string to normalise
     * @param restToLowercase whether to convert to lowercase all characters after
     *                        the first
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
     * Note: "In utero" age group edge case is only present and therefore only
     * handled in CTIS
     */
    public static String calculateAgeGroup(Item study) {
        EnumSet<ConverterCVT.AgeGroup> ageGroups = EnumSet.noneOf(ConverterCVT.AgeGroup.class);

        String minAge = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MIN_AGE);
        String minAgeUnit = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MIN_AGE_UNIT);
        String maxAge = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MAX_AGE);
        String maxAgeUnit = ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MAX_AGE_UNIT);

        // Checking min age
        if (!ConverterUtils.isBlankOrNull(minAge) && !ConverterUtils.isBlankOrNull(minAgeUnit)) {
            if (!minAgeUnit.equals(ConverterCVT.AGE_UNIT_YEARS)) { // If unit is not years, means it's a smaller unit
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
                // If unit is not years it's a smaller unit, and child age group has already
                // been added with min age
                if (maxAgeUnit.equals(ConverterCVT.AGE_UNIT_YEARS)) {
                    if (NumberUtils.isParsable(maxAge)) {
                        Float maxAgeF = Float.parseFloat(maxAge);
                        if (maxAgeF >= 18 && maxAgeF < 65) {
                            ageGroups.add(ConverterCVT.AgeGroup.Adult);
                        } else if (maxAgeF >= 65) {
                            ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);

                            // If minAge is of child age group and max age of older adult age group, need to
                            // add adult age group as well
                            if (ageGroups.contains(ConverterCVT.AgeGroup.Pediatric)) {
                                ageGroups.add(ConverterCVT.AgeGroup.Adult);
                            }
                        }
                    } else {
                        // TODO: write log?
                    }
                }
            } else { // No max age, adding all groups older than the one added with minAge
                if (ageGroups.contains(ConverterCVT.AgeGroup.Pediatric)) {
                    ageGroups.add(ConverterCVT.AgeGroup.Adult);
                    ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);
                } else if (ageGroups.contains(ConverterCVT.AgeGroup.Pediatric)) {
                    ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);
                } // Else OLDER_ADULT, nothing to add
            }
        } else { // Checking max age with no min age
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
     * Get title from a study item if there is any (publicTitle or scientificTitle
     * or acronym)
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

    /**
     * TODO
     */
    public static LocalDate parseDate(String dateStr, DateTimeFormatter df) {
        LocalDate date = null;
        if (!ConverterUtils.isBlankOrNull(dateStr)) {
            // Test the passed formatter
            if (df != null) {
                date = ConverterUtils.getDateFromString(dateStr, df);
            }

            // Test other various patterns (null is ISO format)
            if (date == null) {
                DateTimeFormatter[] patterns = { null, ConverterUtils.P_DATE_D_M_Y_SLASHES,
                        ConverterUtils.P_DATE_D_MWORD_Y_SPACES,
                        ConverterUtils.P_DATE_MWORD_D_Y_HOUR, ConverterUtils.P_DATE_M_D_Y_TIME };
                for (DateTimeFormatter pattern : patterns) {
                    date = ConverterUtils.getDateFromString(dateStr, pattern);
                    if (date != null) {
                        break;
                    }
                }
            }

            // if (date == null) {
            // System.err.println("parseDate(): couldn't parse date: " + dateStr);
            // }
        }

        return date;
    }

    public static Matcher getMatchingIdMatcher(String id) {
        Matcher m = null;

        for (Pattern p : REGISTRY_ID_PATTERNS) {
            m = p.matcher(id);

            if (m.matches()) {
                return m;
            }
        }

        return null;
    }

    public static boolean isUniqueId(String id) {
        return (ConverterUtils.getMatchingIdMatcher(id) != null);
    }

    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    /**
     * This method is here to avoid errors in Groovy task
     * 
     * @param line
     * @return
     */
    public static CtgStudy getCtgStudy(String line) {
        return JSON.parseObject(line, CtgStudy.class);
    }

    /**
     * TODO
     * Method infers, when possible, ID type, source, and uniqueness
     */
    public static ID createID(String id) {
        ID studyIdentifier = null;

        if (!ConverterUtils.isBlankOrNull(id)) {
            String idValue = id;

            String type = null;
            String source = null;
            Boolean unique = false;

            // Testing different ID patterns to try to get ID type/source
            Matcher m = ConverterUtils.getMatchingIdMatcher(id);

            if (m != null) { // Found a matching ID pattern
                unique = true;
                type = ConverterCVT.ID_TYPE_TRIAL_REGISTRY;

                Pattern p = m.pattern();

                if (p == ConverterUtils.P_NCT_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_CTG;
                } else if (p == ConverterUtils.P_EU_ID) {
                    idValue = m.group(3);
                    String ctisPrefix = m.group(1);
                    String euctrPrefix = m.group(2);
                    String ctisSuffix = m.group(4);
                    String euctrSuffix = m.group(5);

                    if (ctisPrefix != null || ctisSuffix != null) { // CTIS ID
                        if (euctrPrefix == null && euctrSuffix == null) {
                            source = ConverterCVT.ID_SOURCE_CTIS; // EUCT Number
                        } else {
                            source = ConverterCVT.ID_SOURCE_AMBIG_EU;
                            // this.writeLog("CTIS ID matched but also has EUCTR ID characteristics: " +
                            // id);
                        }
                    } else if (euctrPrefix != null || euctrSuffix != null) { // EUCTR ID
                        source = ConverterCVT.ID_SOURCE_EUCTR; // EudraCT Number
                    } else { // Undistinguishable ID
                        source = ConverterCVT.ID_SOURCE_AMBIG_EU;
                    }
                } else if (p == ConverterUtils.P_ISRCTN_ID) {
                    idValue = "ISRCTN" + m.group(1);
                    source = ConverterCVT.ID_SOURCE_ISRCTN;
                } else if (p == ConverterUtils.P_UTN_ID) {
                    // TODO: missing a few cases with space after U and spaces around hyphens
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_WHO;
                } else if (p == ConverterUtils.P_ANZCTR_ID) {
                    // TODO: missing a few cases where prefix is ANZCTR instead of ACTRN
                    idValue = "ACTRN" + m.group(1);
                    source = ConverterCVT.ID_SOURCE_ANZCTR;
                } else if (p == ConverterUtils.P_DRKS_ID) {
                    idValue = "DRKS" + m.group(1);
                    source = ConverterCVT.ID_SOURCE_DRKS;
                } else if (p == ConverterUtils.P_CTRI_ID) {
                    // TODO: missing very few cases where slashes are missing or the 1st slash is a
                    // whitespace instead
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_CTRI;
                } else if (p == ConverterUtils.P_JPRN_ID) {
                    // TODO: missing very few cases such as "UMIN_ID:C000000091"
                    String noPrefixId = m.group(1);
                    String idWithPrefix = m.group(2);

                    // Prefixing all IDs (UMIN, jRCT(s), JapicCTI, etc.) with JPRN-
                    if (noPrefixId != null) {
                        idValue = "JPRN-" + noPrefixId;
                    } else {
                        idValue = idWithPrefix;
                    }
                } else if (p == ConverterUtils.P_REBEC_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_REBEC;
                } else if (p == ConverterUtils.P_CHICTR_ID) {
                    // Note: unsure how CHIMCTR and CHICTR differ, possibly a EUCTR/CTIS situation?
                    idValue = m.group(1);
                    if (m.group(2) == null) { // ChiCTR ID
                        source = ConverterCVT.ID_SOURCE_CHICTR;
                    } else { // ChiMCTR ID
                        source = ConverterCVT.ID_SOURCE_CHIMCTR;
                    }
                } else if (p == ConverterUtils.P_CRIS_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_CRIS;
                } else if (p == ConverterUtils.P_RPCEC_ID) {
                    source = ConverterCVT.ID_SOURCE_RPCEC;
                } else if (p == ConverterUtils.P_IRCT_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_IRCT;
                } else if (p == ConverterUtils.P_PACTR_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_PACTR;
                } else if (p == ConverterUtils.P_REPEC_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_REPEC;
                } else if (p == ConverterUtils.P_LBCTR_ID) {
                    source = ConverterCVT.ID_SOURCE_LBCTR;
                } else if (p == ConverterUtils.P_SLCTR_ID) {
                    // TODO: missing very few cases with whitespace before first slash
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_SLCTR;
                } else if (p == ConverterUtils.P_TCTR_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_TCTR;
                } else if (p == ConverterUtils.P_ITMCTR_ID) {
                    source = ConverterCVT.ID_SOURCE_ITMCTR;
                } else if (p == ConverterUtils.P_SNCTP_ID) {
                    idValue = m.group(1);
                    source = ConverterCVT.ID_SOURCE_SNCTP;
                } else if (p == ConverterUtils.P_NTR_ID) {
                    // Note: missing some IDs registered in various free-text formats
                    // Note 2: NTR prefix seems to be for older IDs and NL prefix for newer ones
                    // (even though it is also obsolete and is OMON now)
                    // See page 10: https://onderzoekmetmensen.nl/nl/node/24969/pdf
                    idValue = m.group(1) + m.group(2);
                    source = ConverterCVT.ID_SOURCE_NTR;
                } else if (p == ConverterUtils.P_OMON_ID) {
                    idValue = "NL-" + m.group(1);
                    source = ConverterCVT.ID_SOURCE_OMON;
                }
            }

            studyIdentifier = new ID(idValue, source, type, unique);
        }

        return studyIdentifier;
    }
}
