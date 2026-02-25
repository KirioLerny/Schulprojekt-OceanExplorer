import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  Ship, LaunchShipRequest, NavigateRequest,
  ScanData, PositionData, Submarine, PhotoMeta,
  MeasurementPoint, ApiResponse, Accident
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class OceanApiService {

  private readonly BASE = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  // ── Ship ──────────────────────────────────────────────────────────────────

  launchShip(req: LaunchShipRequest): Observable<ApiResponse<Ship>> {
    return this.http.post<ApiResponse<Ship>>(`${this.BASE}/api/ship/launch`, req)
      .pipe(catchError(this.handleError));
  }

  navigateShip(req: NavigateRequest): Observable<ApiResponse<Ship>> {
    return this.http.post<ApiResponse<Ship>>(`${this.BASE}/api/ship/navigate`, req)
      .pipe(catchError(this.handleError));
  }

  scanSector(shipName: string): Observable<ApiResponse<ScanData>> {
    return this.http.post<ApiResponse<ScanData>>(`${this.BASE}/api/ship/scan`, { shipName })
      .pipe(catchError(this.handleError));
  }

  exitShip(shipName: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.BASE}/api/ship/exit`, { shipName })
      .pipe(catchError(this.handleError));
  }

  getShips(): Observable<Ship[]> {
    return this.http.get<Ship[]>(`${this.BASE}/api/ships`)
      .pipe(catchError(this.handleError));
  }

  getShipPositions(shipName: string): Observable<PositionData[]> {
    return this.http.get<PositionData[]>(`${this.BASE}/api/ships/${encodeURIComponent(shipName)}/positions`)
      .pipe(catchError(this.handleError));
  }

  // ── Scans ─────────────────────────────────────────────────────────────────

  getAllScans(): Observable<ScanData[]> {
    return this.http.get<ScanData[]>(`${this.BASE}/api/scans`)
      .pipe(catchError(this.handleError));
  }

  getShipScans(shipName: string): Observable<ScanData[]> {
    return this.http.get<ScanData[]>(`${this.BASE}/api/ships/${encodeURIComponent(shipName)}/scans`)
      .pipe(catchError(this.handleError));
  }

  // ── Submarine ─────────────────────────────────────────────────────────────

  launchSubmarine(shipName: string): Observable<ApiResponse<Submarine>> {
    return this.http.post<ApiResponse<Submarine>>(`${this.BASE}/api/submarine/launch`, { shipName })
      .pipe(catchError(this.handleError));
  }

  exitSubmarine(subId: number): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.BASE}/api/submarine/${subId}/exit`, {})
      .pipe(catchError(this.handleError));
  }

  getSubmarines(shipName: string): Observable<Submarine[]> {
    return this.http.get<Submarine[]>(`${this.BASE}/api/ships/${encodeURIComponent(shipName)}/submarines`)
      .pipe(catchError(this.handleError));
  }

  getActiveSubmarines(): Observable<Submarine[]> {
    return this.http.get<Submarine[]>(`${this.BASE}/api/submarines/active`)
      .pipe(catchError(this.handleError));
  }

  getSubmarineSessions(): Observable<{submarineId: string, pilotStep: number}[]> {
    return this.http.get<{submarineId: string, pilotStep: number}[]>(`${this.BASE}/api/submarines/sessions`)
      .pipe(catchError(this.handleError));
  }

  disconnectSubmarine(submarineId: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.BASE}/api/submarine/disconnect`, { submarineId })
      .pipe(catchError(this.handleError));
  }

  /** Sendet arise an ein Submarine – die Submarine-App beendet sich dann automatisch. */
  ariseSubmarine(submarineId: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.BASE}/api/submarine/arise`, { submarineId })
      .pipe(catchError(this.handleError));
  }

  getAllSubmarines(): Observable<Submarine[]> {
    return this.http.get<Submarine[]>(`${this.BASE}/api/submarines`)
      .pipe(catchError(this.handleError));
  }

  getMeasurements(): Observable<MeasurementPoint[]> {
    return this.http.get<MeasurementPoint[]>(`${this.BASE}/api/measurements`)
      .pipe(catchError(this.handleError));
  }

  getAccidents(): Observable<Accident[]> {
    return this.http.get<Accident[]>(`${this.BASE}/api/accidents`)
      .pipe(catchError(this.handleError));
  }

  // ── Photos ────────────────────────────────────────────────────────────────

  getPhotos(): Observable<PhotoMeta[]> {
    return this.http.get<PhotoMeta[]>(`${this.BASE}/api/photos`)
      .pipe(catchError(this.handleError));
  }

  getSubmarinePhotos(subId: number): Observable<PhotoMeta[]> {
    return this.http.get<PhotoMeta[]>(`${this.BASE}/api/submarines/${subId}/photos`)
      .pipe(catchError(this.handleError));
  }

  photoUrl(id: number): string {
    return `${this.BASE}/api/photos/${id}`;
  }

  // ── Status ────────────────────────────────────────────────────────────────

  getStatus(): Observable<any> {
    return this.http.get<any>(`${this.BASE}/api/status`)
      .pipe(catchError(this.handleError));
  }

  // ── Error handling ────────────────────────────────────────────────────────

  private handleError(error: HttpErrorResponse): Observable<never> {
    const msg = error.error?.error ?? error.error?.message ?? error.message ?? 'Unbekannter Fehler';
    return throwError(() => new Error(msg));
  }
}

