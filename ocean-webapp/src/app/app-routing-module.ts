import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ControlComponent } from './components/control/control.component';
import { DataViewComponent } from './components/data-view/data-view.component';
import { GalleryComponent } from './components/gallery/gallery.component';

const routes: Routes = [
  { path: '', redirectTo: 'control', pathMatch: 'full' },
  { path: 'control',  component: ControlComponent },
  { path: 'data',     component: DataViewComponent },
  { path: 'gallery',  component: GalleryComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
