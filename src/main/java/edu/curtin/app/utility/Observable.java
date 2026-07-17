package edu.curtin.app.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


/**
 * Generic observable base class for the observer pattern
 * Stores and manages observers of type T and provides shared
 * notification behaviour for observable objects
 * 
 * This removes observer specific fields and methods from concrete
 * observable classes such as Ship, reducing code duplication and improving 
 * extensibility for future observer relationships
 */
public class Observable<T> {
    private final List<T> observers = new ArrayList<>();

    public void addObserver(T observer) {
        if (observer == null) {
            throw new IllegalArgumentException("Observer cannot be null");
        }
        observers.add(observer);
    }

    public void removeObserver(T observer) {
        observers.remove(observer);
    }

    /**
     * Executes the given action for every observer
     *
     * Consumer<T> represents a function that accepts
     * an observer instance and performs some operation on it
     * T is whatever observer type this instance is define by
     *
     * Example: (This is my interpretation of what is happening, I could be wrong)
     * 1. notifyObservers(o -> o.onShipSunk(this)); 
     *  Java infers o is a ShipObserver
     * 
     * 2. Consumer<ShipObserver> action == o -> o.onShipSunk(this);
     *                                  == new Consumer<ShipObserver>() { 
     *                                          public void accept(ShipObserver var1) { var1.onShipSunk(thisShip) }         
     *                                        }                                   
     *
     * 3. observers.forEach(action) -> for (T t : this) { action.accept(t) }
     *                              T = ShipObserver, t = shipObserver
     * 
     * 4. action.accept(t) == action.accept(shipObserver)
     *  accept(t) calls the original lambda body with o as the stored shipObserver
     * 
     * 5. shipObserver.onShipSunk(this)
     * 
     * https://stackoverflow.com/questions/67494505/what-is-a-consumer-in-java
     */
    protected void notifyObservers(Consumer<? super T> action) {
        observers.forEach(action);
    }
}
