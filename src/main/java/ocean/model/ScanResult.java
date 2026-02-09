package ocean.model;

/**
 * Repräsentiert das Ergebnis eines Scan-Befehls.
 *
 * Ein Scan liefert Informationen über die Meerestiefe in einem Sektor:
 * - Mittlere Tiefe (in Metern, negativ = unter Wasser)
 * - Standardabweichung (Variation der Tiefe)
 *
 * @author OceanExplorer Team
 */
public class ScanResult {

    /** Sektor, in dem der Scan durchgeführt wurde */
    private final Vec2D sector;

    /** Mittlere Tiefe in Metern (negativ = unter Wasser) */
    private final int averageDepth;

    /** Standardabweichung der Tiefenmessung */
    private final float standardDeviation;

    /**
     * Erstellt ein neues Scan-Ergebnis.
     *
     * @param sector Gescannter Sektor
     * @param averageDepth Mittlere Tiefe
     * @param standardDeviation Standardabweichung
     */
    public ScanResult(Vec2D sector, int averageDepth, float standardDeviation) {
        this.sector = new Vec2D(sector);
        this.averageDepth = averageDepth;
        this.standardDeviation = standardDeviation;
    }

    // === Getter ===

    public Vec2D getSector() {
        return sector;
    }

    public int getAverageDepth() {
        return averageDepth;
    }

    public float getStandardDeviation() {
        return standardDeviation;
    }

    /**
     * Prüft ob der Sektor befahrbar ist (Tiefe < 0 = Wasser).
     *
     * @return true wenn Wasser vorhanden
     */
    public boolean isNavigable() {
        return averageDepth < 0;
    }

    @Override
    public String toString() {
        return "ScanResult[sector=" + sector +
               ", depth=" + averageDepth +
               "m, stdDev=" + standardDeviation + "]";
    }
}

