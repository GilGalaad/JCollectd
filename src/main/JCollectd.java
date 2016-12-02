package main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

public class JCollectd {

    public static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    static {
        Locale.setDefault(Locale.US);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$td/%1$tm/%1$tY %1$tH:%1$tM:%1$tS.%1$tL [%4$s] %5$s%n");
        LOGGER.setLevel(java.util.logging.Level.INFO);
    }

    public static void main(String[] args) {
        init(args);
    }

    private static void init(String[] args) {
        if (args.length != 1) {
            LOGGER.severe("Please provide path to configuration file as parameter, aborting");
            System.exit(1);
        }

        // loading parameters
        FileInputStream in;
        Properties prop = new Properties();
        try {
            in = new FileInputStream(args[0]);
            prop.load(in);
        } catch (IOException ex) {
            LOGGER.severe("Configuration file not found or not readable at specified path, aborting");
            System.exit(1);
        }

        // parsing and sanitizing parameters
        String propValue = prop.getProperty("db.path");
        if (propValue == null || propValue.trim().equals("")) {
            LOGGER.severe("Mandatory parameter 'dbpath' not found in configuration file, aborting");
            System.exit(1);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        LOGGER.severe("Parameter 'db.path' set to non existent or non writable directory, aborting");
                        System.exit(1);
                    }
                }
            } catch (RuntimeException ex) {
                LOGGER.severe("Parameter 'db.path' set to an illegal value, aborting");
                System.exit(1);
            }
        }

    }
}
