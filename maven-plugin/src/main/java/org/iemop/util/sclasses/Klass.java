package org.iemop.util.sclasses;
import java.io.Serializable;

public class Klass implements Comparable<Klass>, Serializable {
    private final String locationName;
    private final String className;

    public Klass(String locationName, String className) {
        this.locationName = locationName;
        this.className = className;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getClassName() {
        return className;
    }

    public int compareTo(Klass other) {
        int locationNameComparison = locationName.compareTo(other.getClassName());
        if (locationNameComparison != 0) {
            return locationNameComparison;
        }
        return className.compareTo(other.getClassName());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Klass klass = (Klass) other;
        return locationName.equals(klass.getLocationName()) && className.equals(klass.getClassName());
    }

    @Override
    public int hashCode() {
        return 31 * locationName.hashCode() + className.hashCode();
    }

    @Override
    public String toString() {
        return locationName + "#" + className;
    }
}
