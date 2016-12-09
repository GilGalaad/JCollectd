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
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;

public class JCollectd {

    public static final Logger logger = Logger.getGlobal();

    static {
        Locale.setDefault(Locale.US);
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$td/%1$tm/%1$tY %1$tH:%1$tM:%1$tS.%1$tL %4$s] %5$s%n");
        System.setProperty("java.util.logging.ConsoleHandler.level", "INFO");
        logger.setLevel(java.util.logging.Level.INFO);
    }

    public static void main(String[] args) throws Exception {
        CollectConfig conf = init(args);
        Thread sh = new ShutdownHook(Thread.currentThread());
        Runtime.getRuntime().addShutdownHook(sh);
        CollectEngine eng = new CollectEngine(conf);
        eng.run();
    }

    private static CollectConfig init(String[] args) {
        if (!System.getProperty("os.name").equals("Linux")) {
            logger.log(SEVERE, "Unsupported platform: {0}, aborting", System.getProperty("os.name"));
            System.exit(1);
        }

        if (args.length != 1) {
            logger.log(SEVERE, "Please provide path to configuration file as parameter, aborting");
            System.exit(1);
        }

        // loading parameters
        FileInputStream in;
        Properties prop = new Properties();
        try {
            in = new FileInputStream(args[0]);
            prop.load(in);
        } catch (IOException ex) {
            logger.log(SEVERE, "Configuration file not found or not readable at specified path, aborting");
            System.exit(1);
        }

        // parsing and sanitizing mandatory properties
        CollectConfig conf = new CollectConfig();
        String propValue = prop.getProperty("dbpath");
        if (isEmpty(propValue)) {
            logger.log(SEVERE, "Mandatory parameter 'dbpath' not found in configuration file, aborting");
            System.exit(1);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        logger.log(SEVERE, "Parameter 'dbpath' set to non existent or non writable directory, aborting");
                        System.exit(1);
                    }
                }
                logger.log(INFO, "Parameter dbpath -> {0}", p.toString());
                conf.setDbPath(p);
            } catch (RuntimeException ex) {
                logger.log(SEVERE, "Parameter 'dbpath' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        propValue = prop.getProperty("webpath");
        if (isEmpty(propValue)) {
            logger.log(SEVERE, "Mandatory parameter 'webpath' not found in configuration file, aborting");
            System.exit(1);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        logger.log(SEVERE, "Parameter 'webpath' set to non existent or non writable directory, aborting");
                        System.exit(1);
                    }
                }
                logger.log(INFO, "Parameter webpath -> {0}", p.toString());
                conf.setWebPath(p);
            } catch (RuntimeException ex) {
                logger.log(SEVERE, "Parameter 'webpath' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        propValue = (prop.getProperty("hostname"));
        if (isEmpty(propValue)) {
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                logger.log(INFO, "Parameter hostname -> {0} (autodiscovered)", hostname);
                conf.setHostname(hostname);
            } catch (UnknownHostException ex) {
                logger.log(INFO, "Parameter hostname -> {0} (autodiscovery failed)", "localhost");
                conf.setHostname("localhost");
            }
        } else {
            logger.log(INFO, "Parameter hostname -> {0}", propValue.trim());
            conf.setHostname(propValue.trim());
        }

        propValue = (prop.getProperty("reportHours"));
        if (isEmpty(propValue)) {
            logger.log(INFO, "Parameter reportHours -> {0} (default)", 12);
            conf.setReportHours(12);
        } else {
            try {
                int reportHours = Integer.parseUnsignedInt(propValue.trim());
                logger.log(INFO, "Parameter reportHours -> {0}", reportHours);
                conf.setReportHours(reportHours);
            } catch (NumberFormatException ex) {
                logger.severe("Parameter 'reportHours' set to an illegal value, aborting");
                System.exit(1);
            }
        }

        propValue = (prop.getProperty("retentionHours"));
        if (isEmpty(propValue)) {
            logger.log(INFO, "Parameter retentionHours -> {0} (default)", Math.max(12, conf.getReportHours()));
            conf.setRetentionHours(Math.max(12, conf.getReportHours()));
        } else {
            try {
                int retentionHours = Integer.parseUnsignedInt(propValue.trim());
                if (retentionHours >= conf.getReportHours()) {
                    logger.log(INFO, "Parameter retentionHours -> {0}", retentionHours);
                    conf.setRetentionHours(retentionHours);
                } else {
                    logger.log(INFO, "Parameter retentionHours -> {0} (floored by reportHours)", conf.getReportHours());
                    conf.setRetentionHours(conf.getReportHours());
                }
            } catch (NumberFormatException ex) {
                logger.log(SEVERE, "Parameter 'retentionHours' set to an illegal value, aborting");
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
        logger.log(INFO, "Found {0} probe definitions", plist.size());
        for (int i = 0; i < plist.size(); i++) {
            String[] split = plist.get(i).split("_", -1);
            int idx = Integer.parseInt(split[1]);
            if (idx != i + 1) {
                logger.log(SEVERE, "Illegal probe list: probe #{0} missing, aborting", i + 1);
                System.exit(1);
            }
            propValue = prop.getProperty(plist.get(i));
            if (isEmpty(propValue)) {
                logger.log(SEVERE, "Illegal probe list: probe #{0} undefined, aborting", idx);
                System.exit(1);
            } else if (propValue.equalsIgnoreCase("load")) {
                logger.log(INFO, "Probe #{0} -> {1}, {2}", new Object[]{idx, ProbeType.LOAD, getGraphSize(prop, idx)});
                probeConfList.add(new ProbeConfig(ProbeType.LOAD, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("cpu")) {
                logger.log(INFO, "Probe #{0} -> {1}, {2}", new Object[]{idx, ProbeType.CPU, getGraphSize(prop, idx)});
                probeConfList.add(new ProbeConfig(ProbeType.CPU, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("mem")) {
                logger.log(INFO, "Probe #{0} -> {1}, {2}", new Object[]{idx, ProbeType.MEM, getGraphSize(prop, idx)});
                probeConfList.add(new ProbeConfig(ProbeType.MEM, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("net")) {
                logger.log(INFO, "Probe #{0} -> {1}, {2}, {3}", new Object[]{idx, ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx)});
                probeConfList.add(new ProbeConfig(ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx)));
            } else if (propValue.equalsIgnoreCase("hdd")) {
                logger.log(INFO, "Probe #{0} -> {1}, {2}, {3}", new Object[]{idx, ProbeType.HDD, getGraphSize(prop, idx), getProbeDevice(prop, idx)});
                probeConfList.add(new ProbeConfig(ProbeType.HDD, getGraphSize(prop, idx), getProbeDevice(prop, idx)));
            } else {
                logger.log(SEVERE, "Unsupported probe #{0} type: {1}, aborting", new Object[]{idx, propValue});
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
            logger.log(SEVERE, "Unsupported probe #{0} size: {1}, aborting", new Object[]{idx, gsize});
            System.exit(1);
        }
        return null;
    }

    private static String getProbeDevice(Properties prop, int idx) {
        String device = prop.getProperty("probe_" + idx + "_device");
        if (isEmpty(device)) {
            logger.log(SEVERE, "Illegal probe list: probe #{0} requires mandatory device name, aborting", idx);
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
