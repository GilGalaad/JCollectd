import * as echarts from "echarts";
import { Probe } from "../../services/api.model";

export const ERROR_MESSAGE = "Error while fetching data, connection to backend failed";

const TEXT_COLOR = "gainsboro";
const FONT_FAMILY = "Verdana, Geneva, sans-serif";
const INACTIVE_TEXT_COLOR = "dimgray";
const LINE_COLOR = "silver";
const AREA_OPACITY = 0.5;

export function createChartOption(probe: Probe, source: [][]): echarts.EChartsOption {
  const ret: echarts.EChartsOption = {};

  ret.title = {
    text: getChartTitle(probe),
    textStyle: {
      color: TEXT_COLOR,
      fontFamily: FONT_FAMILY,
    },
    left: "16",
    top: "16",
  };

  ret.legend = {
    top: "16",
    inactiveColor: INACTIVE_TEXT_COLOR,
    textStyle: {
      color: TEXT_COLOR,
      fontFamily: FONT_FAMILY,
    },
    icon: "roundRect",
  };

  ret.grid = {
    left: "32",
    top: "75",
    right: "32",
    bottom: "32",
    containLabel: true,
  };

  ret.xAxis = [
    {
      type: "time",
      axisLine: {
        lineStyle: {
          color: LINE_COLOR,
        },
      },
    },
  ];

  ret.yAxis = [
    {
      type: "value",
      axisLine: {
        lineStyle: {
          color: LINE_COLOR,
        },
      },
      axisLabel: {
        margin: 16,
        formatter: probe.type === "CPU" || probe.type === "GPU" ? "{value}%" : undefined,
      },
      splitLine: {
        lineStyle: {
          color: LINE_COLOR,
        },
      },
    },
  ];

  ret.tooltip = {
    trigger: "axis",
    axisPointer: {
      label: {
        formatter: function (obj: any) {
          const label = Intl.DateTimeFormat(undefined, {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "numeric",
            minute: "numeric",
            second: "numeric",
          })
            .format(new Date(obj.value))
            .replace(",", "");
          return label;
        },
      },
    },
    textStyle: {
      fontFamily: FONT_FAMILY,
    },
  };

  ret.dataset = {
    source: source,
    dimensions: getChartDimensions(probe),
  };

  ret.series = getChartSeries(probe);

  ret.color = getChartColor(probe);

  return ret;
}

function getChartTitle(probe: Probe) {
  switch (probe.type) {
    case "LOAD":
      return "Average load";
    case "CPU":
      return "CPU usage";
    case "MEM":
      return "Memory usage (MiB)";
    case "NET":
      return "Network usage: " + probe.label + " (Mbps)";
    case "DISK":
      return "Disk usage: " + probe.label + " (MiB/s)";
    case "ZFS":
      return "ZFS dataset usage: " + probe.label + " (MiB/s)";
    case "GPU":
      return "GPU usage";
  }
}

function getChartDimensions(probe: Probe) {
  switch (probe.type) {
    case "LOAD":
      return ["timestamp", "1 minute", "5 minutes", "15 minutes"];
    case "CPU":
      return ["timestamp", "CPU"];
    case "MEM":
      return ["timestamp", "Used memory", "Cache", "Swap"];
    case "NET":
      return ["timestamp", "TX", "RX"];
    case "DISK":
    case "ZFS":
      return ["timestamp", "Read", "Write"];
    case "GPU":
      return ["timestamp", "GPU"];
  }
}

function getChartColor(probe: Probe) {
  switch (probe.type) {
    case "LOAD":
    case "MEM":
      return ["#3366cc", "#ff9900", "#dc3912"];
    case "CPU":
    case "GPU":
      return ["#3366cc"];
    case "NET":
    case "DISK":
    case "ZFS":
      return ["#109618", "#3366cc"];
  }
}

function getChartSeries(probe: Probe): echarts.SeriesOption[] {
  switch (probe.type) {
    case "LOAD":
      return [
        {
          type: "line",
          name: getChartDimensions(probe)[1],
          showSymbol: false,
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[1],
          },
          z: 0,
        },
        {
          type: "line",
          name: getChartDimensions(probe)[2],
          showSymbol: false,
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[2],
          },
          z: 1,
        },
        {
          type: "line",
          name: getChartDimensions(probe)[3],
          showSymbol: false,
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[3],
          },
          z: 2,
        },
      ];
    case "CPU":
    case "GPU":
      return [
        {
          type: "line",
          name: getChartDimensions(probe)[1],
          showSymbol: false,
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[1],
          },
        },
      ];
    case "MEM":
      return [
        {
          type: "line",
          name: getChartDimensions(probe)[1],
          showSymbol: false,
          stack: "total",
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[1],
          },
        },
        {
          type: "line",
          name: getChartDimensions(probe)[2],
          showSymbol: false,
          stack: "total",
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[2],
          },
        },
        {
          type: "line",
          name: getChartDimensions(probe)[3],
          showSymbol: false,
          stack: "total",
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[3],
          },
        },
      ];
    case "NET":
      return [
        {
          type: "line",
          name: getChartDimensions(probe)[1],
          showSymbol: false,
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[2],
          },
        },
        {
          type: "line",
          name: getChartDimensions(probe)[2],
          showSymbol: false,
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[1],
          },
        },
      ];
    case "DISK":
    case "ZFS":
      return [
        {
          type: "line",
          name: getChartDimensions(probe)[1],
          showSymbol: false,
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[1],
          },
        },
        {
          type: "line",
          name: getChartDimensions(probe)[2],
          showSymbol: false,
          areaStyle: { opacity: AREA_OPACITY },
          encode: {
            x: getChartDimensions(probe)[0],
            y: getChartDimensions(probe)[2],
          },
        },
      ];
  }
}

export function updateChartOption(source: [][]): echarts.EChartsOption {
  return {
    dataset: {
      source: source,
    },
  };
}
