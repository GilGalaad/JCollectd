import { CommonModule } from "@angular/common";
import { AfterViewChecked, Component, OnDestroy, OnInit } from "@angular/core";
import { Title } from "@angular/platform-browser";
import * as echarts from "echarts";
import { Subscription } from "rxjs";
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
  timer$: Subscription | null = null;
  errorMessage: string | null = null;

  constructor(
    private title: Title,
    private apiService: ApiService,
  ) {}

  ngOnInit(): void {
    this.fetchData();
  }

  ngAfterViewChecked(): void {
    if (this.charts.length === 0 && this.probes.length > 0 && this.probes.length === document.getElementsByClassName("chart-container").length) {
      // init charts with loading template
      this.probes.forEach((probe, idx) => {
        const chart = echarts.init(document.getElementById("chart" + idx));
        chart.setOption(this.createChartOption(probe, idx), true);
        this.charts.push(chart);
      });
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
        // this.timer$ = interval(60000).subscribe(() => this.refresh());
      },
      error: (_) => {
        this.errorMessage = ERROR_MESSAGE;
      },
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
