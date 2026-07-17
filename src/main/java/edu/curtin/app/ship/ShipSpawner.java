package edu.curtin.app.ship;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.fleet.Fleet;
// Responsible for creating ships and wiring them into the simulation.
// The ShipFactory only creates ships, while ShipSpawner handles
// observer registration and adding ships to the fleet.
public class ShipSpawner {
    private static final Logger logger = Logger.getLogger(ShipSpawner.class.getName());
    
    private final ShipFactory shipFactory;
    private final Fleet fleet;
    
    private int nextId = 0;

    public ShipSpawner(ShipFactory shipFactory, Fleet fleet) {
        if (shipFactory == null || fleet == null) {
            throw new ShipException("ShipSpawner requires non-null dependencies");
        }
        this.shipFactory = shipFactory;
        this.fleet = fleet;
    }

    // Creates a ship, attaches its observer(s), and adds it to the fleet
    public void spawn(int id, int x, int y, ShipObserver observer) {
        spawn(id, x, y, List.of(observer));
    }

    public void spawn(int id, int x, int y, List<ShipObserver> observers) {
        if (observers == null) {
            throw new ShipException("Cannot spawn ship with null observer: id=" + id);
        }
        Ship ship = shipFactory.create(id, x, y);
        for (ShipObserver observer : observers) {
            if (observer == null) {
                throw new ShipException("Null observer supplied");
            }
            ship.addObserver(observer);
        }
        fleet.addShip(ship);
        logger.log(Level.INFO, () -> "Ship spawned: id=" + id + ", x=" + x + ", y=" + y);
        nextId = Math.max(nextId, id + 1);
        if (nextId > 999) {
            nextId = 0;
        }
    }

    public void spawnReplacement(int x, int y, ShipObserver observer) {
        spawnReplacement(x, y, List.of(observer));
    }

    public void spawnReplacement(int x, int y, List<ShipObserver> observers) {
        if (observers == null) {
            throw new ShipException("Cannot spawn ship with null observer: id=" + nextId);
        }
        Ship ship = shipFactory.create(nextId, x, y);
        for (ShipObserver observer : observers) {
            if (observer == null) {
                throw new ShipException("Null observer supplied");
            }
            ship.addObserver(observer);
        }
        fleet.addShip(ship);
        logger.log(Level.INFO, () -> "Ship spawned: id=" + nextId + ", x=" + x + ", y=" + y); 
        nextId++;
        if (nextId > 999) {
            nextId = 0;
        }
    }

}
