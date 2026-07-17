package edu.curtin.app.message;

import edu.curtin.app.fleet.FleetCoordinator;
import edu.curtin.app.fleet.FleetException;
import edu.curtin.app.port.Port;


//“supply size type origin destination” (e.g., supply 4500 carpets A Z)
public class SupplyMessageParser implements ParseStrategy {
    
    private final FleetCoordinator coordinator;

    public SupplyMessageParser(FleetCoordinator coordinator) {
        this.coordinator = coordinator;
    }
    
    @Override
    public boolean canParse(String[] message) {
        return message != null 
            && message.length == 5 
            && "supply".equals(message[0]);
    }

    @Override
    public void parse(String[] message) throws MessageException {
        String cargo, pickupId, dropoffId;
        Port pickup, dropoff;
        int amount;
        try {
            amount = Integer.parseInt(message[1]);
        } catch (NumberFormatException e) {
            throw new MessageException(String.join(" ", message) + " failed to parse fields as integers", e);
        }
        cargo = message[2];
        pickupId = message[3];
        dropoffId = message[4];
        try {
            pickup = coordinator.getFleet().getPort(pickupId);
            dropoff = coordinator.getFleet().getPort(dropoffId);
            coordinator.assignSupplyTask(pickup, dropoff, cargo, amount);
        } catch (FleetException e) {
            throw new MessageException("Supply task references unknown port: ", e);
        }
    }
}
