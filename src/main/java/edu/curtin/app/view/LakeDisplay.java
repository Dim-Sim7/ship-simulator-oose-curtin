package edu.curtin.app.view;


import java.util.List;

import edu.curtin.app.SimulationException;
import edu.curtin.app.port.Port;
import edu.curtin.app.ship.Ship;
import edu.curtin.app.storm.Storm;

/**
 * Responsible for rendering the lake, ships, ports and storms
 * The display is rebuilt and redrawn every simulation tick
 */
public class LakeDisplay {
    
    private final int height;
    private final int width;

    // 2D grid representing the current lake state
    private String[][] lake;

    private static final String WATER = "~~ ";
    private static final String STORM = "!! ";

    public LakeDisplay(int height, int width) {
        if (width <= 0 || height <= 0) {
            throw new SimulationException("LakeDisplay dimensions must be positive: width=" + width + ", height=" + height);
        }
        this.height = height;
        this.width = width;
        lake = new String[height][width]; // row major 2D array (height = row, width = col)
    }

    /**
     * Rebuilds the lake grid for the current simulation tick
     * Rendering order determines visual priority --
     * storms override ports and ships, ports override ships
     */
    private void updateLake(List<Ship> ships, List<Port> ports, List<Storm> storms) {
        if (ships == null || ports == null || storms == null) {
            throw new SimulationException("LakeDisplay requires non-null lists");
        }
        // clear lake to empty water
        clear();

        // add ships
        for (Ship ship : ships) {
            int row = Math.clamp(ship.getY(), 0, height - 1);
            int col = Math.clamp(ship.getX(), 0, width - 1);
            lake[row][col] = shipSymbol(ship); 
        }
        // add ports
        for (Port port : ports) {
            lake[port.getY()][port.getX()] = portSymbol(port);
        }

        // add storms
        for (Storm storm : storms) {
            renderStorm(storm);
        }

        // Storms override ports and ships, ports override ships

    }
    /**
     * Updates and prints the current lake state to the console
     */
    public void render(List<Ship> ships, List<Port> ports, List<Storm> storms) {
        updateLake(ships, ports, storms);

        System.out.print("\033[2J\033[H");  // ANSI escape sequence clears the terminal screen
        System.out.flush();
        System.out.println();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                System.out.print(lake[row][col]);
            }
            System.out.println();
        }
        System.out.print("Press 'Enter' to  stop: ");
    }

    // Fills the lake with water tiles
    private void clear() {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                lake[row][col] = WATER;
            }
        }
    }
    
    /**
     * Renders a circular storm area using a bounding-box calculation
     * Only grid cells inside the storm radius are updated
     */
    private void renderStorm(Storm storm) {
        // Clamp values if they go outside of lake
        int colMin = Math.max(0, storm.getX() - storm.getRadius());
        int colMax = Math.min(width - 1, storm.getX() + storm.getRadius());
        int rowMin = Math.max(0, storm.getY() - storm.getRadius());
        int rowMax = Math.min(height - 1, storm.getY() + storm.getRadius());

        int dx;
        int dy;

        // Loop over bounding box and check if grid is in storm
        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                dx = storm.getX() - col;
                dy = storm.getY() - row;
                // Pythag dx^2 + dy^2 = d^2 == a^2 + b^2 = c^2
                // d^2 <= c^2 ? Inside storm
                if (dx * dx + dy * dy <= storm.getRadius() * storm.getRadius()) {
                        lake[row][col] = STORM;
                }
            }
        }
    }

    /**
     * Returns a formatted display symbol for a ship
     * ANSI colours represent ship status.
     */
    private String shipSymbol(Ship ship) {
        String id = String.format("%-3s", ship.getId());  // pad to 3 chars
        if (ship.needsRescue()) {
            return "\033[41m" + id + "\033[0m";  // red
        } 
        else if (ship.needsPortRepair()) {
            return "\033[43m" + id + "\033[0m";  // yellow
        } 
        else if (ship.isDestroyed()) {
            return "\033[30m" + id + "\033[0m";  // dark
        } 
        else if (ship.hasCurrentTask()) {
            return "\033[36m" + id + "\033[0m";  // cyan
        } 
        else {
            return id;  // normal
        }
    }

    /**
     * Returns a formatted display symbol for a port
     * Ports with docked ships are highlighted green
     */
    private String portSymbol(Port port) {
        String id = port.hasDocked() // Show amount of ships docked in port
            ? String.format("%-3s", port.getId() + port.getDockedCount())
            : String.format("%-3s", port.getId());
        if (port.hasDocked()) {
            return "\033[42m" + id + "\033[0m"; // green background
        }
        return id;
    }


}
