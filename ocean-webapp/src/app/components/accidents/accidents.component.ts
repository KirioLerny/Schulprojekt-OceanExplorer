import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { OceanApiService } from '../../services/ocean-api.service';
import { Accident } from '../../models/models';
import { Subscription } from 'rxjs';

@Component({
  standalone: false,
  selector: 'app-accidents',
  templateUrl: './accidents.component.html',
  styleUrls: ['./accidents.component.scss']
})
export class AccidentsComponent implements OnInit, OnDestroy {

  accidents: Accident[] = [];
  loading = true;

  private pollTimer?: ReturnType<typeof setInterval>;
  private sub?: Subscription;

  displayedColumns = ['id', 'submarineId', 'x', 'y', 'description', 'timestamp'];

  constructor(private api: OceanApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.load();
    this.pollTimer = setInterval(() => this.load(), 10000);
  }

  ngOnDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.sub?.unsubscribe();
  }

  load() {
    this.sub?.unsubscribe();
    this.sub = this.api.getAccidents().subscribe({
      next: data => {
        this.accidents = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  get uniqueSubs(): number {
    return new Set(this.accidents.map(a => a.submarineId).filter(id => id != null)).size;
  }

  get lastAccidentTime(): string {
    if (!this.accidents.length) return '-';
    return this.accidents[0].timestamp ?? '-';
  }
}


