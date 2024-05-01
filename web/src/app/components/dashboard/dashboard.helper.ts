import * as echarts from "echarts";
import { Probe } from "../../services/api.model";

export const ERROR_MESSAGE = "Error while fetching data, connection to backend failed";

const TEXT_COLOR = "gainsboro";
const LINE_COLOR = "silver";

function getTitle(text: string): echarts.TitleComponentOption {
  return {
    left: "16",
    top: "16",
    text: text,
    textStyle: getTextStyle(),
  };
}

function getLegend(data: string[]): echarts.LegendComponentOption {
  return {
    top: "16",
    inactiveColor: "dimgray",
    textStyle: getTextStyle(),
    icon: "roundRect",
    data: data,
  };
}

function getTextStyle() {
  return {
    color: TEXT_COLOR,
  };
}

function getGrid(): echarts.GridComponentOption {
  return {
    top: "75",
    bottom: "32",
    left: "32",
    right: "32",
    containLabel: true,
  };
}

function getXAxis(): echarts.XAXisComponentOption[] {
  return [
    {
      type: "time",
      axisLine: {
        lineStyle: {
          color: LINE_COLOR,
        },
      },
    },
  ];
}

function getYAxis(): echarts.YAXisComponentOption[] {
  return [
    {
      type: "value",
      axisLine: {
        lineStyle: {
          color: LINE_COLOR,
        },
      },
      axisLabel: {
        margin: 16,
      },
      splitLine: {
        lineStyle: {
          color: LINE_COLOR,
        },
      },
    },
  ];
}

function getYAxisPercent(): echarts.YAXisComponentOption[] {
  const ret = getYAxis() as any;
  ret[0].axisLabel.formatter = "{value}%";
  return ret;
}

function getTooltip(): echarts.TooltipComponentOption {
  return {
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
  };
}

export function getLoadOptions(_: Probe, source: [][]): echarts.EChartsOption {
  return {
    color: ["#3366cc", "#ff9900", "#dc3912"],
    title: getTitle("Average load"),
    legend: getLegend(["1 minute", "5 minutes", "15 minutes"]),
    grid: getGrid(),
    xAxis: getXAxis(),
    yAxis: getYAxis(),
    tooltip: getTooltip(),
    dataset: {
      source: source,
      dimensions: ["timestamp", "1 minute", "5 minutes", "15 minutes"],
    },
    series: [
      {
        name: "1 minute",
        type: "line",
        encode: {
          x: "timestamp",
          y: "1 minute",
        },
        showSymbol: false,
        z: 2,
      },
      {
        name: "5 minutes",
        type: "line",
        encode: {
          x: "timestamp",
          y: "5 minutes",
        },
        showSymbol: false,
        z: 1,
      },
      {
        name: "15 minutes",
        type: "line",
        encode: {
          x: "timestamp",
          y: "15 minutes",
        },
        showSymbol: false,
        z: 0,
      },
    ],
  };
}

export function getCpuGpuOptions(probe: Probe, source: [][]): echarts.EChartsOption {
  return {
    color: ["#3366cc"],
    title: getTitle(probe.type + " usage"),
    legend: getLegend([probe.type]),
    grid: getGrid(),
    xAxis: getXAxis(),
    yAxis: getYAxisPercent(),
    tooltip: getTooltip(),
    dataset: {
      source: source,
      dimensions: ["timestamp", probe.type],
    },
    series: [
      {
        name: probe.type,
        type: "line",
        encode: {
          x: "timestamp",
          y: probe.type,
        },
        showSymbol: false,
        areaStyle: {},
      },
    ],
  };
}

export function getMemOptions(_: Probe, source: [][]): echarts.EChartsOption {
  return {
    color: ["#3366cc", "#ff9900", "#dc3912"],
    title: getTitle("Memory usage (MiB)"),
    legend: getLegend(["Used memory", "Cache", "Swap"]),
    grid: getGrid(),
    xAxis: getXAxis(),
    yAxis: getYAxis(),
    tooltip: getTooltip(),
    dataset: {
      source: source,
      dimensions: ["timestamp", "Used memory", "Cache", "Swap"],
    },
    series: [
      {
        name: "Used memory",
        type: "line",
        stack: "Total",
        areaStyle: {},
        encode: {
          x: "timestamp",
          y: "Used memory",
        },
        showSymbol: false,
      },
      {
        name: "Cache",
        type: "line",
        stack: "Total",
        areaStyle: {},
        encode: {
          x: "timestamp",
          y: "Cache",
        },
        showSymbol: false,
      },
      {
        name: "Swap",
        type: "line",
        stack: "Total",
        areaStyle: {},
        encode: {
          x: "timestamp",
          y: "Swap",
        },
        showSymbol: false,
      },
    ],
  };
}

export function getNetOptions(probe: Probe, source: [][]): echarts.EChartsOption {
  return {
    color: ["#109618", "#3366cc"],
    title: getTitle("Network usage: " + probe.label + " (Mbps)"),
    legend: getLegend(["TX", "RX"]),
    grid: getGrid(),
    xAxis: getXAxis(),
    yAxis: getYAxis(),
    tooltip: getTooltip(),
    dataset: {
      source: source,
      dimensions: ["timestamp", "RX", "TX"],
    },
    series: [
      {
        name: "TX",
        type: "line",
        areaStyle: {},
        encode: {
          x: "timestamp",
          y: "TX",
        },
        showSymbol: false,
      },
      {
        name: "RX",
        type: "line",
        areaStyle: {},
        encode: {
          x: "timestamp",
          y: "RX",
        },
        showSymbol: false,
      },
    ],
  };
}

export function getDiskOptions(probe: Probe, source: [][]): echarts.EChartsOption {
  return {
    color: ["#109618", "#3366cc"],
    title: probe.type === "DISK" ? getTitle("Disk usage: " + probe.label + " (MiB/s)") : getTitle("ZFS dataset usage: " + probe.label + " (MiB/s)"),
    legend: getLegend(["Read", "Write"]),
    grid: getGrid(),
    xAxis: getXAxis(),
    yAxis: getYAxis(),
    tooltip: getTooltip(),
    dataset: {
      source: source,
      dimensions: ["timestamp", "Read", "Write"],
    },
    series: [
      {
        name: "Read",
        type: "line",
        areaStyle: {},
        encode: {
          x: "timestamp",
          y: "Read",
        },
        showSymbol: false,
      },
      {
        name: "Write",
        type: "line",
        areaStyle: {},
        encode: {
          x: "timestamp",
          y: "Write",
        },
        showSymbol: false,
      },
    ],
  };
}
