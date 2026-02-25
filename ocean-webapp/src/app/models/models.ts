export interface Ship {
  id: number;
  name: string;
  active: boolean;
  currentX: number | null;
  currentY: number | null;
  directionX: number | null;
  directionY: number | null;
}

export interface LaunchShipRequest {
  name: string;
  sectorX: number;
  sectorY: number;
  dirX: number;
  dirY: number;
}

export interface NavigateRequest {
  shipName: string;
  rudder: 'Left' | 'Center' | 'Right';
  course: 'Forward' | 'Backward';
}

export interface ScanData {
  id: number;
  x: number;
  y: number;
  averageDepth: number;
  stdDeviation: number;
  timestamp: string;
}

export interface PositionData {
  id: number;
  x: number;
  y: number;
  directionX: number | null;
  directionY: number | null;
  timestamp: string;
}

export interface Submarine {
  id: number;
  name: string;
  shipId: number;
  active: boolean;
}

export interface PhotoMeta {
  id: number;
  diveId: number;
  submarineName: string;
  x: number;
  y: number;
  z: number;
  dirX: number;
  dirY: number;
  dirZ: number;
  timestamp: string;
}

export interface MeasurementPoint {
  x: number;
  y: number;
  z: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  error?: string;
}

export interface ShipState {
  launched: boolean;
  ship: Ship | null;
  activeSubmarinesCount: number;
  canNavigate: boolean;
}

