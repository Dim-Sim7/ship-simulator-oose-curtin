package edu.curtin.app.fleet;

import java.util.ArrayList;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.port.Port;
import edu.curtin.app.ship.Ship;
import edu.curtin.app.utility.Position;
import edu.curtin.app.utility.Updatable;

/**
 * Fleet owns ships and ports. Used to query information relating to both
 * Calls update() on each ship per simulation tick
 */
public class Fleet implements Updatable {
    private static final Logger logger = Logger.getLogger(Fleet.class.getName());

    // List is used for ship as they are frequently iterated
    // over for simulation updates and to retrieve ships
    // that have specific characteristics that meet certain requirements
    // i.e. finding rescue ships, idle ships
    private List<Ship> ships = new ArrayList<>();
    // Map is used for ports to allow fast lookup by portId
    private final Map<String, Port> ports = new HashMap<>();

    public Fleet() {}

    @Override
    public void update() {
        for (Ship ship : ships) {
            ship.update();
        }
    }

    public void addShip(Ship ship) {
        if (ship == null) {
            throw new FleetException("Cannot add null ship");
        }
        ships.add(ship);
    }

    public void removeShip(int id) {
        boolean removed = ships.removeIf(s -> s.getId() == id);
        if (removed) {
            logger.log(Level.INFO, () -> "Ship removed: id = " + id);
        } else {
            logger.log(Level.WARNING, () -> "Attempted to remove ship that does not exist, id = " + id);
        }
    }

    public Ship getShip(int shipId) {
        Ship foundShip = null;
        for (Ship ship : ships) {
            if (ship.getId() == shipId) {
                foundShip = ship;
                break; // early break after finding ship
            }
        }
        if (foundShip == null) {
            throw new FleetException("Ship not found: id =" + shipId);
        }
        return foundShip;
    }

    public void addPort(Port port) {
        if (port == null) {
            throw new FleetException("Cannot add null port to fleet");
        }
        ports.put(port.getId(), port);
        logger.log(Level.INFO, () -> "Port added: id = " + port.getId());
    }

    public Port getPort(String id) {
        Port port = ports.get(id);
        if (port == null) {
            throw new FleetException("Port not found: id=" + id);
        }
        return port;
    }

    public Port getPort(int x, int y) {
        Port foundPort = null;
        for (Port port : ports.values()) {
            if (port.getX() == x && port.getY() == y) {
                foundPort = port;
                break; // early break after finding port
            }
        }
        if (foundPort == null) {
            throw new FleetException("Port not found: x = " + x + ", y = " + y);
        }
        return foundPort;
    }

    // returns a read only copy of ports
    public List<Port> getPorts() {
        return Collections.unmodifiableList(new ArrayList<>(ports.values()));
    }

    // returns a read only copy of ships
    // https://stackoverflow.com/questions/22636575/unmodifiablemap-java-collections-vs-immutablemap-google
    public List<Ship> getShips() {
        return Collections.unmodifiableList(new ArrayList<>(ships));
    }

    public Ship findIdleShip() {
        Ship idleShip = null;
        for (Ship ship : ships) {
            if (ship.getState().isIdle() && idleShip == null) {
                idleShip = ship;
                break; // early break after finding ship
            }
        }
        return idleShip; // null ship is valid, caller checks
    }

    public Ship findRescueShip() {
        Ship rescueShip = null;
        for (Ship ship : ships) {
            if (ship.canRescue() && rescueShip == null) {
                rescueShip = ship;
                break; // early break after finding ship
            }
        }
        return rescueShip; // null ship is valid, caller checks
    }

    public boolean isAtPort(Ship ship) {
        if (ship == null) {
            throw new FleetException("Cannot check port status of null ship");
        }
        boolean isAtPort = false;
        for (Port port : ports.values()) {
            if (port.getX() == ship.getX() && port.getY() == ship.getY()) {
                isAtPort = true;
            }
        }
        return isAtPort;
    }

    public Port findNearestPort(Ship ship) {
        if (ship == null) {
            throw new FleetException("Cannot find nearest port for null ship");
        }
        if (ports.isEmpty()) {
            logger.log(Level.WARNING, "No ports available to find nearest");
            return null;
        }
        Port closestPort = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        double calculatedDistance;
        for (Port port : getPorts()) {
            calculatedDistance = distanceTo(ship.getPosition(), port.getPosition());
            if (calculatedDistance < closestDistance) {
                closestDistance = calculatedDistance;
                closestPort = port;
            }
        }
        final Port result = closestPort;
        logger.log(Level.INFO, () -> "Closest port to ship " + ship.getId() + " is port " + result.getId());
        return result;
    }

    private double distanceTo(Position shipPos, Position portPos) {
        int dx = shipPos.getX() - portPos.getX();
        int dy = shipPos.getY() - portPos.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

}
