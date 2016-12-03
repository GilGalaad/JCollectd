package main;

import engine.CollectConfig;
import engine.ShutdownHook;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JCollectd {

    public static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) throws Exception {
        CollectConfig conf = init(args);
        Thread sh = new ShutdownHook(Thread.currentThread());
        Runtime.getRuntime().addShutdownHook(sh);
    }

    private static CollectConfig init(String[] args) {
        if (args.length != 1) {
            logger.fatal("Please provide path to configuration file as parameter, aborting");
            System.exit(1);
        }

        // loading parameters
        FileInputStream in;
        Properties prop = new Properties();
        try {
            in = new FileInputStream(args[0]);
            prop.load(in);
        } catch (IOException ex) {
            logger.fatal("Configuration file not found or not readable at specified path, aborting");
            System.exit(1);
        }

        // parsing and sanitizing mandatory properties
        CollectConfig conf = new CollectConfig();
        String propValue = prop.getProperty("dbpath");
        if (isEmpty(propValue)) {
            logger.fatal("Mandatory parameter 'dbpath' not found in configuration file, aborting");
            System.exit(1);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        logger.fatal("Parameter 'dbpath' set to non existent or non writable directory, aborting");
                        System.exit(1);
                    }
                }
                logger.debug("Parameter dbpath -> {}", p.toString());
                conf.setDbPath(p);
            } catch (RuntimeException ex) {
                logger.fatal("Parameter 'dbpath' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        propValue = prop.getProperty("webpath");
        if (isEmpty(propValue)) {
            logger.fatal("Mandatory parameter 'webpath' not found in configuration file, aborting");
            System.exit(1);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        logger.fatal("Parameter 'webpath' set to non existent or non writable directory, aborting");
                        System.exit(1);
                    }
                }
                logger.debug("Parameter webpath -> {}", p.toString());
                conf.setWebPath(p);
            } catch (RuntimeException ex) {
                logger.fatal("Parameter 'webpath' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        propValue = (prop.getProperty("hostname"));
        if (isEmpty(propValue)) {
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                logger.debug("Parameter hostname -> {} (autodiscovered)", hostname);
                conf.setHostname(hostname);
            } catch (UnknownHostException ex) {
                logger.debug("Parameter hostname -> {} (autodiscovery failed)", "localhost");
                conf.setHostname("localhost");
            }
        } else {
            logger.debug("Parameter hostname -> {}", propValue.trim());
            conf.setHostname(propValue.trim());
        }

        propValue = (prop.getProperty("reportHours"));
        if (isEmpty(propValue)) {
            logger.debug("Parameter reportHours -> {} (default)", 12);
            conf.setReportHours(12);
        } else {
            try {
                int reportHours = Integer.parseUnsignedInt(propValue.trim());
                logger.debug("Parameter reportHours -> {}", reportHours);
                conf.setReportHours(reportHours);
            } catch (NumberFormatException ex) {
                logger.fatal("Parameter 'reportHours' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        propValue = (prop.getProperty("retentionHours"));
        if (isEmpty(propValue)) {
            logger.debug("Parameter retentionHours -> {} (default)", Math.max(12, conf.getReportHours()));
            conf.setRetentionHours(Math.max(12, conf.getReportHours()));
        } else {
            try {
                int retentionHours = Integer.parseUnsignedInt(propValue.trim());
                if (retentionHours >= conf.getReportHours()) {
                    logger.debug("Parameter retentionHours -> {}", retentionHours);
                    conf.setReportHours(retentionHours);
                } else {
                    logger.debug("Parameter retentionHours -> {} (floored by reportHours)", conf.getReportHours());
                    conf.setReportHours(conf.getReportHours());
                }
            } catch (NumberFormatException ex) {
                logger.fatal("Parameter 'retentionHours' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        // parsing probes properties
        ArrayList<String> plist = new ArrayList<>();
        Enumeration<String> iter = (Enumeration<String>) prop.propertyNames();
        while (iter.hasMoreElements()) {
            String p = iter.nextElement();
            if (p.matches("probe_\\d+_type")) {
                plist.add(p);
            }
        }
        Collections.sort(plist);
        conf.setProbeConfList(new ArrayList<>(plist.size()));
        for (int i = 0; i < plist.size(); i++) {
            String pn = plist.get(i);
            int idx = Integer.parseInt(pn.substring(6, pn.lastIndexOf("_")));
            if (idx != i + 1) {
                logger.fatal("Inconsistent probe list: probe #{} missing", i + 1);
                System.exit(1);
            }
            propValue = prop.getProperty(pn);
            if (isEmpty(propValue)) {
                logger.fatal("Inconsistent probe list: probe #{} undefined", i + 1);
                System.exit(1);
            }
        }

        return conf;
    }

    public static boolean isEmpty(String str) {
        return (str == null || str.trim().equals(""));
    }
}
