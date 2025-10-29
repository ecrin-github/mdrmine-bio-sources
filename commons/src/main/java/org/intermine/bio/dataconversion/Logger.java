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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;



/**
 * TODO
 * @author
 */
public class Logger {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSS");

    private Writer logWriter = null;

    public Logger(String logDir, String suffix) throws Exception {
        if (logDir.equals("")) {
            System.out.println("Warning: log dir is empty, log file will be created in current directory");
        }

        String current_timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // Create log dir (if not exists)
        Path logDirP = Paths.get(logDir);
        if (!Files.exists(logDirP)) Files.createDirectories(logDirP);

        // Initialise log writer
        Path logFile = Paths.get(logDirP.toString(), current_timestamp + "_" + suffix + ".log");
        this.logWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile.toString()), "utf-8"));
    }

    /**
     * Write to log file with timestamp.
     * 
     * @param text the log text
     */
    public void writeLog(String trialID, String text) {
        try {
            if (this.logWriter != null) {
                if (trialID != null) {
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + trialID + " - " + text + "\n");
                    this.logWriter.flush();
                } else {
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + text + "\n");
                    this.logWriter.flush();
                }
            } else {
                System.out.println("Log writer is null (cannot write logs)");
            }
        } catch(IOException e) {
            System.out.println("Couldn't write to log file");
        }
    }

    /**
     * Write to log file with timestamp.
     * 
     * @param text the log text
     */
    public void writeLog(String text) {
        this.writeLog(null, text);
    }

    /**
     * Close opened log writer.
     */
    public void stopLogging() throws IOException {
        if (this.logWriter != null) {
            this.logWriter.close();
        }
    }
}