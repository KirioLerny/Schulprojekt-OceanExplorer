import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { OceanApiService } from '../../services/ocean-api.service';
import { PhotoMeta } from '../../models/models';
import { Subscription } from 'rxjs';

@Component({
  standalone: false,
  selector: 'app-gallery',
  templateUrl: './gallery.component.html',
  styleUrls: ['./gallery.component.scss']
})
export class GalleryComponent implements OnInit, OnDestroy {

  photos: PhotoMeta[] = [];
  loading = false;
  lightboxOpen = false;
  lightboxIndex = 0;

  filterSub = '';
  private pollTimer?: ReturnType<typeof setInterval>;
  private loadingSub?: Subscription;

  constructor(private api: OceanApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.load();
    this.pollTimer = setInterval(() => this.load(), 8000);
  }

  ngOnDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.loadingSub?.unsubscribe();
  }

  private load() {
    this.loadingSub?.unsubscribe();
    this.loadingSub = this.api.getPhotos().subscribe({
      next: photos => {
        this.photos = photos;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  get filtered(): PhotoMeta[] {
    if (!this.filterSub.trim()) return this.photos;
    return this.photos.filter(p =>
      p.submarineName?.toLowerCase().includes(this.filterSub.toLowerCase())
    );
  }

  openLightbox(index: number) {
    this.lightboxIndex = index;
    this.lightboxOpen = true;
  }

  closeLightbox() {
    this.lightboxOpen = false;
  }

  navigate(dir: number) {
    const len = this.filtered.length;
    this.lightboxIndex = (this.lightboxIndex + dir + len) % len;
  }

  get currentPhoto(): PhotoMeta | null {
    return this.filtered[this.lightboxIndex] ?? null;
  }

  photoUrl(id: number): string {
    return this.api.photoUrl(id);
  }

  get uniqueSubs(): string[] {
    return [...new Set(this.photos.map(p => p.submarineName))];
  }
}

