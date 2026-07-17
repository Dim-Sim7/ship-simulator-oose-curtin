package edu.curtin.app.storm;


import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.utility.Position;

/**
 * Simple factory to create storms
 */
public class StormFactory {
    private static final Logger logger = Logger.getLogger(StormFactory.class.getName());
    
    public StormFactory() {}

    public Storm create(int x, int y, int radius) {
        if (x < 0 || y < 0) {
            throw new StormException("Storm position cannot be negative: x=" + x + ", y=" + y);
        }
        return create(new Position(x, y), radius);
    }

    public Storm create(Position centrePos, int radius) {
        if (centrePos == null) {
            throw new StormException("Position of storm cannot be null");
        }
        if (radius <= 0) {
            throw new StormException("Storm radius cannot be 0 or less");
        }
        Storm storm = new Storm(centrePos, radius);
        logger.log(Level.INFO, () -> "STORM created at x = " + centrePos.getX() + "-- y = " + centrePos.getY() + " with radius "+ radius);
        return storm;
    }
}
