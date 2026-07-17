package edu.curtin.app.storm;
import edu.curtin.app.utility.Position;

/**
 * Represents a storm in the system
 * Storms exist for one simulation tick and can damage ships in its' radius
 */
public class Storm {
    private final Position centrePos;
    private final int radius;

    public Storm(int x, int y, int radius) {
        if (x < 0 || y < 0) {
            throw new StormException("Storm position cannot be negative: x=" + x + ", y=" + y);
        }
        if (radius <= 0) {
            throw new StormException("Storm radius must be positive: " + radius);
        }
        this.centrePos = new Position(x, y);
        this.radius = radius;
    }

    public Storm(Position centrePos, int radius) {
        if (centrePos == null) {
            throw new StormException("Storm position cannot be null");
        }
        if (radius <= 0) {
            throw new StormException("Storm radius must be positive: " + radius);
        }
        this.centrePos = centrePos;
        this.radius = radius;
    }

    public Position getPosition() {
        return centrePos;
    }

    public int getX() {
        return centrePos.getX();
    }

    public int getY() {
        return centrePos.getY();
    }

    public int getRadius() {
        return radius;
    }

    public boolean isWithinStorm(int x, int y) {
        if (x < 0 || y < 0) {
            throw new StormException("Cannot check negative coordinates");
        }
        // Calculate horizontal and vertical distance
        int dx = this.getX() - x;
        int dy = this.getY() - y;

        // Check if the distance is within the storm radius
        return Math.sqrt(dx * dx + dy* dy) <= this.getRadius();
    }

}
