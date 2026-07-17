package edu.curtin.app.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.fleet.Fleet;
import edu.curtin.app.ship.Ship;
import edu.curtin.app.utility.Updatable;
import edu.curtin.app.view.Journal;
/**
 * Manages all active storms in the simulation and applies
 * storm effects to ships each simulation tick
 */
public class StormManager implements Updatable {
    private static final Logger logger = Logger.getLogger(StormManager.class.getName());

    private final Fleet fleet;
    private final Journal journal;
    private final List<Storm> activeStorms = new ArrayList<>();

    public StormManager(Fleet fleet, Journal journal) {
        this.fleet = fleet;
        this.journal = journal;
    }

    public List<Storm> getStorms() {
        return activeStorms;
    }


    public void addStorm(Storm storm) {
        if (storm == null) {
            throw new StormException("Cannot add null storm");
        }
        // Add to active storms
        activeStorms.add(storm);
        logger.log(Level.INFO, () -> "Storm arrived: x=" + storm.getX() + ", y=" + storm.getY()
            + ", radius=" + storm.getRadius());
    }

    public void removeStorm(Storm storm) {
        activeStorms.remove(storm);
    }

    /**
     * On update, compare each storm to each ship and determine
     * if ships are inside the currently active storms, they then take damage
     * All current storms are removed after each update
     */
    @Override
    public void update() {
        for (Storm storm : new ArrayList<>(activeStorms)) { // copying to avoid concurrency issues
            for (Ship ship : fleet.getShips()) {
                if (storm.isWithinStorm(ship.getX(), ship.getY())) {
                    logger.log(Level.INFO, () -> "Storm hit ship: id=" + ship.getId()
                        + ", health=" + ship.getHealth());  
                    ship.takeDamage();
                    String outcome = ship.isDestroyed() ? "SUNK" : ship.needsRescue() ? "DISABLED" : "TOOK DAMAGE";
                    journal.logStormHitShip(storm.getRadius(), ship.getId(), outcome);
                }
            }
            removeStorm(storm);
        }
    }
}
