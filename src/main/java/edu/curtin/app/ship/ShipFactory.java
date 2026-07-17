package edu.curtin.app.ship;

import edu.curtin.app.utility.Position;

/**
 * Simple factory to create ships
 */
public class ShipFactory {

    public ShipFactory() {}

    public Ship create(int id, int x, int y) {
        if (x < 0 || y < 0) {
            throw new ShipException("Position cannot be negative");
        }
        if (id < 0) {
            throw new ShipException("Id cannot be negative");
        }
        Ship ship = new Ship(id, new Position(x, y));
        return ship;
    }
}
