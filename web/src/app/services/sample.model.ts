export interface Runtime {
  hostname: string;
  probes: Probe[];
  collectTms?: Date | null;
  collectElapsed?: string | null;
  persistElapsed?: string | null;
}

export interface Probe {
  type: "LOAD" | "CPU" | "MEM" | "NET" | "DISK" | "ZFS" | "GPU";
  size: "FULL" | "HALF";
  device: string | null;
  label: string | null;
}
