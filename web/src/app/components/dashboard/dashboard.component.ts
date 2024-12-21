import { CommonModule } from "@angular/common";
import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from "@angular/core";
import { Title } from "@angular/platform-browser";
import * as echarts from "echarts";
import { Subscription, interval } from "rxjs";
import { Probe } from "../../services/api.model";
import { ApiService } from "../../services/api.service";
import { ERROR_MESSAGE, createChartOption, updateChartOption } from "./dashboard.helper";

@Component({
  selector: "app-dashboard",
  imports: [CommonModule],
  templateUrl: "./dashboard.component.html",
  styleUrl: "./dashboard.component.scss",
})
export class DashboardComponent implements OnInit, OnDestroy {
  hostname: string | null = null;
  interval: number = 60;
  probes: Probe[] = [];
  collectTms: Date | null = null;
  collectElapsed: string | null = null;
  persistElapsed: string | null = null;
  reportElapsed: string | null = null;
  datasets: [][][] = [];

  charts: echarts.ECharts[] = [];
  errorMessage: string | null = null;
  timer$: Subscription | null = null;

  constructor(
    private title: Title,
    private apiService: ApiService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.initData();
  }

  private initData() {
    this.apiService.getRuntime().subscribe({
      next: (response) => {
        this.hostname = response.hostname;
        this.interval = response.interval;
        this.probes = response.probes;
        this.collectTms = response.collectTms;
        this.collectElapsed = response.collectElapsed;
        this.persistElapsed = response.persistElapsed;
        this.reportElapsed = response.reportElapsed;
        this.datasets = response.datasets;
        this.title.setTitle(this.hostname);
        this.errorMessage = null;

        this.cdr.detectChanges();
        this.initCharts();
      },
      error: (_) => {
        this.errorMessage = ERROR_MESSAGE;
      },
    });
  }

  private initCharts() {
    this.probes.forEach((probe, idx) => {
      const chart = echarts.init(document.getElementById("chart" + idx));
      const option = createChartOption(probe, this.datasets[idx]);
      chart.setOption(option, true);
      this.charts.push(chart);
    });
    this.timer$ = interval(this.interval * 1000).subscribe(() => this.updateData());
  }

  private updateData() {
    this.apiService.getRuntime().subscribe({
      next: (response) => {
        this.collectTms = response.collectTms;
        this.collectElapsed = response.collectElapsed;
        this.persistElapsed = response.persistElapsed;
        this.reportElapsed = response.reportElapsed;
        this.datasets = response.datasets;
        this.errorMessage = null;
        this.updateCharts();
      },
      error: (_) => {
        this.errorMessage = ERROR_MESSAGE;
      },
    });
  }

  private updateCharts() {
    this.charts.forEach((chart, idx) => {
      const option = updateChartOption(this.datasets[idx]);
      chart.setOption(option, false);
    });
  }

  @HostListener("window:resize", ["$event"])
  onResize(event: Event) {
    this.charts.forEach((chart) => chart.resize());
  }

  ngOnDestroy(): void {
    this.charts.forEach((chart) => chart.dispose());
    if (this.timer$) {
      this.timer$.unsubscribe();
    }
  }
}
