import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { OceanApiService } from '../../services/ocean-api.service';
import { ScanData, PositionData, MeasurementPoint } from '../../models/models';
import { Chart, registerables } from 'chart.js';
import { interval, Subscription } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';

Chart.register(...registerables);

@Component({
  standalone: false,
  selector: 'app-data-view',
  templateUrl: './data-view.component.html',
  styleUrls: ['./data-view.component.scss']
})
export class DataViewComponent implements OnInit, OnDestroy {

  @ViewChild('depthChart', { static: false }) depthChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('heatmapCanvas', { static: false }) heatmapRef!: ElementRef<HTMLCanvasElement>;

  scans: ScanData[] = [];
  measurements: MeasurementPoint[] = [];
  loading = true;

  private pollSub?: Subscription;
  private depthChart?: Chart;
  private heatChart?: Chart;

  // Ocean grid 100x100
  grid: number[][] = [];
  gridMin = 0;
  gridMax = 1;

  displayedColumns = ['x', 'y', 'averageDepth', 'stdDeviation', 'timestamp'];

  constructor(private api: OceanApiService) {}

  ngOnInit() {
    this.pollSub = interval(5000).pipe(
      startWith(0),
      switchMap(() => this.api.getAllScans())
    ).subscribe({
      next: scans => {
        this.scans = scans;
        this.loading = false;
        this.buildGrid();
        setTimeout(() => this.renderCharts(), 100);
      },
      error: () => { this.loading = false; }
    });

    this.api.getMeasurements().subscribe({
      next: pts => this.measurements = pts,
      error: () => {}
    });
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
    this.depthChart?.destroy();
    this.heatChart?.destroy();
  }

  private buildGrid() {
    this.grid = Array.from({ length: 100 }, () => Array(100).fill(-1));
    let min = Infinity, max = -Infinity;
    for (const s of this.scans) {
      if (s.x >= 0 && s.x < 100 && s.y >= 0 && s.y < 100) {
        this.grid[s.y][s.x] = s.averageDepth;
        if (s.averageDepth < min) min = s.averageDepth;
        if (s.averageDepth > max) max = s.averageDepth;
      }
    }
    this.gridMin = min === Infinity ? 0 : min;
    this.gridMax = max === -Infinity ? 1 : max;
  }

  private renderCharts() {
    this.renderDepthChart();
    this.renderHeatmap();
  }

  private renderDepthChart() {
    const canvas = this.depthChartRef?.nativeElement;
    if (!canvas || this.scans.length === 0) return;

    this.depthChart?.destroy();

    const sorted = [...this.scans].sort((a, b) =>
      new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    ).slice(-50);

    this.depthChart = new Chart(canvas, {
      type: 'line',
      data: {
        labels: sorted.map(s => `(${s.x},${s.y})`),
        datasets: [{
          label: 'Durchschnittliche Tiefe (m)',
          data: sorted.map(s => s.averageDepth),
          borderColor: '#4fc3f7',
          backgroundColor: 'rgba(79,195,247,0.15)',
          fill: true,
          tension: 0.4,
          pointRadius: 3,
        }, {
          label: 'Std.-Abweichung',
          data: sorted.map(s => s.stdDeviation),
          borderColor: '#ff8a65',
          backgroundColor: 'rgba(255,138,101,0.1)',
          fill: false,
          tension: 0.4,
          pointRadius: 2,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { labels: { color: '#e0f0ff' } }
        },
        scales: {
          x: {
            ticks: { color: '#8ab8d8', maxTicksLimit: 15 },
            grid: { color: 'rgba(255,255,255,0.05)' }
          },
          y: {
            ticks: { color: '#8ab8d8' },
            grid: { color: 'rgba(255,255,255,0.05)' }
          }
        }
      }
    });
  }

  private renderHeatmap() {
    const canvas = this.heatmapRef?.nativeElement;
    if (!canvas || this.scans.length === 0) return;

    this.heatChart?.destroy();

    const ctx = canvas.getContext('2d')!;
    const W = canvas.width  = 500;
    const H = canvas.height = 500;
    const cellW = W / 100;
    const cellH = H / 100;

    ctx.fillStyle = '#071020';
    ctx.fillRect(0, 0, W, H);

    const range = this.gridMax - this.gridMin || 1;

    for (let y = 0; y < 100; y++) {
      for (let x = 0; x < 100; x++) {
        const val = this.grid[y][x];
        if (val < 0) continue;
        const t = (val - this.gridMin) / range;
        const r = Math.round(0   + t * 30);
        const g = Math.round(60  + t * 100);
        const b = Math.round(120 + t * 135);
        ctx.fillStyle = `rgb(${r},${g},${b})`;
        ctx.fillRect(x * cellW, y * cellH, cellW, cellH);
      }
    }

    // Grid lines
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 0.3;
    for (let i = 0; i <= 100; i += 10) {
      ctx.beginPath(); ctx.moveTo(i * cellW, 0); ctx.lineTo(i * cellW, H); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(0, i * cellH); ctx.lineTo(W, i * cellH); ctx.stroke();
    }
  }

  get scannedCount(): number {
    return this.scans.length;
  }

  get avgDepth(): string {
    if (!this.scans.length) return '–';
    const avg = this.scans.reduce((s, c) => s + c.averageDepth, 0) / this.scans.length;
    return avg.toFixed(1) + ' m';
  }

  get maxDepth(): string {
    if (!this.scans.length) return '–';
    return Math.max(...this.scans.map(s => s.averageDepth)).toFixed(1) + ' m';
  }

  get measurementCount(): number {
    return this.measurements.length;
  }
}

