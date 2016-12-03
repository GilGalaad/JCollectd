package main;

import engine.CollectEngine;
import engine.ShutdownHook;
import engine.config.CollectConfig;
import engine.config.ProbeConfig;
import engine.config.ProbeConfig.GraphSize;
import engine.config.ProbeConfig.ProbeType;
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
import java.util.Locale;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JCollectd {

    public static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) throws Exception {
        CollectConfig conf = init(args);
        Thread sh = new ShutdownHook(Thread.currentThread());
        Runtime.getRuntime().addShutdownHook(sh);
        CollectEngine eng = new CollectEngine(conf);
        eng.run();
    }

    private static CollectConfig init(String[] args) {
        if (!System.getProperty("os.name").equals("Linux")) {
            logger.fatal("Unsupported platform: {}, aborting", System.getProperty("os.name"));
            //System.exit(1);
        }

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
                logger.info("Parameter dbpath -> {}", p.toString());
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
                logger.info("Parameter webpath -> {}", p.toString());
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
                logger.info("Parameter hostname -> {} (autodiscovered)", hostname);
                conf.setHostname(hostname);
            } catch (UnknownHostException ex) {
                logger.info("Parameter hostname -> {} (autodiscovery failed)", "localhost");
                conf.setHostname("localhost");
            }
        } else {
            logger.info("Parameter hostname -> {}", propValue.trim());
            conf.setHostname(propValue.trim());
        }

        propValue = (prop.getProperty("reportHours"));
        if (isEmpty(propValue)) {
            logger.info("Parameter reportHours -> {} (default)", 12);
            conf.setReportHours(12);
        } else {
            try {
                int reportHours = Integer.parseUnsignedInt(propValue.trim());
                logger.info("Parameter reportHours -> {}", reportHours);
                conf.setReportHours(reportHours);
            } catch (NumberFormatException ex) {
                logger.fatal("Parameter 'reportHours' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        propValue = (prop.getProperty("retentionHours"));
        if (isEmpty(propValue)) {
            logger.info("Parameter retentionHours -> {} (default)", Math.max(12, conf.getReportHours()));
            conf.setRetentionHours(Math.max(12, conf.getReportHours()));
        } else {
            try {
                int retentionHours = Integer.parseUnsignedInt(propValue.trim());
                if (retentionHours >= conf.getReportHours()) {
                    logger.info("Parameter retentionHours -> {}", retentionHours);
                    conf.setReportHours(retentionHours);
                } else {
                    logger.info("Parameter retentionHours -> {} (floored by reportHours)", conf.getReportHours());
                    conf.setReportHours(conf.getReportHours());
                }
            } catch (NumberFormatException ex) {
                logger.fatal("Parameter 'retentionHours' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        // parsing probes properties
        ArrayList<String> plist = new ArrayList<>();
        Enumeration<?> iter = prop.propertyNames();
        while (iter.hasMoreElements()) {
            String p = (String) iter.nextElement();
            if (p.matches("probe_\\d+_type")) {
                plist.add(p);
            }
        }
        Collections.sort(plist);
        ArrayList<ProbeConfig> probeConfList = new ArrayList<>(plist.size());
        conf.setProbeConfigList(probeConfList);
        logger.info("Found {} probe definitions", plist.size());
        for (int i = 0; i < plist.size(); i++) {
            String[] split = plist.get(i).split("_", -1);
            int idx = Integer.parseInt(split[1]);
            if (idx != i + 1) {
                logger.fatal("Illegal probe list: probe #{} missing, aborting", i + 1);
                System.exit(1);
            }
            propValue = prop.getProperty(plist.get(i));
            if (isEmpty(propValue)) {
                logger.fatal("Illegal probe list: probe #{} undefined, aborting", idx);
                System.exit(1);
            } else if (propValue.equalsIgnoreCase("load")) {
                logger.info("Probe #{} -> {}, {}", idx, ProbeType.LOAD, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfig(ProbeType.LOAD, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("cpu")) {
                logger.info("Probe #{} -> {}, {}", idx, ProbeType.CPU, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfig(ProbeType.CPU, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("mem")) {
                logger.info("Probe #{} -> {}, {}", idx, ProbeType.MEM, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfig(ProbeType.MEM, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("net")) {
                logger.info("Probe #{} -> {}, {}, {}", idx, ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx));
                probeConfList.add(new ProbeConfig(ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx)));
            } else if (propValue.equalsIgnoreCase("hdd")) {
                logger.info("Probe #{} -> {}, {}, {}", idx, ProbeType.HDD, getGraphSize(prop, idx), getProbeDevice(prop, idx));
                probeConfList.add(new ProbeConfig(ProbeType.HDD, getGraphSize(prop, idx), getProbeDevice(prop, idx)));
            } else {
                logger.fatal("Unsupported probe #{} type: {}, aborting", idx, propValue);
                System.exit(1);
            }
        }
        return conf;
    }

    private static GraphSize getGraphSize(Properties prop, int idx) {
        String gsize = prop.getProperty("probe_" + idx + "_size");
        if (isEmpty(gsize) || gsize.trim().equalsIgnoreCase("full")) {
            return GraphSize.FULL_SIZE;
        } else if (gsize.trim().equalsIgnoreCase("half")) {
            return GraphSize.HALF_SIZE;
        } else {
            logger.fatal("Unsupported probe #{} size: {}, aborting", idx, gsize);
            System.exit(1);
        }
        return null;
    }

    private static String getProbeDevice(Properties prop, int idx) {
        String device = prop.getProperty("probe_" + idx + "_device");
        if (isEmpty(device)) {
            logger.fatal("Illegal probe list: probe #{} requires mandatory device name, aborting", idx);
            System.exit(1);
        } else {
            return device.trim();
        }
        return null;
    }

    public static boolean isEmpty(String str) {
        return (str == null || str.trim().equals(""));
    }

    public static String prettyPrint(long num) {
        return String.format(Locale.ITALY, "%,d", num);
    }
}
