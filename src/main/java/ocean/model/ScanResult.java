package ocean.model;

/**
 * Repraesentiert das Ergebnis eines Scan-Befehls.
 *
 * <pre>
 * Ein Scan liefert Informationen ueber die Meerestiefe in einem Sektor:
 *   Mittlere Tiefe (in Metern, negativ = unter Wasser)
 *   Standardabweichung (Variation der Tiefe)
 * </pre>
 */
public class ScanResult {

    private final Vec2D sector;
    private final int averageDepth;
    private final float standardDeviation;

    /**
     * Erstellt ein neues Scan-Ergebnis.
     *
     * @param sector            Gescannter Sektor
     * @param averageDepth      Mittlere Tiefe
     * @param standardDeviation Standardabweichung
     */
    public ScanResult(Vec2D sector, int averageDepth, float standardDeviation) {
        this.sector = new Vec2D(sector);
        this.averageDepth = averageDepth;
        this.standardDeviation = standardDeviation;
    }

    public Vec2D getSector() { return sector; }

    public int getAverageDepth() { return averageDepth; }

    public float getStandardDeviation() { return standardDeviation; }

    /**
     * Prueft ob der Sektor befahrbar ist (Tiefe &lt; 0 = Wasser).
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
