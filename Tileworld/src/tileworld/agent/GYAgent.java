package tileworld.agent;

import tileworld.Parameters;
import tileworld.environment.TWDirection;
import tileworld.environment.TWEnvironment;
import tileworld.environment.TWHole;
import tileworld.environment.TWTile;
import tileworld.exceptions.CellBlockedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static tileworld.environment.TWDirection.S;

public class GYAgent extends TWAgent {

    public enum STATE {

        SPLIT_REGION, FIND_FUEL_STATION, PLAN_GREEDY;
    }

    private String name;
    private Boolean hasNewMessage;
    private Boolean foundFuel;
    private Message message;
    private STATE state;
    private int targetX, targetY;
    private String[] otherAgentName;
    private int[] otherAgentLocX = new int[2];

    /**
     * Fuel level, automatically decremented once per move.
     */
    // protected double fuelLevel;
    /**
     * List of carried tiles - will have a set capacity
     */
    // protected ArrayList<TWTile> carriedTiles;
    /**
     * Sensor class, used for getting information about the environment.
     */
    // protected TWAgentSensor sensor;
    /**
     * Memory which stores sensed facts in the form of tuples (see TWAgentMemoryFact)
     */
    // protected TWAgentWorkingMemory memory;

    public GYAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
        super(xpos, ypos, env, fuelLevel);
        this.name = name;
        this.hasNewMessage = false;
        this.foundFuel = false;
        this.message = new Message(this.name,"","");
        this.state = STATE.SPLIT_REGION;
        this.targetX = this.targetY = -1;
        this.otherAgentName = new String[2];
//        this.score = 0; done in super function
//        this.fuelLevel = fuelLevel;
//        this.carriedTiles = new ArrayList<TWTile>();
//        this.sensor = new TWAgentSensor(this, Parameters.defaultSensorRange);
//        this.memory = new TWAgentWorkingMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
    }


    public void communicate() {
        if (hasNewMessage) {
            //Message message = new Message(this.name,"b",m);
            this.getEnvironment().receiveMessage(this.message); // this will send the message to the broadcast channel of the environment
            hasNewMessage = false;
        }
    }

    protected TWDirection generalDir = TWDirection.E;

    protected TWThought think() {
        TWThought thought;
        checkMessage();

        switch(state){
            case SPLIT_REGION:
                thought = planSplitRegion();
                break;
            case FIND_FUEL_STATION:
                thought = planFindFuelStation();
                break;
            case PLAN_GREEDY:
                thought = planGreedy();
                break;
            default:
                thought = new TWThought(TWAction.IDLE, TWDirection.Z);
                break;
        }

        return thought ;
    }

    private TWDirection getDirection(int x, int y, int targetX, int targetY) {
        // System.out.println("Inside get direction: " + x + " " + y + " " + targetX + " " + targetY);
        TWDirection dir;
        if (targetX > x) {
            dir = TWDirection.E;
        } else if (targetX < x) {
            dir = TWDirection.W;
        } else if (targetY < y) {
            dir = TWDirection.N;
        } else {
            dir = TWDirection.S;
        }
        return dir;
    }

    protected void act(TWThought thought) {

        TWAction action = thought.getAction();
        //System.out.println("carreid tiles: " + carriedTiles.size() + " memory size: " + memory.getMemorySize() + " action: " + action + " direction: " + thought.getDirection());
        //System.out.println(name + " fuel: " + getFuelLevel());
        switch(action) {
            case MOVE:
                try {
                    move(thought.getDirection());
                } catch (CellBlockedException ex) {
                    switch (generalDir){
                        case E:
                            generalDir = TWDirection.S;
                            break;
                        case S:
                            generalDir = TWDirection.W;
                            break;
                        case W:
                            generalDir = TWDirection.N;
                            break;
                        case N:
                            generalDir = TWDirection.E;
                            break;
                    }
                }
                break;
            case PICKUP:
                pickUpTile((TWTile)this.memory.getMemoryGrid().get(x,y));
                memory.clearEntityFromMemory(x, y);
                break;
            case PUTDOWN:
                // putTileInHole((TWHole)this.memory.getMemoryGrid().get(x,y));
                putTileInHole(memory.getNearbyHole(x,y,10));
                memory.clearEntityFromMemory(x,y);
                break;
            case REFUEL:
                refuel();
                break;
            case IDLE:
                break;
        }
    }

    public String getName() {
        return name;
    }

    private void checkMessage() {
        ArrayList<Message> message = this.getEnvironment().getMessages();
        for (int i=0; i<message.size(); i++){
            Message m = message.get(i);
            if (m.getFrom() != this.name){
                switch(m.getMessageType()){
                    case MY_X_Y:
                        System.out.println(m.getFrom() + " is at x-" + m.getX() + " y-" + m.getY());
                        if (otherAgentName[0] == null) {
                            otherAgentName[0] = m.getFrom();
                            otherAgentLocX[0] = m.getX();
                        }
                        else {
                            otherAgentName[1] = m.getFrom();
                            otherAgentLocX[1] = m.getX();
                        }
                        if (state == STATE.SPLIT_REGION)
                            getRegion();
                        break;
                    default:
                        System.out.println("Not suppose to see this.");
                        break;
                }
            }
        }
    }

    private void sendMyLocation(){
        this.message.setMessageType(Message.MESSAGE_TYPE.MY_X_Y);
        this.message.setX(this.getX());
        this.message.setY(this.getY());
        this.message.setMessage("nothing here");
        hasNewMessage = true;
    }

    private void getRegion(){
        if (otherAgentName[0] != null && otherAgentName[1] != null){
            int index = 0;
            if (getX() > otherAgentLocX[0])
                index += 1;
            if (getX() > otherAgentLocX[1])
                index += 1;
            System.out.println(index);
            int[] xCorner = new int[]{0,
                    (int)Math.floor(getEnvironment().getxDimension()/3),
                    (int)Math.floor(getEnvironment().getxDimension()*2/3)};
            this.targetX = xCorner[index] + Parameters.defaultSensorRange;
            this.targetY = Parameters.defaultSensorRange;
            state = STATE.FIND_FUEL_STATION;
        }
    }

    private TWThought planSplitRegion(){
        sendMyLocation();
        return new TWThought(TWAction.IDLE, TWDirection.Z);
    }

    private TWThought planFindFuelStation(){
        //System.out.println("I am at " + x + " " + y + " I am going to " + targetX + " " + targetY);
        return new TWThought(TWAction.MOVE, getDirection(x,y,targetX, targetY));
    }

    private TWThought planGreedy(){
        if (memory.getMemorySize() == 0) {
            return new TWThought(TWAction.MOVE, generalDir);
        }
        TWHole hole = memory.getNearbyHole(x,y,10);
        TWTile tile = memory.getNearbyTile(x,y,10);

        // if the current location is a hole/ tile, check conditions and do
        if (hole != null && hole.getX() == x && hole.getY() == y && carriedTiles.size() != 0) {
            return new TWThought(TWAction.PUTDOWN, generalDir);
        } else if (tile != null && tile.getX() == x && tile.getY() == y && carriedTiles.size() < 3) {
            return new TWThought(TWAction.PICKUP, generalDir);
        }

        // go for the direction of the nearest hole/ tile, depend on the size of carriedTiles
        if (carriedTiles.size() != 0 && carriedTiles.size() < 3) {
            // go to holes first, if null, then go to tile
            if (hole != null) {
                System.out.println("go to hole");
                return new TWThought(TWAction.MOVE, getDirection(x,y,hole.getX(), hole.getY()));
            } else if (tile != null) {
                System.out.println("go to tile");
                return new TWThought(TWAction.MOVE, getDirection(x,y,tile.getX(), tile.getY()));
            }
        } else if (carriedTiles.size() == 0) {
            // only go to tiles, ignore holes, if null then go general dir
            if (tile != null) {
                System.out.println("go to tile");
                return new TWThought(TWAction.MOVE, getDirection(x,y,tile.getX(), tile.getY()));
            }
        } else if (carriedTiles.size() == 3) {
            // only go to holes, ignore tiles, if null then go general dir
            if (hole != null) {
                System.out.println("go to hole");
                return new TWThought(TWAction.MOVE, getDirection(x,y,hole.getX(), hole.getY()));
            }
        }

        System.out.println("Default case, Simple Score: " + this.score);
        // default case, go general direction
        return new TWThought(TWAction.MOVE, generalDir);
    }
}
