package ocean.model;

public class ScanResult {

    private final Vec2D sector;
    private final int averageDepth;
    private final float standardDeviation;

    public ScanResult(Vec2D sector, int averageDepth, float standardDeviation) {
        this.sector = new Vec2D(sector);
        this.averageDepth = averageDepth;
        this.standardDeviation = standardDeviation;
    }

    public Vec2D getSector() { return sector; }
    public int getAverageDepth() { return averageDepth; }
    public float getStandardDeviation() { return standardDeviation; }

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
