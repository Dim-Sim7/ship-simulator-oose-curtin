package edu.curtin.app.utility;

/**
 * Represents an object that performs logic every simulation tick
 *
 * Classes implementing this interface participate in the
 * simulation update loop managed by SimulationManager
 */
public interface Updatable {
    // Perform one simulation step update
    void update();
}