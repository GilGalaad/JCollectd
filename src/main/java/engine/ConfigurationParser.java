package engine;

import static common.CommonUtils.isEmpty;
import common.exception.ConfigurationException;
import engine.config.CollectConfiguration;
import static engine.config.CollectConfiguration.DbEngine.SQLITE;
import static engine.config.CollectConfiguration.OperatingSystem.FREEBSD;
import static engine.config.CollectConfiguration.OperatingSystem.LINUX;
import engine.config.ProbeConfiguration;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConfigurationParser {

    public static CollectConfiguration parse(String[] args) throws ConfigurationException {
        if (args.length < 1) {
            throw new ConfigurationException("Please provide path to configuration file as parameter");
        }

        CollectConfiguration conf = new CollectConfiguration();
        String propValue;

        // checking OS
        if (System.getProperty("os.name").equals("FreeBSD")) {
            conf.setOs(FREEBSD);
        } else if (System.getProperty("os.name").equals("Linux")) {
            conf.setOs(LINUX);
        } else {
            throw new ConfigurationException(String.format("Unsupported platform: %s", System.getProperty("os.name")));
        }

        // loading parameters
        Properties prop = new Properties();
        try (FileInputStream in = new FileInputStream(args[0])) {
            prop.load(in);
        } catch (Exception ex) {
            throw new ConfigurationException("Configuration file not found or not readable at specified path", ex);
        }

        // parsing and sanitizing global properties
        // SQLite is the only supported database at the moment
        conf.setDbEngine(SQLITE);

        propValue = prop.getProperty("dbPath");
        if (isEmpty(propValue)) {
            throw new ConfigurationException("Mandatory parameter 'dbPath' not found in configuration file", null);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue).normalize();
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        throw new ConfigurationException("Parameter 'dbPath' set to non existent or non writable directory");
                    }
                }
                log.info("Parameter dbpath -> {}", p.toString());
                conf.setDbPath(p);
            } catch (RuntimeException ex) {
                throw new ConfigurationException("Parameter 'dbpath' set to an illegal value", ex);
            }
        }

        propValue = prop.getProperty("webPath");
        if (isEmpty(propValue)) {
            log.info("Parameter webpath -> HTML report disabled");
            conf.setWebPath(null);
        } else {
            propValue = propValue.trim();
            try {
                Path p = Paths.get(propValue);
                if (p.getParent() != null) {
                    File f = p.getParent().toFile();
                    if (!f.exists() || !f.isDirectory() || !f.canWrite()) {
                        throw new ConfigurationException("Parameter 'webPath' set to non existent or non writable directory");
                    }
                }
                log.info("Parameter webpath -> {}", p.toString());
                conf.setWebPath(p);
            } catch (RuntimeException ex) {
                throw new ConfigurationException("Parameter 'webPath' set to an illegal value", ex);
            }
        }

        propValue = (prop.getProperty("hostname"));
        if (isEmpty(propValue)) {
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                log.info("Parameter hostname -> {} (autodiscovered)", hostname);
                conf.setHostname(hostname);
            } catch (UnknownHostException ex) {
                log.info("Parameter hostname -> localhost (autodiscovery failed)");
                conf.setHostname("localhost");
            }
        } else {
            log.info("Parameter hostname -> {}", propValue.trim());
            conf.setHostname(propValue.trim());
        }

        propValue = (prop.getProperty("reportHours"));
        if (isEmpty(propValue)) {
            log.info("Parameter reportHours -> {} (default)", 12);
            conf.setReportHours(12);
        } else {
            try {
                int reportHours = Integer.parseUnsignedInt(propValue.trim());
                log.info("Parameter reportHours -> {}", reportHours);
                conf.setReportHours(reportHours);
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Parameter 'reportHours' set to an illegal value", ex);
            }
        }

        propValue = (prop.getProperty("retentionHours"));
        if (isEmpty(propValue)) {
            log.info("Parameter retentionHours -> {} (defaulting to reportHours)", conf.getReportHours());
            conf.setRetentionHours(conf.getReportHours());
        } else {
            try {
                int retentionHours = Integer.parseInt(propValue.trim());
                if (retentionHours >= conf.getReportHours()) {
                    log.info("Parameter retentionHours -> {}", retentionHours);
                    conf.setRetentionHours(retentionHours);
                } else if (retentionHours >= 0 && retentionHours < conf.getReportHours()) {
                    log.info("Parameter retentionHours -> {} (ceiling to reportHours)", conf.getReportHours());
                    conf.setRetentionHours(conf.getReportHours());
                } else {
                    log.info("Parameter retentionHours -> database cleanup disabled", conf.getReportHours());
                    conf.setRetentionHours(-1);
                }
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Parameter 'retentionHours' set to an illegal value", ex);
            }
        }

        propValue = (prop.getProperty("interval"));
        if (isEmpty(propValue)) {
            log.info("Parameter interval -> {} (default)", 60);
            conf.setInterval(60);
        } else {
            try {
                int interval = Integer.parseUnsignedInt(propValue.trim());
                log.info("Parameter interval -> {}", interval);
                conf.setInterval(interval);
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Parameter 'interval' set to an illegal value", ex);
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
        Collections.sort(plist, (s1, s2) -> {
            Integer idx1 = Integer.parseInt(s1.substring(s1.indexOf(".") + 1, s1.lastIndexOf(".")));
            Integer idx2 = Integer.parseInt(s2.substring(s2.indexOf(".") + 1, s2.lastIndexOf(".")));
            return idx1.compareTo(idx2);
        });
        ArrayList<ProbeConfiguration> probeConfList = new ArrayList<>(plist.size());
        conf.setProbeConfigList(probeConfList);
        log.info("Found {} probe definitions", plist.size());
        for (int i = 0; i < plist.size(); i++) {
            // idx is 1-based probe number
            int idx = Integer.parseInt(plist.get(i).substring(plist.get(i).indexOf(".") + 1, plist.get(i).lastIndexOf(".")));
            if (idx != i + 1) {
                throw new ConfigurationException(String.format("Probe #%s missing", i + 1));
            }

            propValue = prop.getProperty(plist.get(i));
            if (isEmpty(propValue)) {
                throw new ConfigurationException(String.format("Probe #%s undefined type", idx));
            } else if (propValue.equalsIgnoreCase("load")) {
                log.info("Probe #{} -> {}, {}", idx, ProbeConfiguration.ProbeType.LOAD, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.LOAD, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("cpu")) {
                log.info("Probe #{} -> {}, {}", idx, ProbeConfiguration.ProbeType.CPU, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.CPU, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("mem")) {
                log.info("Probe #{} -> {}, {}", idx, ProbeConfiguration.ProbeType.MEM, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.MEM, getGraphSize(prop, idx)));
            } else if (propValue.equalsIgnoreCase("net")) {
                log.info("Probe #{} -> {}, {}, {}, {}", idx, ProbeConfiguration.ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.NET, getGraphSize(prop, idx), getProbeDevice(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx)));
            } else if (propValue.equalsIgnoreCase("disk")) {
                log.info("Probe #{} -> {}, {}, {}, {}", idx, ProbeConfiguration.ProbeType.DISK, getGraphSize(prop, idx), getProbeDeviceList(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.DISK, getGraphSize(prop, idx), getProbeDeviceList(prop, idx),
                        !isEmpty(getProbeLabel(prop, idx)) ? getProbeLabel(prop, idx) : getProbeDevice(prop, idx)));
            } else if (propValue.equalsIgnoreCase("gpu")) {
                log.info("Probe #{} -> {}, {}", idx, ProbeConfiguration.ProbeType.GPU, getGraphSize(prop, idx));
                probeConfList.add(new ProbeConfiguration(ProbeConfiguration.ProbeType.GPU, getGraphSize(prop, idx)));
            } else {
                throw new ConfigurationException(String.format("Probe #%s unsupported type", idx));
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
            throw new ConfigurationException(String.format("Probe #%s unsupported size", idx));
        }
    }

    private static String getProbeDevice(Properties prop, int idx) throws ConfigurationException {
        String device = prop.getProperty("probe." + idx + ".device");
        if (isEmpty(device)) {
            throw new ConfigurationException(String.format("Probe #%s requires mandatory device name", idx));
        } else {
            return device.trim();
        }
    }

    private static String getProbeDeviceList(Properties prop, int idx) throws ConfigurationException {
        String device = prop.getProperty("probe." + idx + ".device");
        if (isEmpty(device) || isEmpty(device.replaceAll("\\+", ""))) {
            throw new ConfigurationException(String.format("Probe #%s requires mandatory device name", idx));
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
