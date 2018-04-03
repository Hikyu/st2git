/*****************************************************************************
    This file is part of Git-Starteam.

    Git-Starteam is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Git-Starteam is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Git-Starteam.  If not, see <http://www.gnu.org/licenses/>.
******************************************************************************/
package org.sync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.FileUtils;

public final class Log {
    private static File logFile = null;
    private static FileWriter writer = null;
    static {
        try {
            logFile = new File("st-git.log");
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            writer = new FileWriter(logFile);
        } catch (Exception e) {
        }
    }

    // prevent instantiation
    private Log() {
    }

    // SimpleDateFormat is not threadsafe
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static synchronized StringBuilder newEntry() {
        Date date = new Date();
        StringBuilder b = new StringBuilder();
        return b.append(DATE_FORMAT.format(date)).append(' ');
    }

    /**
     * Logs msg to stderr
     */
    public static void log(String msg) {
        StringBuilder log = newEntry().append(msg);
        System.err.println(log);
        log2File(log.toString());
    }

    private static void log2File(String log) {
        if (writer != null) {
            try {
                writer.write(log);
                writer.write("\n");
            } catch (IOException e) {
            }
        }
    }

    /**
     * Logs formatted message to stderr
     */
    public static void logf(String fmt, Object... objects) {
        StringBuilder log = newEntry().append(String.format(fmt, objects));
        System.err.println(log);
        log2File(log.toString());
    }

    /**
     * Logs msg to stdout
     */
    public static void out(String msg) {
        StringBuilder log = newEntry().append(msg);
        System.out.println(log);
        log2File(log.toString());
    }

    public static void close() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
        }
    }
}
