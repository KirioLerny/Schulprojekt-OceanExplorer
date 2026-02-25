import { Component, OnInit, OnDestroy } from '@angular/core';
import { OceanApiService } from '../../services/ocean-api.service';
import { PhotoMeta } from '../../models/models';
import { interval, Subscription } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';

@Component({
  standalone: false,
  selector: 'app-gallery',
  templateUrl: './gallery.component.html',
  styleUrls: ['./gallery.component.scss']
})
export class GalleryComponent implements OnInit, OnDestroy {

  photos: PhotoMeta[] = [];
  loading = true;
  lightboxOpen = false;
  lightboxIndex = 0;

  filterSub = '';
  private pollSub?: Subscription;

  constructor(private api: OceanApiService) {}

  ngOnInit() {
    this.pollSub = interval(8000).pipe(
      startWith(0),
      switchMap(() => this.api.getPhotos())
    ).subscribe({
      next: photos => {
        this.photos = photos;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
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

