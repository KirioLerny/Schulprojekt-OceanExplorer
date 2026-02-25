import { Component } from '@angular/core';

@Component({
  standalone: false,
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrls: ['./app.scss']
})
export class App {
  navItems = [
    { path: '/control',    icon: 'directions_boat', label: 'Steuerung' },
    { path: '/submarines', icon: 'water',            label: 'Submarines' },
    { path: '/data',       icon: 'bar_chart',        label: 'Messdaten' },
    { path: '/gallery',    icon: 'photo_library',    label: 'Bildgalerie' },
    { path: '/accidents',  icon: 'warning',          label: 'Unfälle' },
  ];
}
