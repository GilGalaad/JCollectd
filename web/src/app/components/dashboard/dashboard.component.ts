import { CommonModule } from "@angular/common";
import { AfterViewChecked, Component, OnDestroy, OnInit } from "@angular/core";
import { Title } from "@angular/platform-browser";
import * as echarts from "echarts";
import { Subscription, forkJoin, interval, switchMap } from "rxjs";
import { Probe, Runtime } from "../../services/sample.model";
import { SampleService } from "../../services/sample.service";
import { ERROR_MESSAGE, LOADING_CHART, getCpuGpuOptions, getDiskOptions, getLoadOptions, getMemOptions, getNetOptions } from "./dashboard.helper";

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

  charts: echarts.ECharts[] = [];
  datasets: [][][] = [];
  timer$: Subscription | null = null;

  startFetchTms: number | null = null;
  endFetchTms: number | null = null;
  errorMessage: string | null = null;

  constructor(
    private title: Title,
    private sampleService: SampleService,
  ) {}

  ngOnInit(): void {
    this.fetchRuntime();
  }

  ngAfterViewChecked(): void {
    if (this.charts.length === 0 && this.probes.length > 0 && this.probes.length === document.getElementsByClassName("chart-container").length) {
      // init charts with loading template
      this.probes.forEach((_, idx) => {
        const chart = echarts.init(document.getElementById("chart" + idx));
        chart.setOption(LOADING_CHART);
        this.charts.push(chart);
      });

      // concurrently fetch all datasets
      this.fetchDatasets();
    }
  }

  private fetchRuntime() {
    this.sampleService.getRuntime().subscribe({
      next: (response) => {
        this.runtime = response;
        this.title.setTitle(this.runtime.hostname);
        this.probes = this.runtime.probes;
        this.errorMessage = null;
      },
      error: (_) => {
        this.errorMessage = ERROR_MESSAGE;
      },
    });
  }

  private fetchDatasets() {
    const datasets$ = this.probes.map((_, idx) => this.sampleService.getDataset(idx));
    this.endFetchTms = null;
    this.startFetchTms = performance.now();
    forkJoin(datasets$).subscribe({
      next: (response) => {
        this.endFetchTms = performance.now();
        this.datasets = response;
        this.errorMessage = null;
        this.createOptions(true);
        this.timer$ = interval(60000).subscribe(() => this.refresh());
      },
      error: (_) => {
        this.errorMessage = ERROR_MESSAGE;
      },
    });
  }

  private refresh() {
    this.sampleService
      .getRuntime()
      .pipe(
        switchMap((response) => {
          this.runtime = response;
          const datasets$ = this.probes.map((_, idx) => this.sampleService.getDataset(idx));
          this.endFetchTms = null;
          this.startFetchTms = performance.now();
          return forkJoin(datasets$);
        }),
      )
      .subscribe({
        next: (response) => {
          this.endFetchTms = performance.now();
          this.datasets = response;
          this.errorMessage = null;
          this.createOptions(false);
        },
        error: (_) => {
          this.errorMessage = ERROR_MESSAGE;
        },
      });
  }

  private createOptions(overwrite: boolean) {
    this.probes.forEach((probe, idx) => {
      switch (probe.type) {
        case "LOAD":
          this.charts[idx].setOption(getLoadOptions(probe, this.datasets[idx]), overwrite);
          break;
        case "CPU":
        case "GPU":
          this.charts[idx].setOption(getCpuGpuOptions(probe, this.datasets[idx]), overwrite);
          break;
        case "MEM":
          this.charts[idx].setOption(getMemOptions(probe, this.datasets[idx]), overwrite);
          break;
        case "NET":
          this.charts[idx].setOption(getNetOptions(probe, this.datasets[idx]), overwrite);
          break;
        case "DISK":
        case "ZFS":
          this.charts[idx].setOption(getDiskOptions(probe, this.datasets[idx]), overwrite);
          break;
      }
    });
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
