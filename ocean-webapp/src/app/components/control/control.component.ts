import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { OceanApiService } from '../../services/ocean-api.service';
import { Ship, Submarine } from '../../models/models';
import { interval, Subscription } from 'rxjs';
import { switchMap, startWith } from 'rxjs/operators';

@Component({
  standalone: false,
  selector: 'app-control',
  templateUrl: './control.component.html',
  styleUrls: ['./control.component.scss']
})
export class ControlComponent implements OnInit, OnDestroy {

  launchForm!: FormGroup;
  loading = false;
  ship: Ship | null = null;
  submarines: Submarine[] = [];
  maxSubs = 4;

  private pollSub?: Subscription;

  constructor(
    private fb: FormBuilder,
    private api: OceanApiService,
    private snack: MatSnackBar
  ) {}

  ngOnInit() {
    this.launchForm = this.fb.group({
      name: ['Explorer', [Validators.required, Validators.minLength(3)]],
      sectorX: [50, [Validators.required, Validators.min(0), Validators.max(99)]],
      sectorY: [50, [Validators.required, Validators.min(0), Validators.max(99)]],
      dirX: [0, Validators.required],
      dirY: [1, Validators.required],
    });
    this.startPolling();
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  private startPolling() {
    this.pollSub = interval(3000).pipe(
      startWith(0),
      switchMap(() => this.api.getShips())
    ).subscribe({
      next: ships => {
        const active = ships.find(s => s.active);
        if (active) {
          this.ship = active;
          this.loadSubmarines();
        } else {
          this.ship = null;
          this.submarines = [];
        }
      },
      error: () => {}
    });
  }

  private loadSubmarines() {
    if (!this.ship) return;
    this.api.getSubmarines(this.ship.name).subscribe({
      next: subs => this.submarines = subs,
      error: () => {}
    });
  }

  get canNavigate(): boolean {
    return !!this.ship && this.activeSubCount === 0;
  }

  get activeSubCount(): number {
    return this.submarines.filter(s => s.active).length;
  }

  get canLaunchSub(): boolean {
    return !!this.ship && this.activeSubCount < this.maxSubs;
  }

  launchShip() {
    if (this.launchForm.invalid) return;
    this.loading = true;
    const v = this.launchForm.value;
    this.api.launchShip({
      name: v.name,
      sectorX: v.sectorX,
      sectorY: v.sectorY,
      dirX: v.dirX,
      dirY: v.dirY,
    }).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.ship = res.data!;
          this.snack.open(`✅ Schiff "${res.data?.name}" gestartet!`, 'OK', { duration: 3000 });
        } else {
          this.snack.open(`❌ ${res.error ?? res.message}`, 'OK', { duration: 4000 });
        }
      },
      error: err => {
        this.loading = false;
        this.snack.open(`❌ ${err.message}`, 'OK', { duration: 4000 });
      }
    });
  }

  navigate(rudder: 'Left' | 'Center' | 'Right', course: 'Forward' | 'Backward') {
    if (!this.ship || !this.canNavigate) return;
    this.loading = true;
    this.api.navigateShip({ shipName: this.ship.name, rudder, course }).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.ship = res.data!;
          this.snack.open(`⚓ Bewegt → (${res.data?.currentX}, ${res.data?.currentY})`, 'OK', { duration: 2000 });
        } else {
          this.snack.open(`❌ ${res.error ?? res.message}`, 'OK', { duration: 4000 });
        }
      },
      error: err => {
        this.loading = false;
        this.snack.open(`❌ ${err.message}`, 'OK', { duration: 4000 });
      }
    });
  }

  scanSector() {
    if (!this.ship) return;
    this.loading = true;
    this.api.scanSector(this.ship.name).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.snack.open(`🔍 Scan: Tiefe ${res.data?.averageDepth?.toFixed(1)}m, σ=${res.data?.stdDeviation?.toFixed(2)}`, 'OK', { duration: 3000 });
        } else {
          this.snack.open(`❌ ${res.error ?? res.message}`, 'OK', { duration: 4000 });
        }
      },
      error: err => {
        this.loading = false;
        this.snack.open(`❌ ${err.message}`, 'OK', { duration: 4000 });
      }
    });
  }

  exitShip() {
    if (!this.ship) return;
    if (!confirm(`Schiff "${this.ship.name}" wirklich beenden?`)) return;
    this.loading = true;
    this.api.exitShip(this.ship.name).subscribe({
      next: res => {
        this.loading = false;
        this.snack.open('🚢 Schiff beendet', 'OK', { duration: 3000 });
        this.ship = null;
        this.submarines = [];
      },
      error: err => {
        this.loading = false;
        this.snack.open(`❌ ${err.message}`, 'OK', { duration: 4000 });
      }
    });
  }

  launchSubmarine() {
    if (!this.canLaunchSub) return;
    this.loading = true;
    this.api.launchSubmarine(this.ship!.name).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.snack.open(`🤿 Submarine gestartet!`, 'OK', { duration: 3000 });
          this.loadSubmarines();
        } else {
          this.snack.open(`❌ ${res.error ?? res.message}`, 'OK', { duration: 4000 });
        }
      },
      error: err => {
        this.loading = false;
        this.snack.open(`❌ ${err.message}`, 'OK', { duration: 4000 });
      }
    });
  }

  exitSubmarine(sub: Submarine) {
    if (!confirm(`Submarine "${sub.name}" einziehen?`)) return;
    this.loading = true;
    this.api.exitSubmarine(sub.id).subscribe({
      next: () => {
        this.loading = false;
        this.snack.open('🤿 Submarine eingezogen', 'OK', { duration: 3000 });
        this.loadSubmarines();
      },
      error: err => {
        this.loading = false;
        this.snack.open(`❌ ${err.message}`, 'OK', { duration: 4000 });
      }
    });
  }

  dirLabel(x: number | null, y: number | null): string {
    if (x === null || y === null) return '–';
    if (y > 0) return '⬆ Nord';
    if (y < 0) return '⬇ Süd';
    if (x > 0) return '➡ Ost';
    if (x < 0) return '⬅ West';
    return '•';
  }
}

