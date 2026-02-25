import { Component, OnInit, OnDestroy, ChangeDetectorRef } from "@angular/core";
import { OceanApiService } from "../../services/ocean-api.service";
import { Submarine, PhotoMeta } from "../../models/models";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Subscription } from "rxjs";
interface SubWithSession extends Submarine {
  pilotStep: number;
  photos: PhotoMeta[];
  showPhotos: boolean;
}
@Component({
  standalone: false,
  selector: "app-submarine-control",
  templateUrl: "./submarine-control.component.html",
  styleUrls: ["./submarine-control.component.scss"]
})
export class SubmarineControlComponent implements OnInit, OnDestroy {
  subs: SubWithSession[] = [];
  loading = false;
  readonly PILOT_STEPS = [
    { icon: "⬇️", label: "Taucht ab",  desc: "Route DOWN" },
    { icon: "📏", label: "Messen",      desc: "Messung laeuft" },
    { icon: "📸", label: "Foto",        desc: "Foto wird gemacht" },
    { icon: "⬆️", label: "Auftauchen", desc: "Route UP" },
    { icon: "✅", label: "Aufgetaucht", desc: "Fertig" },
  ];
  private pollTimer?: ReturnType<typeof setInterval>;
  private subsSub?: Subscription;
  private sessSub?: Subscription;
  constructor(
    private api: OceanApiService,
    private snack: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}
  ngOnInit() {
    this.load();
    this.pollTimer = setInterval(() => this.load(), 2000);
  }
  ngOnDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.subsSub?.unsubscribe();
    this.sessSub?.unsubscribe();
  }
  load() {
    this.subsSub?.unsubscribe();
    this.subsSub = this.api.getActiveSubmarines().subscribe({
      next: submarines => {
        const existing = new Map(this.subs.map(s => [s.name, s]));
        this.subs = submarines.map(sub => ({
          ...sub,
          pilotStep:  existing.get(sub.name)?.pilotStep  ?? 0,
          photos:     existing.get(sub.name)?.photos     ?? [],
          showPhotos: existing.get(sub.name)?.showPhotos ?? false,
        }));
        this.loadSessions();
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }
  private loadSessions() {
    this.sessSub?.unsubscribe();
    this.sessSub = this.api.getSubmarineSessions().subscribe({
      next: sessions => {
        for (const sub of this.subs) {
          const sess = sessions.find(s => s.submarineId === sub.name);
          if (sess) sub.pilotStep = sess.pilotStep;
        }
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }
  stepInfo(step: number) {
    return this.PILOT_STEPS[Math.min(step, this.PILOT_STEPS.length - 1)];
  }
  stepPercent(step: number): number {
    return Math.round((Math.min(step, this.PILOT_STEPS.length) / this.PILOT_STEPS.length) * 100);
  }
  togglePhotos(sub: SubWithSession) {
    if (sub.id < 0) {
      this.snack.open("Fotos verfuegbar nach Abschluss des Tauchgangs", "OK", { duration: 3000 });
      return;
    }
    sub.showPhotos = !sub.showPhotos;
    if (sub.showPhotos && sub.photos.length === 0) {
      this.api.getSubmarinePhotos(sub.id).subscribe({
        next: photos => { sub.photos = photos; this.cdr.detectChanges(); },
        error: () => {}
      });
    }
  }
  disconnect(sub: SubWithSession) {
    if (!confirm("Submarine " + sub.name + " wirklich abbrechen?")) return;
    this.loading = true;
    this.api.disconnectSubmarine(sub.name).subscribe({
      next: res => {
        this.loading = false;
        this.snack.open(res.success ? (sub.name + " abgebrochen") : (res.error ?? "Fehler"), "OK", { duration: 3000 });
        this.load();
      },
      error: err => { this.loading = false; this.snack.open(err.message, "OK", { duration: 4000 }); }
    });
  }
  photoUrl(id: number): string { return this.api.photoUrl(id); }
}
