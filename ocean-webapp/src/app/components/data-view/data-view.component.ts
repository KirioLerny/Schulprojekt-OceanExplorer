import { Component, OnInit, OnDestroy, AfterViewInit, ElementRef, ViewChild, ChangeDetectorRef } from '@angular/core';
import { OceanApiService } from '../../services/ocean-api.service';
import { ScanData, MeasurementPoint } from '../../models/models';
import { Chart, registerables } from 'chart.js';
import { Subscription } from 'rxjs';

Chart.register(...registerables);

@Component({
  standalone: false,
  selector: 'app-data-view',
  templateUrl: './data-view.component.html',
  styleUrls: ['./data-view.component.scss']
})
export class DataViewComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('depthChart',    { static: false }) depthChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('heatmapCanvas', { static: false }) heatmapRef!:    ElementRef<HTMLCanvasElement>;
  @ViewChild('scatter3d',     { static: false }) scatter3dRef!:  ElementRef<HTMLCanvasElement>;

  scans:        ScanData[]        = [];
  measurements: MeasurementPoint[] = [];
  loading = false;

  private pollTimer?: ReturnType<typeof setInterval>;
  private scanSub?:   Subscription;
  private measSub?:   Subscription;
  private depthChart?: Chart;
  private heatChart?:  Chart;
  private chartsReady = false;

  grid:    number[][] = [];
  gridMin = 0;
  gridMax = 1;

  displayedColumns = ['x', 'y', 'averageDepth', 'stdDeviation', 'timestamp'];

  constructor(private api: OceanApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.load();
    this.pollTimer = setInterval(() => this.load(), 5000);
  }

  ngAfterViewInit() {
    this.chartsReady = true;
    if (this.scans.length > 0) this.renderCharts();
    if (this.measurements.length > 0) this.renderScatter();
  }

  ngOnDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.scanSub?.unsubscribe();
    this.measSub?.unsubscribe();
    this.depthChart?.destroy();
    this.heatChart?.destroy();
  }

  private load() {
    this.scanSub?.unsubscribe();
    this.scanSub = this.api.getAllScans().subscribe({
      next: scans => {
        this.scans = scans;
        this.loading = false;
        this.buildGrid();
        this.cdr.detectChanges();
        if (this.chartsReady) this.renderCharts();
      },
      error: () => { this.loading = false; this.cdr.detectChanges(); }
    });

    this.measSub?.unsubscribe();
    this.measSub = this.api.getMeasurements().subscribe({
      next: pts => {
        this.measurements = pts;
        this.cdr.detectChanges();
        if (this.chartsReady) this.renderScatter();
      },
      error: () => {}
    });
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

  renderCharts() {
    this.renderDepthChart();
    this.renderHeatmap();
  }

  private renderDepthChart() {
    const canvas = this.depthChartRef?.nativeElement;
    if (!canvas || this.scans.length === 0) return;
    this.depthChart?.destroy();
    this.depthChart = undefined;

    const sorted = [...this.scans]
      .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())
      .slice(-50);

    this.depthChart = new Chart(canvas, {
      type: 'line',
      data: {
        labels: sorted.map(s => `(${s.x},${s.y})`),
        datasets: [{
          label: 'Durchschnittliche Tiefe (m)',
          data: sorted.map(s => s.averageDepth),
          borderColor: '#4fc3f7',
          backgroundColor: 'rgba(79,195,247,0.15)',
          fill: true, tension: 0.4, pointRadius: 3,
        }, {
          label: 'Std.-Abweichung',
          data: sorted.map(s => s.stdDeviation),
          borderColor: '#ff8a65',
          backgroundColor: 'rgba(255,138,101,0.1)',
          fill: false, tension: 0.4, pointRadius: 2,
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false, animation: false,
        plugins: { legend: { labels: { color: '#e0f0ff' } } },
        scales: {
          x: { ticks: { color: '#8ab8d8', maxTicksLimit: 15 }, grid: { color: 'rgba(255,255,255,0.05)' } },
          y: { ticks: { color: '#8ab8d8' },                    grid: { color: 'rgba(255,255,255,0.05)' } }
        }
      }
    });
  }

  private renderHeatmap() {
    const canvas = this.heatmapRef?.nativeElement;
    if (!canvas || this.scans.length === 0) return;
    this.heatChart?.destroy();
    this.heatChart = undefined;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

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
        const r = Math.round(t * 30);
        const g = Math.round(60  + t * 100);
        const b = Math.round(120 + t * 135);
        ctx.fillStyle = `rgb(${r},${g},${b})`;
        ctx.fillRect(x * cellW, y * cellH, cellW, cellH);
      }
    }

    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 0.3;
    for (let i = 0; i <= 100; i += 10) {
      ctx.beginPath(); ctx.moveTo(i * cellW, 0); ctx.lineTo(i * cellW, H); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(0, i * cellH); ctx.lineTo(W, i * cellH); ctx.stroke();
    }
  }

  /** Renders the 3D measurement scatter as a 2D top-down canvas (X/Y pos, Z=color). */
  renderScatter() {
    const canvas = this.scatter3dRef?.nativeElement;
    if (!canvas || this.measurements.length === 0) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const W = canvas.width  = 500;
    const H = canvas.height = 400;

    ctx.fillStyle = '#071020';
    ctx.fillRect(0, 0, W, H);

    // Determine ranges
    const xs = this.measurements.map(p => p.x);
    const ys = this.measurements.map(p => p.y);
    const zs = this.measurements.map(p => p.z);
    const xMin = Math.min(...xs), xMax = Math.max(...xs) || 1;
    const yMin = Math.min(...ys), yMax = Math.max(...ys) || 1;
    const zMin = Math.min(...zs), zMax = Math.max(...zs) || 1;
    const xRange = xMax - xMin || 1;
    const yRange = yMax - yMin || 1;
    const zRange = zMax - zMin || 1;

    const pad = 30;

    // Grid lines
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 5; i++) {
      const gx = pad + (i / 5) * (W - 2 * pad);
      const gy = pad + (i / 5) * (H - 2 * pad);
      ctx.beginPath(); ctx.moveTo(gx, pad); ctx.lineTo(gx, H - pad); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(pad, gy); ctx.lineTo(W - pad, gy); ctx.stroke();
    }

    // Axis labels
    ctx.fillStyle = '#8ab8d8';
    ctx.font = '11px monospace';
    ctx.fillText(`X: ${xMin}`, pad, H - 8);
    ctx.fillText(`${xMax}`, W - pad - 20, H - 8);
    ctx.save();
    ctx.translate(12, H - pad);
    ctx.rotate(-Math.PI / 2);
    ctx.fillText(`Y: ${yMin}..${yMax}`, 0, 0);
    ctx.restore();

    // Points
    for (const p of this.measurements) {
      const cx = pad + ((p.x - xMin) / xRange) * (W - 2 * pad);
      const cy = H - pad - ((p.y - yMin) / yRange) * (H - 2 * pad);
      const t  = (p.z - zMin) / zRange;
      const r  = Math.round(79  + t * (255 - 79));
      const g  = Math.round(195 - t * (195 - 138));
      const b  = Math.round(247 - t * (247 - 101));
      ctx.beginPath();
      ctx.arc(cx, cy, 3, 0, Math.PI * 2);
      ctx.fillStyle = `rgb(${r},${g},${b})`;
      ctx.fill();
    }

    // Title
    ctx.fillStyle = '#e0f0ff';
    ctx.font = 'bold 12px sans-serif';
    ctx.fillText(`${this.measurements.length} Punkte — Z: ${zMin}..${zMax}`, pad, 18);
  }

  /** Scrolls to the 3D section and re-renders the scatter. */
  locateMeasurements() {
    this.api.getMeasurements().subscribe({
      next: pts => {
        this.measurements = pts;
        this.cdr.detectChanges();
        setTimeout(() => {
          this.renderScatter();
          document.getElementById('locate-section')?.scrollIntoView({ behavior: 'smooth' });
        }, 50);
      },
      error: () => {}
    });
  }

  get scannedCount():     number { return this.scans.length; }
  get measurementCount(): number { return this.measurements.length; }

  get avgDepth(): string {
    if (!this.scans.length) return '-';
    const avg = this.scans.reduce((s, c) => s + c.averageDepth, 0) / this.scans.length;
    return avg.toFixed(1) + ' m';
  }

  get maxDepth(): string {
    if (!this.scans.length) return '-';
    return Math.max(...this.scans.map(s => s.averageDepth)).toFixed(1) + ' m';
  }
}

