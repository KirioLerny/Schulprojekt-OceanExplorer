package ocean.model;

import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Hilfsklasse zur Beschreibung einer 2D-Koordinate bzw. eines 2D-Richtungsvektors.
 *
 * <pre>
 * JSON-Darstellung: {"vec2":[x,y]}
 * </pre>
 */
public class Vec2D {

    private int x;
    private int y;

    private static final Vec2D[] NeighbourOffsets = {
        new Vec2D(-1, 0), new Vec2D(-1, 1), new Vec2D(0, 1),  new Vec2D(1, 1),
        new Vec2D(1, 0),  new Vec2D(1, -1), new Vec2D(0, -1), new Vec2D(-1, -1)
    };

    public Vec2D() {
    }

    public Vec2D(int x, int y) {
        super();
        this.x = x;
        this.y = y;
    }

    public Vec2D(Vec2D v) {
        this.x = v.x;
        this.y = v.y;
    }

    public int getX() { return x; }

    public void setX(int x) { this.x = x; }

    public int getY() { return y; }

    public void setY(int y) { this.y = y; }

    /**
     * Addiert den uebergebenen Vektor zum aktuellen und gibt eine Referenz
     * auf das geaenderte Objekt zurueck.
     *
     * @param v zu addierender Vektor
     * @return this
     */
    public Vec2D add(Vec2D v) {
        this.x += v.x;
        this.y += v.y;
        return this;
    }

    /**
     * Addiert einen Offset zu beiden Komponenten.
     *
     * @param offset Offset-Wert
     * @return this
     */
    public Vec2D add(int offset) {
        this.x += offset;
        this.y += offset;
        return this;
    }

    /**
     * Addiert den uebergebenen Vektor und liefert ein neues Objekt mit dem Ergebnis.
     * Das Ursprungsobjekt bleibt unveraendert.
     *
     * @param v zu addierender Vektor
     * @return neues Vec2D-Objekt
     */
    public Vec2D getSumVec(Vec2D v) {
        Vec2D value = new Vec2D(this);
        value.x += v.x;
        value.y += v.y;
        return value;
    }

    /**
     * Multipliziert beide Komponenten mit dem Faktor.
     *
     * @param factor Multiplikator
     * @return this
     */
    public Vec2D mul(int factor) {
        this.x *= factor;
        this.y *= factor;
        return this;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Vec2D other = (Vec2D) obj;
        return x == other.x && y == other.y;
    }

    /**
     * Serialisiert den Vektor als JSON-Objekt.
     *
     * @return JSONObject mit Feld "vec2"
     */
    public JSONObject toJson() {
        JSONObject jo = new JSONObject();
        jo.put("vec2", toJsonArray());
        return jo;
    }

    /**
     * Serialisiert den Vektor als JSON-Array.
     *
     * @return JSONArray [x, y]
     */
    public JSONArray toJsonArray() {
        JSONArray vec = new JSONArray();
        vec.put(x);
        vec.put(y);
        return vec;
    }

    /**
     * Konvertiert zu einem 3D-Vektor (z=0).
     *
     * @return Vec mit z=0
     */
    public Vec asVec() {
        return new Vec(x, y, 0);
    }

    /**
     * Gibt einen neuen Vektor mit gespiegelter Richtung zurueck.
     *
     * @return invertierter Vektor
     */
    public Vec2D invert() {
        return new Vec2D(x * -1, y * -1);
    }

    /**
     * Gibt ein Array aller 8 angrenzenden Koordinaten zurueck.
     * Es findet keine Bereichspruefung statt.
     *
     * @return Array mit 8 Nachbar-Vektoren
     */
    public Vec2D[] getNeighbours() {
        Vec2D[] neighbours = new Vec2D[8];
        for (int i = 0; i < neighbours.length; i++) {
            neighbours[i] = this.getSumVec(NeighbourOffsets[i]);
        }
        return neighbours;
    }

    /**
     * Deserialisiert aus einem JSON-String.
     *
     * @param json JSON-String
     * @return Vec2D oder null bei Fehler
     */
    public static Vec2D fromJson(String json) {
        return fromJson(new JSONObject(json));
    }

    /**
     * Deserialisiert aus einem JSON-Array.
     *
     * @param ja JSONArray [x, y]
     * @return Vec2D oder null bei Fehler
     */
    public static Vec2D fromJson(JSONArray ja) {
        if (ja.length() == 2) {
            return new Vec2D(ja.getInt(0), ja.getInt(1));
        } else {
            System.err.println("Vec2D.fromJson(ja): invalid data: " + ja.toString(2));
        }
        return null;
    }

    /**
     * Deserialisiert aus einem JSON-Objekt mit Feld "vec2".
     *
     * @param jo JSONObject mit "vec2"-Feld
     * @return Vec2D oder null bei Fehler
     */
    public static Vec2D fromJson(JSONObject jo) {
        try {
            JSONArray jvec = jo.getJSONArray("vec2");
            return new Vec2D(jvec.getInt(0), jvec.getInt(1));
        } catch (JSONException e) {
            System.err.println("Vec.fromJson(jo): invalid data: " + jo.toString(2));
            return null;
        }
    }
}
