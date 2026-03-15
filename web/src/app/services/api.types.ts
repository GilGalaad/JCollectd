export interface Runtime {
  hostname: string;
  interval: number;
  probes: Probe[];
  collectTms: Date | null;
  collectElapsed: string | null;
  persistElapsed: string | null;
  reportElapsed: string | null;
  datasets: [][][];
}

export interface Probe {
  type: "LOAD" | "CPU" | "MEM" | "NET" | "DISK" | "ZFS" | "GPU";
  size: "FULL" | "HALF";
  device: string | null;
  label: string | null;
}
