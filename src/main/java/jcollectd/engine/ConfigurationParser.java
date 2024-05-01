package jcollectd.engine;

import jcollectd.common.dto.config.*;
import jcollectd.common.exception.ConfigurationException;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Optional;

import static jcollectd.common.CommonUtils.YAML_OBJECT_MAPPER;
import static jcollectd.common.CommonUtils.isEmpty;
import static jcollectd.common.dto.config.ChartSize.FULL;
import static jcollectd.common.dto.config.OperatingSystem.FREEBSD;
import static jcollectd.common.dto.config.OperatingSystem.LINUX;
import static jcollectd.common.dto.config.ProbeType.*;
import static org.apache.logging.log4j.Level.DEBUG;

@Log4j2
public class ConfigurationParser {

    public static AppConfig parse(String[] args) throws ConfigurationException {
        log.info("Parsing JCollectd configuration");
        if (args.length < 1) {
            throw new ConfigurationException("Please provide path to configuration file as parameter");
        }
        if (!Files.isReadable(Path.of(args[0]))) {
            throw new ConfigurationException("Configuration file does not exist or is not readable");
        }

        AppConfigMapping configMapping;
        try {
            configMapping = YAML_OBJECT_MAPPER.readValue(Path.of(args[0]).toFile(), AppConfigMapping.class);
        } catch (IOException ex) {
            throw new ConfigurationException("Configuration file is not a valid YAML", ex);
        }

        if (Boolean.parseBoolean(configMapping.getVerbose())) {
            Configurator.setRootLevel(DEBUG);
        }

        OperatingSystem os = switch (System.getProperty("os.name")) {
            case "FreeBSD" -> FREEBSD;
            case "Linux" -> LINUX;
            default -> FREEBSD;
//            default -> throw new ConfigurationException(String.format("Unsupported operating system: %s", System.getProperty("os.name")));
        };
        log.info("Operating system: {}", os.getLabel());

        String hostname = Optional.ofNullable(configMapping.getHostname()).orElseGet(() -> {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ex) {
                log.warn("Hostname autodiscovery failed)");
                return "localhost";
            }
        });
        log.info("Hostname: {}", hostname);

        Duration interval = Optional.ofNullable(configMapping.getInterval()).map(s -> {
            try {
                return Duration.parse(s);
            } catch (DateTimeParseException ex) {
                throw new ConfigurationException("Field interval is not a valid duration");
            }
        }).orElse(Duration.ofMinutes(1));
        log.info("Sampling interval: {}", interval);

        if (configMapping.getProbes() == null || configMapping.getProbes().isEmpty()) {
            throw new ConfigurationException("No probe defined");
        }

        ArrayList<Probe> probes = new ArrayList<>(configMapping.getProbes().size());
        for (int i = 0; i < configMapping.getProbes().size(); i++) {
            var probeMapping = configMapping.getProbes().get(i);

            ProbeType type;
            if (isEmpty(probeMapping.getType())) {
                throw new ConfigurationException(String.format("Probe #%s: 'type' parameter is mandatory", i + 1));
            }
            type = ProbeType.of(probeMapping.getType());
            if (type == null) {
                throw new ConfigurationException(String.format("Probe #%s: invalid type '%s'", i + 1, probeMapping.getType()));
            }

            if ((type == NET || type == DISK || type == ZFS) && isEmpty(probeMapping.getDevice())) {
                throw new ConfigurationException(String.format("Probe #%s: 'device' configuration parameter is mandatory for probe type %s", i, type));
            }
            if (os == LINUX && type == ZFS) {
                throw new ConfigurationException(String.format("Probe #%s: type '%s' is not supported on %s operating system", i, type, System.getProperty("os.name")));
            }

            ChartSize size;
            if (isEmpty(probeMapping.getSize())) {
                size = FULL;
            } else {
                size = ChartSize.of(probeMapping.getSize());
            }
            if (size == null) {
                throw new ConfigurationException(String.format("Probe #%s: invalid size '%s'", i + 1, probeMapping.getSize()));
            }

            Probe probe = switch (type) {
                case LOAD, CPU, MEM, GPU -> new Probe(type, size);
                case NET, DISK, ZFS -> new Probe(type, size, probeMapping.getDevice().trim(), isEmpty(probeMapping.getLabel()) ? probeMapping.getDevice().trim() : probeMapping.getLabel().trim());
            };
            if (probes.stream().anyMatch(p -> p.getType() == probe.getType() && (p.getDevice() == null && probe.getDevice() == null) || (p.getDevice() != null && p.getDevice().equals(probe.getDevice())))) {
                throw new IllegalArgumentException(String.format("Probe #%s has duplicate definition", i));
            }
            probes.add(probe);
            log.info("Probe #{}: {}", i + 1, probe.prettyPrint());
        }

        return new AppConfig(os, hostname, interval, probes);
    }

}
