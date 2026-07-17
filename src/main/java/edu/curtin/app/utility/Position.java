package edu.curtin.app.utility;

import java.util.Objects;

import edu.curtin.app.SimulationException;


/**
 * Represents a 2D position in the simulation world
 */
public class Position {
    private int x;
    private int y;

    public Position(int x, int y) {
        // Positions must remain within valid world bounds
        if (x < 0 || y < 0) {
            throw new SimulationException("Position cannot be negative: x=" + x + ", y=" + y);
        }
        this.x = x;
        this.y = y;
    }

    public int getX() { 
        return x; 
    }

    public int getY() { 
        return y; 
    }

    public void setX(int x) { 
        this.x = x; 
    }

    public void setY(int y) { 
        this.y = y; 
    }

    // Move by a certain amount from the current position
    public void stepX(int x) { 
        this.x += x; 
    }

    public void stepY(int y) { 
        this.y += y; 
    }

    @Override
    public boolean equals(Object o) {
        boolean equals = false;
        if ((o instanceof Position p)) { 
            if (x == p.x && y == p.y) {
                equals = true;
            }
        } 
        return equals;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
