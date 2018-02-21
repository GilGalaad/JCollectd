package main;

import static engine.CommonUtils.isEmpty;
import engine.config.CollectConfiguration;
import static engine.config.CollectConfiguration.DbEngine.SQLITE;
import static engine.config.CollectConfiguration.OperatingSystem.FREEBSD;
import static engine.config.CollectConfiguration.OperatingSystem.LINUX;
import engine.config.ProbeConfiguration;
import engine.exception.ConfigurationException;
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

public class ConfigurationParser {

    public static final Logger logger = LogManager.getLogger();

    protected static CollectConfiguration parse(String arg) throws ConfigurationException {
        CollectConfiguration conf = new CollectConfiguration();
        String propValue;

        // checking OS
        if (System.getProperty("os.name").equals("FreeBSD")) {
            conf.setOs(FREEBSD);
        } else if (System.getProperty("os.name").equals("Linux")) {
            conf.setOs(LINUX);
        } else {
            throw new ConfigurationException(String.format("Unsupported platform: %s, aborting", System.getProperty("os.name")));
        }

        // loading parameters
        Properties prop = new Properties();
        try (FileInputStream in = new FileInputStream(arg)) {
            prop.load(in);
        } catch (IOException ex) {
            throw new ConfigurationException("Configuration file not found or not readable at specified path, aborting", ex);
        }

        // parsing and sanitizing global properties
        // Sqlite is the only supported database at the moment
        conf.setDbEngine(SQLITE);

        propValue = prop.getProperty("dbPath");
        if (isEmpty(propValue)) {
            throw new ConfigurationException("Mandatory parameter 'dbPath' not found in configuration file, aborting", null);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        throw new ConfigurationException("Parameter 'dbPath' set to non existent or non writable directory, aborting");
                    }
                }
                logger.info("Parameter dbpath -> {}", p.toString());
                conf.setDbPath(p);
            } catch (RuntimeException ex) {
                throw new ConfigurationException("Parameter 'dbpath' set to an illegal value, aborting", ex);
            }
        }

        propValue = prop.getProperty("webPath");
        if (isEmpty(propValue)) {
            logger.info("Parameter webpath -> HTML report disabled");
            conf.setWebPath(null);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        throw new ConfigurationException("Parameter 'webPath' set to non existent or non writable directory, aborting");
                    }
                }
                logger.info("Parameter webpath -> {}", p.toString());
                conf.setWebPath(p);
            } catch (RuntimeException ex) {
                throw new ConfigurationException("Parameter 'webPath' set to an illegal value, aborting", ex);
            }
        }

        propValue = (prop.getProperty("hostname"));
        if (isEmpty(propValue)) {
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                logger.info("Parameter hostname -> {} (autodiscovered)", hostname);
                conf.setHostname(hostname);
            } catch (UnknownHostException ex) {
                logger.info("Parameter hostname -> localhost (autodiscovery failed)");
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
                throw new ConfigurationException("Parameter 'reportHours' set to an illegal value, aborting", ex);
            }
        }

        propValue = (prop.getProperty("retentionHours"));
        if (isEmpty(propValue)) {
            logger.info("Parameter retentionHours -> {} (defaulting to reportHours)", conf.getReportHours());
            conf.setRetentionHours(conf.getReportHours());
        } else {
            try {
                int retentionHours = Integer.parseInt(propValue.trim());
                if (retentionHours >= conf.getReportHours()) {
                    logger.info("Parameter retentionHours -> {}", retentionHours);
                    conf.setRetentionHours(retentionHours);
                } else if (retentionHours >= 0 && retentionHours < conf.getReportHours()) {
                    logger.info("Parameter retentionHours -> {} (ceiling to reportHours)", conf.getReportHours());
                    conf.setRetentionHours(conf.getReportHours());
                } else {
                    logger.info("Parameter retentionHours -> database cleanup disabled", conf.getReportHours());
                    conf.setRetentionHours(-1);
                }
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Parameter 'retentionHours' set to an illegal value, aborting", ex);
            }
        }

        propValue = (prop.getProperty("interval"));
        if (isEmpty(propValue)) {
            logger.info("Parameter interval -> {} (default)", 60);
            conf.setInterval(60);
        } else {
            try {
                int interval = Integer.parseUnsignedInt(propValue.trim());
                logger.info("Parameter interval -> {}", interval);
                conf.setInterval(interval);
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Parameter 'interval' set to an illegal value, aborting", ex);
            }
        }

        // parsing probes properties
        ArrayList<String> plist = new ArrayList<>();
        Enumeration<?> iter = prop.propertyNames();
        while (iter.hasMoreElements()) {
            String p = (String) iter.nextElement();
            if (p.matches("probe\\.\\d+\\.type")) {
                plist.add(p);
            }
        }
        Collections.sort(plist);
        ArrayList<ProbeConfiguration> probeConfList = new ArrayList<>(plist.size());
        conf.setProbeConfigList(probeConfList);
        logger.info("Found {} probe definitions", plist.size());
        for (int i = 0; i < plist.size(); i++) {
            String[] split = plist.get(i).split("\\.");
            int idx = Integer.parseInt(split[1]);
            if (idx != i + 1) {
                throw new ConfigurationException(String.format("Illegal probe list: probe #%s missing, aborting", i + 1));
            }
            propValue = prop.getProperty(plist.get(i));
            if (isEmpty(propValue)) {
                throw new ConfigurationException(String.format("Illegal probe list: probe #%s undefined type, aborting", i + 1));
            } else if (propValue.equalsIgnoreCase("load")) {
                logger.info("Probe #{} -> {}, {}", idx, ProbeConfiguration.ProbeType.LOAD, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.LOAD, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("cpu")) {
                logger.info("Probe #{} -> {}, {}", idx, ProbeConfiguration.ProbeType.CPU, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.CPU, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("mem")) {
                logger.info("Probe #{} -> {}, {}", idx, ProbeConfiguration.ProbeType.MEM, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.MEM, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("net")) {
                logger.info("Probe #{} -> {}, {}, {}, {}", idx, ProbeConfiguration.ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx)));
            } else if (propValue.equalsIgnoreCase("disk")) {
                logger.info("Probe #{} -> {}, {}, {}, {}", idx, ProbeConfiguration.ProbeType.DISK, getGraphSize(prop, idx), getProbeDeviceList(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.DISK, getGraphSize(prop, idx), getProbeDeviceList(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx)));
            } else {
                throw new ConfigurationException(String.format("Unsupported probe #%s type: %s, aborting", idx, propValue));
            }
        }
        return conf;
    }

    private static ProbeConfiguration.ChartSize getGraphSize(Properties prop, int idx) throws ConfigurationException {
        String gsize = prop.getProperty("probe." + idx + ".size");
        if (isEmpty(gsize) || gsize.trim().equalsIgnoreCase("full")) {
            return ProbeConfiguration.ChartSize.FULL_SIZE;
        } else if (gsize.trim().equalsIgnoreCase("half")) {
            return ProbeConfiguration.ChartSize.HALF_SIZE;
        } else {
            throw new ConfigurationException(String.format("Unsupported probe #%s size: %s, aborting", idx, gsize));
        }
    }

    private static String getProbeDevice(Properties prop, int idx) throws ConfigurationException {
        String device = prop.getProperty("probe." + idx + ".device");
        if (isEmpty(device)) {
            throw new ConfigurationException(String.format("Illegal probe list: probe #%s requires mandatory device name, aborting", idx));
        } else {
            return device.trim();
        }
    }

    private static String getProbeDeviceList(Properties prop, int idx) throws ConfigurationException {
        String device = prop.getProperty("probe." + idx + ".device");
        if (isEmpty(device) || isEmpty(device.replaceAll("\\+", ""))) {
            throw new ConfigurationException(String.format("Illegal probe list: probe #%s requires mandatory device name, aborting", idx));
        } else {
            return device.trim().replaceAll("\\s", "");
        }
    }

    private static String getProbeLabel(Properties prop, int idx) {
        String label = prop.getProperty("probe." + idx + ".label");
        if (!isEmpty(label)) {
            return label.trim();
        }
        return null;
    }

}
