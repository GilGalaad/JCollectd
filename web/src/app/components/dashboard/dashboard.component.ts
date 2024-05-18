import { CommonModule } from "@angular/common";
import { AfterViewChecked, Component, HostListener, OnDestroy, OnInit } from "@angular/core";
import { Title } from "@angular/platform-browser";
import * as echarts from "echarts";
import { Subscription, interval } from "rxjs";
import { Probe } from "../../services/api.model";
import { ApiService } from "../../services/api.service";
import { ERROR_MESSAGE, createChartOption, updateChartOption } from "./dashboard.helper";

@Component({
  selector: "app-dashboard",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./dashboard.component.html",
  styleUrl: "./dashboard.component.scss",
})
export class DashboardComponent implements OnInit, AfterViewChecked, OnDestroy {
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
      },
      error: (_) => {
        this.errorMessage = ERROR_MESSAGE;
      },
    });
  }

  ngAfterViewChecked(): void {
    // init charts only if: 1. not done before, 2. probes are loaded, 3. all divs have been created
    if (this.charts.length === 0 && this.probes.length > 0 && this.probes.length === document.getElementsByClassName("chart-container").length) {
      this.initCharts();
    }
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
    if (this.charts.length > 0) {
      this.charts.forEach((chart, _) => chart.resize());
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
