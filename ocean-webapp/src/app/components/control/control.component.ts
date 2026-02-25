import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { OceanApiService } from '../../services/ocean-api.service';
import { Ship, Submarine } from '../../models/models';
import { Subscription } from 'rxjs';

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
  sessions: {submarineId: string, pilotStep: number}[] = [];
  maxSubs = 4;

  readonly PILOT_LABELS = ['⬇ Taucht ab', '📏 Messen', '📸 Foto', '⬆ Auftauchen', '✅ Aufgetaucht'];

  private pollTimer?: ReturnType<typeof setInterval>;
  private shipSub?:   Subscription;
  private subSub?:    Subscription;
  private sessSub?:   Subscription;

  constructor(
    private fb: FormBuilder,
    private api: OceanApiService,
    private snack: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.launchForm = this.fb.group({
      name:    ['Explorer', [Validators.required, Validators.minLength(3)]],
      sectorX: [50, [Validators.required, Validators.min(0), Validators.max(99)]],
      sectorY: [50, [Validators.required, Validators.min(0), Validators.max(99)]],
      dirX:    [0,  Validators.required],
      dirY:    [1,  Validators.required],
    });
    this.pollShip();
    this.loadSubmarines();
    this.pollTimer = setInterval(() => {
      this.pollShip();
      this.loadSubmarines();
    }, 2000);
  }

  ngOnDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.shipSub?.unsubscribe();
    this.subSub?.unsubscribe();
    this.sessSub?.unsubscribe();
  }

  private pollShip() {
    this.shipSub?.unsubscribe();
    this.shipSub = this.api.getShips().subscribe({
      next: ships => {
        const active = ships.find(s => s.active) ?? null;
        this.ship = active;
        // Nur leeren wenn KEIN Schiff mehr aktiv UND keine aktiven Subs laufen
        if (!active && this.submarines.every(s => !s.active)) {
          this.submarines = [];
        }
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  loadSubmarines() {
    this.subSub?.unsubscribe();
    this.subSub = this.api.getActiveSubmarines().subscribe({
      next: subs => {
        console.log('[Submarines] received:', subs);
        this.submarines = subs;
        this.cdr.detectChanges();
      },
      error: err => {
        console.error('[Submarines] fetch error:', err);
      }
    });
    this.sessSub?.unsubscribe();
    this.sessSub = this.api.getSubmarineSessions().subscribe({
      next: sess => { this.sessions = sess; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  sessionFor(subName: string): {submarineId: string, pilotStep: number} | undefined {
    return this.sessions.find(s => s.submarineId === subName);
  }

  stepLabel(step: number): string {
    return this.PILOT_LABELS[step] ?? `Schritt ${step}`;
  }

  /** Submarine sauber einziehen via arise (Submarine-App beendet sich dann automatisch). */
  ariseByName(subName: string) {
    if (!confirm(`Submarine "${subName}" einziehen (arise)?`)) return;
    this.loading = true;
    this.api.ariseSubmarine(subName).subscribe({
      next: res => {
        this.loading = false;
        this.snack.open(res.success ? `${subName} eingezogen` : (res.error ?? 'Fehler'), 'OK', { duration: 3000 });
        this.loadSubmarines();
      },
      error: err => { this.loading = false; this.snack.open(err.message, 'OK', { duration: 4000 }); }
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
      name: v.name, sectorX: v.sectorX, sectorY: v.sectorY, dirX: v.dirX, dirY: v.dirY,
    }).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.ship = res.data!;
          this.snack.open(`Schiff "${res.data?.name}" gestartet!`, 'OK', { duration: 3000 });
        } else {
          this.snack.open(res.error ?? res.message ?? 'Fehler', 'OK', { duration: 4000 });
        }
      },
      error: err => { this.loading = false; this.snack.open(err.message, 'OK', { duration: 4000 }); }
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
          this.snack.open(`Bewegt nach (${res.data?.currentX}, ${res.data?.currentY})`, 'OK', { duration: 2000 });
        } else {
          this.snack.open(res.error ?? res.message ?? 'Fehler', 'OK', { duration: 4000 });
        }
      },
      error: err => { this.loading = false; this.snack.open(err.message, 'OK', { duration: 4000 }); }
    });
  }

  scanSector() {
    if (!this.ship) return;
    this.loading = true;
    this.api.scanSector(this.ship.name).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.snack.open(`Scan: Tiefe ${res.data?.averageDepth?.toFixed(1)}m, Stab.=${res.data?.stdDeviation?.toFixed(2)}`, 'OK', { duration: 3000 });
        } else {
          this.snack.open(res.error ?? res.message ?? 'Fehler', 'OK', { duration: 4000 });
        }
      },
      error: err => { this.loading = false; this.snack.open(err.message, 'OK', { duration: 4000 }); }
    });
  }

  exitShip() {
    if (!this.ship) return;
    if (!confirm(`Schiff "${this.ship.name}" wirklich beenden?`)) return;
    this.loading = true;
    this.api.exitShip(this.ship.name).subscribe({
      next: () => {
        this.loading = false;
        this.snack.open('Schiff beendet', 'OK', { duration: 3000 });
        this.ship = null;
        this.submarines = [];
        this.cdr.detectChanges();
      },
      error: err => { this.loading = false; this.snack.open(err.message, 'OK', { duration: 4000 }); }
    });
  }

  launchSubmarine() {
    if (!this.canLaunchSub) return;
    this.loading = true;
    this.api.launchSubmarine(this.ship!.name).subscribe({
      next: res => {
        this.loading = false;
        if (res.success) {
          this.snack.open('Submarine gestartet - erscheint in der Liste sobald es sich verbunden hat', 'OK', { duration: 5000 });
          this.startRapidPoll();
        } else {
          this.snack.open(res.error ?? res.message ?? 'Fehler', 'OK', { duration: 4000 });
        }
      },
      error: err => { this.loading = false; this.snack.open(err.message, 'OK', { duration: 4000 }); }
    });
  }

  /** Polls every 500 ms for up to 15 s after a submarine launch to catch the connecting phase. */
  private startRapidPoll() {
    let elapsed = 0;
    const rapid = setInterval(() => {
      this.loadSubmarines();
      elapsed += 500;
      if (elapsed >= 15000) clearInterval(rapid);
    }, 500);
  }

  exitSubmarine(sub: Submarine) {
    if (!confirm(`Submarine "${sub.name}" einziehen?`)) return;
    this.loading = true;
    this.api.exitSubmarine(sub.id).subscribe({
      next: () => {
        this.loading = false;
        this.snack.open('Submarine eingezogen', 'OK', { duration: 3000 });
        this.loadSubmarines();
      },
      error: err => { this.loading = false; this.snack.open(err.message, 'OK', { duration: 4000 }); }
    });
  }

  dirLabel(x: number | null, y: number | null): string {
    if (x === null || y === null) return '-';
    if (y > 0) return 'Nord';
    if (y < 0) return 'Sued';
    if (x > 0) return 'Ost';
    if (x < 0) return 'West';
    return '-';
  }
}

