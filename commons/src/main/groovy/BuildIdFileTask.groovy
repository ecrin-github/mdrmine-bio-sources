package org.intermine.bio.dataconversion

import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.events.XMLEvent
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONException
import com.alibaba.fastjson2.JSONFactory
import com.alibaba.fastjson2.reader.ObjectReader
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.exceptions.CsvMalformedLineException
import org.intermine.plugin.BioSourceProperties
import org.intermine.plugin.project.ProjectXmlBinding
import org.intermine.plugin.project.Source

class BuildIdFileTask {

    String DEFAULT_LOG_DIR = '/home/ubuntu/mdrmine/logs'
    String DEFAULT_OUTPUT_DIR = '/home/ubuntu/data/idfile'
    Pattern P_HEADER_BIOLINCC = Pattern.compile('\\w+.*')
    IDsMap idsMap = IDsMap.getIDsMap()
    Set<IDsHandler> noUidsIdsHandlers = new HashSet<IDsHandler>()
    Logger logger = null

    // @TaskAction
    void run(project) {
        String logDir = project.findProperty('logDir') ?: DEFAULT_LOG_DIR
        String outputDir = project.findProperty('outputDir') ?: DEFAULT_OUTPUT_DIR

        String mineName = project.getProjectDir().getParentFile().getName().split('-')[0]
        String projectXml = project.getProjectDir().getParentFile().getParent() + File.separator + mineName + File.separator + 'project.xml'

        org.intermine.plugin.project.Project imProject = ProjectXmlBinding.unmarshall(new File(projectXml))
        println "Fetching source data using ${projectXml}"

        // Creating ID file directory if not exists
        Files.createDirectories(Paths.get(outputDir))
        String idFileFP = outputDir + File.separator + ConverterUtils.getCurrentTimestamp() + '_id_file.tsv'

        imProject.sources.keySet().each { sourceNameV ->
            Source source = imProject.sources.get(sourceNameV)
            String sourceName = source.name

            if (source != null && sourceName != null) {
                String srcDataDir = BioSourceProperties.getUserProperty(source, 'src.data.dir')
                String srcDataDirIncludes = BioSourceProperties.getUserProperty(source, 'src.data.dir.includes')

                if (srcDataDir != null && !srcDataDir.isEmpty() && srcDataDirIncludes != null && !srcDataDirIncludes.isEmpty()) {
                    // Initialising logger
                    if (logger == null) {
                        logger = new Logger(logDir, 'id_file_builder')
                    }

                    Path dataFP = Paths.get(srcDataDir, srcDataDirIncludes)

                    println "Source: ${sourceName}"
                    println "src.data.dir: ${srcDataDir}"
                    println "src.data.dir.includes: ${srcDataDirIncludes}"
                    println "Path: ${dataFP.toString()}"

                    if (sourceName.equals('who')) {
                        String headersFilePath = BioSourceProperties.getUserProperty(source, 'headersFilePath')
                        println "WHO headers file path: ${headersFilePath}"

                        this.parseWho(dataFP, Paths.get(headersFilePath))
                    } else if (sourceName.equals('ctg')) {
                        this.parseCtg(dataFP)
                    } else if (sourceName.equals('ctis')) {
                        this.parseCtis(dataFP)
                    } else if (sourceName.equals('euctr')) {
                        this.parseEuctr(dataFP)
                    } else if (sourceName.equals('biolincc')) {
                        this.parseBiolincc(dataFP)
                    } else {
                        println "Not using source ${sourceName}"
                    }
                }
            }
        }

        println 'Writing ID file'
        this.writeIdFile(idFileFP)

        println 'Writing nonUid file'
        String nonUidFileFP = outputDir + File.separator + ConverterUtils.getCurrentTimestamp() + '_nonuid_file.tsv'
        this.writeNonUidFile(nonUidFileFP)
    }

    void writeLog(String text) {
        if (this.logger != null) {
            this.logger.writeLog(text)
        } else {
            println 'Logger is null (cannot write logs)'
        }
    }

    void addToIdsMap(IDsHandler idsH) {
        boolean added = ConverterUtils.addToIdsMap(this.idsMap, idsH)

        if (!added) {
            this.noUidsIdsHandlers.add(idsH)
        }
    }

    void parseWho(Path dataFP, Path headersFP) {
        Map<String, Integer> fieldsToInd = this.getWhoHeaders(headersFP)

        BufferedReader reader = Files.newBufferedReader(dataFP)

        final CSVParser parser = new CSVParserBuilder()
                .withSeparator(',' as char)
                .withQuoteChar('"' as char)
                .build()
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .build()

        boolean skipNext = false

        // nextLine[] is an array of values from the line
        String[] lineValues = csvReader.readNext()

        int lineNumber = 0
        while (lineValues != null) {
            if (!skipNext) {
                lineNumber++
                if (lineNumber % 10000 == 0) {
                    println "Line: ${lineNumber}"
                }
                /* Trial ID */
                String trialID = this.getAndCleanValue(lineValues, fieldsToInd, 'TrialID')
                /* Secondary IDs */
                String secondaryIDs = this.getAndCleanValue(lineValues, fieldsToInd, 'SecondaryIDs')

                // In MDR model but unused; Unclear what the various values mean - certain
                // bridging flag/childs
                // values with parent or child bridged type seem to refer to the same study and
                // not additional/children studies
                // Treating bridgingFlag and childs as other IDs of this study
                String bridgingFlag = this.getAndCleanValue(lineValues, fieldsToInd, 'Bridging_flag')
                // TODO: unused?
                String bridgedType = this.getAndCleanValue(lineValues, fieldsToInd, 'Bridged_type')
                String childs = this.getAndCleanValue(lineValues, fieldsToInd, 'Childs')

                // Adding trialID, secondaryIDs, bridgingFlag and childs into one set
                // Note: Streams are not available in the version of Groovy
                Set<String> ids = new HashSet<String>()

                String[] secIDsArr = secondaryIDs.split(';')
                for (String secID: secIDsArr) {
                    String id = secID.strip()
                    if (!id.isBlank()) {
                        ids.add(id)
                    }
                }

                String[] childsArr = childs.split(';')
                for (String childID: childsArr) {
                    String id = childID.strip()
                    if (!id.isBlank()) {
                        ids.add(id)
                    }
                }
                ids.add(bridgingFlag)

                IDsHandler idsH = new IDsHandler(ConverterCVT.SOURCE_NAME_WHO, trialID, true)
                idsH.addIds(ids)

                this.addToIdsMap(idsH)
            } else {
                skipNext = false
            }
            try {
                lineValues = csvReader.readNext()
            } catch (CsvMalformedLineException e) {
                this.writeLog('WHO - Found malformed line, skipping it: ' + e)
                lineValues = new String[0]
                skipNext = true
            }
        }

        csvReader.close()
    }

    void parseCtg(Path dataFP) {
        BufferedReader br = Files.newBufferedReader(dataFP)

        // CtgStudy object reader
        ObjectReader<CtgStudy> objectReader = JSONFactory.getDefaultObjectReaderProvider()
                .getObjectReader(CtgStudy.class)

        br.readLine() // JSON array start

        String line
        int lineNumber = 0
        while ((line = br.readLine()) != null) {
            if (!line.equals(']')) {
                lineNumber++
                if (lineNumber % 10000 == 0) {
                    println "Line: ${lineNumber}"
                }
                try {
                    CtgStudy ctgStudy = ConverterUtils.getCtgStudy(line)

                    // TODO: trials have nctIdAliases...
                    // TODO: there is additional info regarding IDs, inferring id source/type should
                    // be attempted here first and then by the usual method

                    if (ctgStudy.protocolSection == null) {
                        this.writeLog('CTG - Warning: found study with no protocol section')
                    } else {
                        IdentificationModule idModule = ctgStudy.protocolSection.identificationModule

                        if (idModule == null) {
                            this.writeLog('CTG - Warning: found study with no ID module')
                        } else {
                            Set<String> ids = null

                            // Adding other IDs if any
                            if (idModule.secondaryIdInfos != null) {
                                ids = idModule.secondaryIdInfos.stream()
                                        .map({ secId -> secId.id })
                                        .map({ it -> it.strip() })
                                        .collect(Collectors.toSet())
                            } else {
                                ids = new HashSet<String>()
                            }

                            ID nctId = new ID(idModule.nctId, ConverterCVT.ID_SOURCE_CTG, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, true)
                            IDsHandler idsH = new IDsHandler(ConverterCVT.SOURCE_NAME_CTG, nctId, true)
                            idsH.addIds(ids)

                            this.addToIdsMap(idsH)
                        }
                    }
                } catch (JSONException e) {
                    this.writeLog('CTG - Failed to read JSON study: ' + e)
                }
            }
        }
    }

    void parseCtis(Path dataFP) {
        BufferedReader reader = Files.newBufferedReader(dataFP)

        final CSVParser parser = new CSVParserBuilder()
                .withSeparator(',' as char)
                .withQuoteChar('"' as char)
                .build()
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .build()

        boolean skipNext = false

        Map<String, Integer> fieldsToInd = this.getCtisHeaders(csvReader.readNext())

        String[] lineValues = csvReader.readNext()

        // TODO: performance tests? compared to iterator
        while (lineValues != null) {
            if (!skipNext) {
                String trialID = this.getAndCleanValueCtis(lineValues, fieldsToInd, 'Trial number')

                if (!ConverterUtils.isBlankOrNull(trialID) && trialID.length() == 17) {
                    String baseID = trialID.substring(0, 14)

                    ID id = new ID(baseID, ConverterCVT.ID_SOURCE_CTIS, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, true)
                    IDsHandler idsH = new IDsHandler(ConverterCVT.SOURCE_NAME_CTIS, id, true)

                    this.addToIdsMap(idsH)
                } else {
                    this.writeLog('Unexpected length for trial ID: ' + trialID)
                }
            } else {
                skipNext = false
            }

            try {
                lineValues = csvReader.readNext()
            } catch (CsvMalformedLineException e) {
                this.writeLog('Failed to parse line')
                lineValues = new String[0]
                skipNext = true
            }
        }

        csvReader.close()
    }

    void parseEuctr(Path dataFP) {
        BufferedReader reader = Files.newBufferedReader(dataFP)

        XMLInputFactory xi = XMLInputFactory.newInstance()

        // Disable DTD check in DOCTYPE for file(s) with CTIS entries to avoid errors
        xi.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        xi.setProperty('javax.xml.stream.isSupportingExternalEntities', false)

        XMLStreamReader xr = xi.createXMLStreamReader(reader)
        XmlMapper xm = new XmlMapper()
        int eventType

        while (xr.hasNext()) {
            eventType = xr.next()
            switch (eventType) {
                case XMLEvent.START_ELEMENT:
                    if (xr.getLocalName().toLowerCase().equals('trial')) {
                        EuctrTrial trial = xm.readValue(xr, EuctrTrial.class)

                        EuctrMainInfo mainInfo = trial.getMainInfo()
                        if (mainInfo == null) {
                            this.writeLog('EUCTR trial mainInfo is null')
                        } else {
                            IDsHandler idsH = null

                            String trialId = this.getAndCleanValue(mainInfo, 'trialId')
                            String regName = this.getAndCleanValue(mainInfo, 'regName')

                            String cleanID = trialId.substring(0, 14) // Removing country code or resubmission suffix

                            ID primaryId = null

                            // TODO
                            // Always EUCTR except in 20251208.xml where its CTIS
                            if (!ConverterUtils.isBlankOrNull(regName) && ConverterCVT.EUCTR_REG_NAME_CTIS.equalsIgnoreCase(regName)) {
                                primaryId = new ID(cleanID, ConverterCVT.ID_SOURCE_CTIS, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, true)
                            } else {
                                primaryId = new ID(cleanID, ConverterCVT.ID_SOURCE_EUCTR, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, true)
                            }

                            idsH = new IDsHandler(ConverterCVT.SOURCE_NAME_EUCTR, primaryId, true)

                            Set<String> ids = new HashSet<String>()

                            // Adding other IDs if any
                            List<EuctrSecondaryId> secIds = trial.getSecondaryIds()
                            if (secIds != null && secIds.size() > 0) {
                                ids = secIds.stream()
                                        .map({ it -> it.getSecondaryId() })
                                        .map({ it -> it.strip() })
                                        .collect(Collectors.toSet())
                            } else {
                                ids = new HashSet<String>()
                            }

                            // WHO UTRN
                            String trialUtrn = this.getAndCleanValue(mainInfo, 'utrn')
                            ids.add(trialUtrn)

                            idsH.addIds(ids)

                            this.addToIdsMap(idsH)
                        }
                    }
                    break
                case XMLEvent.CHARACTERS:
                    break
                case XMLEvent.ATTRIBUTE:
                    break
                case XMLEvent.START_DOCUMENT:
                    break
                default:
                    break
            }
        }
        xr.close()
    }

    void parseBiolincc(Path dataFP) {
        BufferedReader reader = Files.newBufferedReader(dataFP)

        final CSVParser parser = new CSVParserBuilder()
                .withSeparator(',' as char)
                .withQuoteChar('"' as char)
                .build()
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .build()

        /* Headers */
        Map<String, Integer> fieldsToInd = this.getBiolinccHeaders(csvReader.readNext())

        /* Reading file */
        boolean skipNext = false

        String[] lineValues = csvReader.readNext()

        // TODO: performance tests? compared to iterator
        while (lineValues != null) {
            if (!skipNext) {
                String accessionNumber = this.getAndCleanValue(lineValues, fieldsToInd, 'Accession Number')
                String clinicalTrialUrls = this.getAndCleanValue(lineValues, fieldsToInd, 'Clinical trial urls')
                Set<String> nctIds = this.getNctIdsFromUrls(clinicalTrialUrls)

                IDsHandler idsH = null

                // From the current MDR documentation: "BioLINCC Ids (e.g. HLB01461719a,
                // HLB00510606a) have been found to change over time [...]. They can therefore
                // not be used as an identifier"
                ID biolinccID = new ID(accessionNumber, ConverterCVT.ID_SOURCE_NHLBI, ConverterCVT.ID_TYPE_BIOLINCC, false)
                idsH = new IDsHandler(ConverterCVT.SOURCE_NAME_BIOLINCC, nctIds)

                idsH.addId(biolinccID)

                this.addToIdsMap(idsH)
            } else {
                skipNext = false
            }

            try {
                lineValues = csvReader.readNext()
            } catch (CsvMalformedLineException e) {
                this.writeLog('Failed to parse line')
                lineValues = new String[0]
                skipNext = true
            }
        }

        csvReader.close()
    }

    Set<String> getNctIdsFromUrls(String clinicalTrialUrls) {
        Set<String> parsedIds = new HashSet<String>()

        if (!ConverterUtils.isBlankOrNull(clinicalTrialUrls)) {
            String[] urls = clinicalTrialUrls.split(', ')
            Matcher mUrl

            for (String url : urls) {
                mUrl = ConverterUtils.P_ID_AT_END_OF_URL.matcher(url)
                if (mUrl.matches()) {
                    parsedIds.add(mUrl.group(1))
                } else {
                    this.writeLog('Biolincc - Failed to match CTG URL: ' + url)
                }
            }
        }

        return parsedIds
    }

    /**
     * Get a dictionary (map) of the WHO data file field names linked to their
     * corresponding column index in the data file, using a separate headers file.
     * The headers file path is defined in the project.xml file (and set as an
     * instance attribute of this class).
     *
     * @return map of data file field names and their corresponding column index
     */
    Map<String, Integer> getWhoHeaders(Path headersFilePath) throws Exception {
        if (headersFilePath == null || headersFilePath.toString().isEmpty()) {
            throw new Exception('headersFilePath property not set in mdrmine project.xml')
        }

        if (!headersFilePath.toFile().isFile()) {
            throw new FileNotFoundException('WHO Headers file does not exist (path tested: ' + headersFilePath + ' )')
        }

        List<String> fileContent = Files.readAllLines(headersFilePath, StandardCharsets.UTF_8)
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>()

        if (fileContent.size() > 0) {
            String headersLine = String.join('', fileContent).strip()
            // Deleting the invisible \FEFF unicode character at the beginning of the header
            // file
            if (Character.getNumericValue(headersLine.charAt(0)) == -1 ||
                Integer.toHexString(Character.getNumericValue(headersLine.charAt(0)) | 0x10000).substring(1).toLowerCase() == ('feff')) {
                headersLine = headersLine.substring(1)
                }
            String[] fields = headersLine.split(',')

            for (int ind = 0; ind < fields.length; ind++) {
                fieldsToInd.put(fields[ind], ind)
            }
        } else {
            throw new Exception('WHO Headers file is empty')
        }

        return fieldsToInd
    }

    Map<String, Integer> getCtisHeaders(String[] headersList) throws Exception {
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>()

        if (headersList.length > 0) {
            for (int ind = 0; ind < headersList.length; ind++) {
                // Deleting the invisible \FEFF unicode character at the beginning of the header
                // file
                if (Character.getNumericValue(headersList[ind].charAt(0)) == -1 ||
                    Integer.toHexString(Character.getNumericValue(headersList[ind].charAt(0)) | 0x10000).substring(1).toLowerCase() == ('feff')) {
                    headersList[ind] = headersList[ind].substring(1)
                    }
                fieldsToInd.put(headersList[ind], ind)
            }
        } else {
            throw new Exception('CTIS Headers file is empty')
        }

        return fieldsToInd
    }

    Map<String, Integer> getBiolinccHeaders(String[] fields) throws Exception {
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>()

        if (fields.length == 0) {
            throw new Exception('BioLINCC data file is empty (failed getting headers)')
        }

        for (int ind = 0; ind < fields.length; ind++) {
            if (ind == 0) {
                /*
                 * Opened file is passed to this function, so we can't change the encoding on
                 * read
                 * to handle the BOM \uFEFF leading character, we have to remove it ourselves.
                 * The character is read differently in Docker, so we match the header field
                 * name starting with a letter.
                 */
                Matcher mHeader = P_HEADER_BIOLINCC.matcher(fields[ind])
                if (mHeader.find()) {
                    fieldsToInd.put(mHeader.group(0), ind)
                } else {
                    throw new Exception("Couldn't properly parse BioLINCC first header value: '" + fields[ind] + "'")
                }
            } else {
                fieldsToInd.put(fields[ind], ind)
            }
        }

        return fieldsToInd
    }

    String getAndCleanValue(String[] lineValues, Map<String, Integer> fieldsToInd, String field) {
        // TODO: handle errors
        return ConverterUtils.unescapeHtml(ConverterUtils.removeQuotes(lineValues[fieldsToInd.get(field)])).strip()
    }

    String getAndCleanValueCtis(String[] lineValues, Map<String, Integer> fieldsToInd, String field) {
        // TODO: handle errors
        return lineValues[fieldsToInd.get(field) as Integer].strip()
    }

    String getAndCleanValue(Object euctrObj, String fieldName) throws Exception {
        Method method = euctrObj.getClass().getMethod('get' + ConverterUtils.capitaliseFirstLetter(fieldName, false),
                (Class<?>[]) null)
        String value = (String) method.invoke(euctrObj)
        if (value != null) {
            return value.strip()
        }
        return null
    }

    void writeIdFile(String idFP) {
        Set<IDsHandler> idsHSet = this.idsMap.getAllIDsHandlers()
        BufferedWriter fos = new BufferedWriter(new FileWriter(idFP, true))
        for (IDsHandler idsH: idsHSet) {
            String idfs = idsH.getIdFileString()
            if (!idfs.isEmpty()) {
                fos.write(idfs + '\n')
            }
        }
        fos.close()
    }

    void writeNonUidFile(String nonUidFP) {
        BufferedWriter fos = new BufferedWriter(new FileWriter(nonUidFP, true))
        for (IDsHandler idsH: this.noUidsIdsHandlers) {
            String idfs = idsH.getNonUidFileString()
            if (!idfs.isEmpty()) {
                fos.write(idfs + '\n')
            }
        }
        fos.close()
    }

}
