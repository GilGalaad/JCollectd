import { CommonModule } from "@angular/common";
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit, signal } from "@angular/core";
import { Title } from "@angular/platform-browser";
import * as echarts from "echarts";
import { interval, Subscription } from "rxjs";
import { Api } from "../../services/api";
import { Probe } from "../../services/api.types";
import { createChartOption, ERROR_MESSAGE, updateChartOption } from "./dashboard.helper";

@Component({
  selector: "app-dashboard",
  imports: [CommonModule],
  templateUrl: "./dashboard.html",
  styleUrl: "./dashboard.css",
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard implements OnInit, OnDestroy {
  readonly hostname = signal<string | null>(null);
  readonly collectTms = signal<Date | null>(null);
  readonly probes = signal<Probe[]>([]);
  readonly collectElapsed = signal<string | null>(null);
  readonly persistElapsed = signal<string | null>(null);
  readonly reportElapsed = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  private datasets: [][][] = [];
  private charts: echarts.ECharts[] = [];
  private timer$: Subscription | null = null;

  constructor(
    private title: Title,
    private api: Api,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadInitialData();
  }

  private loadInitialData() {
    this.api.getRuntime().subscribe({
      next: (response) => {
        this.title.setTitle(response.hostname);
        this.hostname.set(response.hostname);
        this.collectTms.set(response.collectTms);
        this.probes.set(response.probes);
        this.collectElapsed.set(response.collectElapsed);
        this.persistElapsed.set(response.persistElapsed);
        this.reportElapsed.set(response.reportElapsed);
        this.errorMessage.set(null);
        this.datasets = response.datasets;
        this.cdr.detectChanges();
        this.initCharts();
        this.startTimer(response.interval);
      },
      error: (_) => {
        this.errorMessage.set(ERROR_MESSAGE);
      },
    });
  }

  private initCharts() {
    this.probes().forEach((probe, idx) => {
      const chart = echarts.init(document.getElementById("chart" + idx));
      const option = createChartOption(probe, this.datasets[idx]);
      chart.setOption(option, true);
      this.charts.push(chart);
    });
  }

  private startTimer(i: number) {
    this.timer$ = interval(i * 1000).subscribe(() => this.updateData());
  }

  private updateData() {
    this.api.getRuntime().subscribe({
      next: (response) => {
        this.collectTms.set(response.collectTms);
        this.collectElapsed.set(response.collectElapsed);
        this.persistElapsed.set(response.persistElapsed);
        this.reportElapsed.set(response.reportElapsed);
        this.errorMessage.set(null);
        this.datasets = response.datasets;
        this.updateCharts();
      },
      error: (_) => {
        this.errorMessage.set(ERROR_MESSAGE);
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
    this.timer$?.unsubscribe();
  }
}
