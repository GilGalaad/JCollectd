import { CommonModule } from "@angular/common";
import { AfterViewChecked, Component, OnDestroy, OnInit } from "@angular/core";
import { Title } from "@angular/platform-browser";
import * as echarts from "echarts";
import { Subscription, interval } from "rxjs";
import { Probe, Runtime } from "../../services/api.model";
import { ApiService } from "../../services/api.service";
import { ERROR_MESSAGE, getCpuGpuOptions, getDiskOptions, getLoadOptions, getMemOptions, getNetOptions } from "./dashboard.helper";

@Component({
  selector: "app-dashboard",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./dashboard.component.html",
  styleUrl: "./dashboard.component.scss",
})
export class DashboardComponent implements OnInit, AfterViewChecked, OnDestroy {
  runtime: Runtime | null = null;
  probes: Probe[] = [];
  datasets: [][][] = [];
  charts: echarts.ECharts[] = [];
  errorMessage: string | null = null;
  timer$: Subscription | null = null;

  constructor(
    private title: Title,
    private apiService: ApiService,
  ) {}

  ngOnInit(): void {
    this.fetchData();
  }

  ngAfterViewChecked(): void {
    // init charts only if: 1. not done before, 2. probes are loaded, 3. all divs have been created
    if (this.charts.length === 0 && this.probes.length > 0 && this.probes.length === document.getElementsByClassName("chart-container").length) {
      this.initCharts();
    }
  }

  private fetchData() {
    this.apiService.getRuntime().subscribe({
      next: (response) => {
        this.runtime = response;
        this.title.setTitle(this.runtime.hostname);
        this.probes = this.runtime.probes;
        this.datasets = this.runtime.datasets;
        this.errorMessage = null;
        if (this.charts.length > 0) {
          this.updateCharts();
        }
      },
      error: (_) => {
        this.errorMessage = ERROR_MESSAGE;
      },
    });
  }

  private initCharts() {
    this.probes.forEach((probe, idx) => {
      const chart = echarts.init(document.getElementById("chart" + idx));
      chart.setOption(this.createChartOption(probe, idx), true);
      this.charts.push(chart);
    });
    this.timer$ = interval(60000).subscribe(() => this.fetchData());
  }

  private updateCharts() {
    this.probes.forEach((probe, idx) => {
      this.charts[idx].setOption(this.createChartOption(probe, idx), false);
    });
  }

  private createChartOption(probe: Probe, idx: number) {
    switch (probe.type) {
      case "LOAD":
        return getLoadOptions(probe, this.datasets[idx]);
      case "CPU":
      case "GPU":
        return getCpuGpuOptions(probe, this.datasets[idx]);
      case "MEM":
        return getMemOptions(probe, this.datasets[idx]);
      case "NET":
        return getNetOptions(probe, this.datasets[idx]);
      case "DISK":
      case "ZFS":
        return getDiskOptions(probe, this.datasets[idx]);
    }
  }

  ngOnDestroy(): void {
    for (let chart of this.charts) {
      chart.dispose();
    }
    if (this.timer$) {
      this.timer$.unsubscribe();
    }
  }
}
