package jcollectd.common.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfigMapping {

    private String verbose;
    private String hostname;
    private String interval;
    private String retention;
    private String port;
    private List<ProbeMapping> probes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProbeMapping {

        private String type;
        private String size;
        private String device;
        private String label;

    }

}
