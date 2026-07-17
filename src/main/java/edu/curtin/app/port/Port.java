package edu.curtin.app.port;

import java.util.Objects;

import edu.curtin.app.utility.Position;

/**
 * Ports exist on the edges of a lake
 * Holds a counter for how many ships are docked
 * Acts as a destination for ship tasks
 */
public class Port {
    private final Position position;
    private final String id;
    private int dockedCount = 0; // This is used to change display of port

    public Port(String id, int x, int y) {
        this.id = id;
        this.position = new Position(x , y);
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    public Position getPosition() {
        return position;
    }

    public String getId() {
        return id;
    }

    public void shipDocked() { 
        dockedCount++; 
    }

    public void shipUndocked() { 
        if (dockedCount > 0) {
            dockedCount--; 
        }
    }
    public int getDockedCount() { 
        return dockedCount; 
    }

    public boolean hasDocked() {
        return dockedCount > 0;
    }

    @Override
    public boolean equals(Object obj) {
        boolean equals = false;
        if (obj instanceof Port port) {
            if ((port.getX() == this.getX()) && (port.getY() == this.getY())) {
                equals = true;
            }
        }
        return equals;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, id);
    }
}
