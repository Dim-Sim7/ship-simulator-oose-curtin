package edu.curtin.app.message;

import edu.curtin.app.storm.Storm;
import edu.curtin.app.storm.StormFactory;
import edu.curtin.app.storm.StormManager;


//“storm x y r ” (e.g., storm 12 19 5)
public class StormMessageParser implements ParseStrategy {
    
    private final StormFactory stormFactory;
    private final StormManager stormManager;

    public StormMessageParser(StormFactory stormFactory, StormManager stormManager) {
        if (stormFactory == null) {
            throw new MessageException("StormMessageParser needs a non-null storm factory");
        }
        if (stormManager == null) {
            throw new MessageException("StormMessageParser needs a non-null storm observer");
        }
        this.stormFactory = stormFactory;
        this.stormManager = stormManager;
    }

    @Override
    public boolean canParse(String[] message) {
        return message != null
            && message.length == 4
            && "storm".equals(message[0]);
    }

    @Override
    public void parse(String[] message) throws MessageException {
        int x, y, radius;
        try {
            x = Integer.parseInt(message[1]);
            y = Integer.parseInt(message[2]);
            radius = Integer.parseInt(message[3]);
        } catch (NumberFormatException e) {
            throw new MessageException(String.join(" ",  message) + " failed to parse fields as integers", e);
        }
        Storm storm  = stormFactory.create(x, y, radius);
        stormManager.addStorm(storm);
    }
}
