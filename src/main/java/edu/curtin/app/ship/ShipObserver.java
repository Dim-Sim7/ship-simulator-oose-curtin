package edu.curtin.app.ship;

import edu.curtin.app.task.Task;
/**
 * Implementation of the observer pattern for ships
 */
public interface ShipObserver {
    // Called when a ship has sunk and should be removed or handled
    void onShipSunk(Ship ship);

    // Called when a ship becomes disabled and must be rescued by another ship
    void onShipDisabled(Ship ship);

    // Called when a ship requires repairs at a port
    void onNeedPortRepairs(Ship ship);

    // Called when a ship successfully completes a task
    void onTaskComplete(Ship ship, Task task);

    // Called when a ship abandons a rescue task
    void onRescueAbandoned(Ship ship, Task curTask);
    
    // Called when a ship enters a port
    // The task parameter describes the reason or activity at the port
    void onEnterPort(Ship ship, String task);

    // Called when a ship exits a port
    // prevX and prevY represent the previous port coordinates
    void onExitPort(Ship ship, int prevX, int prevY);

}
