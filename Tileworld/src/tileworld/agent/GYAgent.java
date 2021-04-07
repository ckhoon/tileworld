package tileworld.agent;

import tileworld.Parameters;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.planners.AstarPathGenerator;
import tileworld.planners.TWPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static tileworld.environment.TWDirection.S;

public class GYAgent extends TWAgent {

    public enum STATE {

        SPLIT_REGION, MOVE_TO_CORNER, FIND_FUEL_STATION, PLAN_GREEDY,PLAN_TO_REFUEL;
    }

    private String name;
    private Boolean hasNewMessage;
    private Boolean foundFuel;
    private Boolean searchX;
    private Boolean prevMoveBlocked;
    private TWDirection prevDir;
    private Message message;
    private STATE state;
    private int targetX, targetY;
    private String[] otherAgentName;
    private int[] otherAgentLocX = new int[2];
    private int[] otherAgentLocY = new int[2];
    private long[] otherAgentTimestamp = new long[2];

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
        this.prevMoveBlocked = false;
        this.message = new Message(this.name,"","");
        this.state = STATE.SPLIT_REGION;
        this.targetX = this.targetY = -1;
        this.otherAgentName = new String[2];
//        this.score = 0; done in super function
        this.fuelLevel = fuelLevel;
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
        System.out.println("FUELSTATION:::::::::::::::::::::");
        System.out.println(fuelStationX);
        System.out.println(fuelStationY);
        TWThought thought;
        checkMessage();
        System.out.println("I'm' "+this.name+" my position is "+this.x+","+this.y);
        System.out.println("____________________________________________________________________________________________");
        sendMyLocation();

        switch(state){
            case SPLIT_REGION:
                thought = planSplitRegion();
                break;
            case MOVE_TO_CORNER:
                thought = planMoveToCorner();
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


        //to refuel, find the path to fuel station, then follow the direction
        //if (this.fuelLevel200 100)
        //highest level: lowest fuellvel
        if (this.fuelLevel>=100 && this.fuelLevel<=200)
            state=STATE.PLAN_TO_REFUEL
            //thought=new TWThought(LAN_TO_REFUEL)
        if (this.fuelLevel<=200

        if (this.fuelLevel<=400){
            if (this.x==fuelStationX &&this.y==fuelStationY)
                thought=new TWThought(TWAction.REFUEL,TWDirection.Z);
            else{
                AstarPathGenerator a = new AstarPathGenerator(this.getEnvironment(), this, 999);
                //find path

                TWPath path =a.findPath(this.x, this.y, fuelStationX, fuelStationY);
                TWDirection nextdir=path.getStep(0).getDirection();
                thought=new TWThought(TWAction.MOVE,nextdir);

            }
        }





        return thought ;
    }

    protected void act(TWThought thought) {

        TWAction action = thought.getAction();
        //System.out.println("carreid tiles: " + carriedTiles.size() + " memory size: " + memory.getMemorySize() + " action: " + action + " direction: " + thought.getDirection());
        //System.out.println(name + " fuel: " + getFuelLevel());
        switch(action) {
            case MOVE:
                try {
                    TWDirection dir = thought.getDirection();
                    if(getEnvironment().isCellBlocked(x+dir.dx, y+dir.dy)) {
                        System.out.println("cell blocked");
                        prevMoveBlocked = true;
                        prevDir = dir;
                        dir = getAltDirection(dir);
                        generalDir = dir;
                    }else
                        prevMoveBlocked = false;
                    move(dir);
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

    /**
     *
     * Functions for message and communication
     *
     */

    private void checkMessage() {
        ArrayList<Message> message = this.getEnvironment().getMessages();
        for (int i=0; i<message.size(); i++){
            Message m = message.get(i);
            if (m.getFrom() != this.name){
                switch(m.getMessageType()){
                    case MY_X_Y:
                        System.out.println(m.getFrom() + " is at x-" + m.getX() + " y-" + m.getY() + " @" + m.getTimestamp());
                        if (otherAgentName[0] == null | otherAgentName[0]==m.getFrom()) {
                            otherAgentName[0] = m.getFrom();
                            otherAgentLocX[0] = m.getX();
                            otherAgentLocY[0] = m.getY();
                            otherAgentTimestamp[0] = m.getTimestamp();
                        }
                        else {
                            otherAgentName[1] = m.getFrom();
                            otherAgentLocX[1] = m.getX();
                            otherAgentLocY[1] = m.getY();
                            otherAgentTimestamp[1] = m.getTimestamp();
                        }
                        if (state == STATE.SPLIT_REGION)
                            getRegion();
                        break;
                    case FOUND_FUEL:
                        this.fuelStationX = m.getX();
                        this.fuelStationY = m.getY();
                        break;
                    case UPDATE_MY_X_Y:
                        if (otherAgentName[0].equals(m.getFrom())) {
                            otherAgentLocX[0] = m.getX();
                            otherAgentLocY[0] = m.getY();
                        }
                        else if (otherAgentName[1].equals(m.getFrom())) {
                            otherAgentLocX[1] = m.getX();
                            otherAgentLocY[1] = m.getY();
                        }
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
        this.message.setTimestamp();
        this.message.setMessage("nothing here");
        hasNewMessage = true;
    }

    private void sendFuelStationLocation(){
        this.message.setMessageType(Message.MESSAGE_TYPE.FOUND_FUEL);
        this.message.setX(fuelStationX);
        this.message.setY(fuelStationY);
        this.message.setTimestamp();
        this.message.setMessage("nothing here");
        hasNewMessage = true;
    }

    private void sendUpdateLocation(){
        this.message.setMessageType(Message.MESSAGE_TYPE.UPDATE_MY_X_Y);
        this.message.setX(this.getX());
        this.message.setY(this.getY());
        this.message.setTimestamp();
        this.message.setMessage("nothing here");
        hasNewMessage = true;
    }

    /**
     *
     * Helper Functions
     *
     */

    private TWDirection getDir(int x, int y, int targetX, int targetY) {
        // System.out.println("Inside get direction: " + x + " " + y + " " + targetX + " " + targetY);
        TWDirection dir;
        if (prevMoveBlocked) {
            dir = prevDir;
        }
        else {
            if (targetX > x) {
                dir = TWDirection.E;
            } else if (targetX < x) {
                dir = TWDirection.W;
            } else if (targetY < y) {
                dir = TWDirection.N;
            } else if (targetY > y) {
                dir = TWDirection.S;
            } else {
                dir = TWDirection.Z;
            }
        }
        return dir;
    }

    private TWDirection getAltDirection(TWDirection dir){
        while(getEnvironment().isCellBlocked(x+dir.dx, y+dir.dy))
            dir = dir.next();
        return dir;
    }

    private void getRegion(){
        if (otherAgentName[0] != null && otherAgentName[1] != null){
            int index = 0;
            if (getX() > otherAgentLocX[0])
                index += 1;
            if (getX() > otherAgentLocX[1])
                index += 1;
            if (getX() == otherAgentLocX[0]){
                if(this.message.getTimestamp() > otherAgentTimestamp[0])
                    index += 1;
            }
            if (getX() == otherAgentLocX[1]){
                if(this.message.getTimestamp() > otherAgentTimestamp[1])
                    index += 1;
            }
            System.out.println(index);
            int[] xCorner = new int[]{0,
                    (int)Math.floor(getEnvironment().getxDimension()/3),
                    (int)Math.floor(getEnvironment().getxDimension()*2/3)};
            this.targetX = xCorner[index] + Parameters.defaultSensorRange;
            this.targetY = Parameters.defaultSensorRange;
            searchX = false;
            state = STATE.MOVE_TO_CORNER;
        }
    }

    private void collectPointsOnMyWay(){
        if (this.getEnvironment().doesCellContainObject(x, y)) {
            //System.out.println("something here");
            TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                    .get(this.getX(), this.getY());
            if (e instanceof TWTile) {
                if (this.carriedTiles.size() < 3) {
                    this.pickUpTile((TWTile) e);
                    memory.clearEntityFromMemory(x, y);
                }
            } else if (e instanceof TWHole) {
                if (this.hasTile())
                    this.putTileInHole((TWHole) e);
            }
        }
    }

    /**
     *
     * Functions for plans
     *
     */

    private TWThought planSplitRegion(){
        sendMyLocation();
        return new TWThought(TWAction.IDLE, TWDirection.Z);
    }

    private TWThought planMoveToCorner(){
        //System.out.println("I am at " + x + " " + y + " I am going to " + targetX + " " + targetY);
        if (fuelStationX != -1) {
            sendFuelStationLocation();
            System.out.println("FFFFFFFFFFFFFFFFFFFF");
            state = STATE.PLAN_GREEDY;
            return new TWThought(TWAction.IDLE, TWDirection.Z);
        }

        //collectPointsOnMyWay();

        if (x==targetX && y==targetY){
            state=STATE.FIND_FUEL_STATION;
            return new TWThought(TWAction.IDLE, TWDirection.Z);
        }
        else
            return new TWThought(TWAction.MOVE, getDir(x,y,targetX, targetY));
    }

    private TWThought planFindFuelStation(){
        if(!searchX){
            if (y==Parameters.defaultSensorRange) {
                this.targetY = getEnvironment().getyDimension() - Parameters.defaultSensorRange;
            }
            else{
                this.targetY = Parameters.defaultSensorRange;
            }
            searchX = true;
        }else{
            this.targetX += (2*Parameters.defaultSensorRange);
            searchX = false;
        }
        state = STATE.MOVE_TO_CORNER;
        return new TWThought(TWAction.IDLE, TWDirection.Z);
    }

    private double sumdistance(double xx,double yy,double x0,double y0,double x1,double y1){
        return Math.sqrt(Math.pow(xx-x0,2)+Math.pow(yy-y0,2))+Math.sqrt(Math.pow(xx-x1,2)+Math.pow(yy-y1,2));
    }
    private TWThought planGreedy(){

        //two agents are close


        //depend
        //generalDir is change to the dir which maximize the manhattan distance with the other 2 agents(perfer leave away from each other)

//In test
//        double d_up=sumdistance(x,y-1,otherAgentLocX[0],otherAgentLocY[0],otherAgentLocX[1],otherAgentLocY[1]);
//        double d_down=sumdistance(x,y+1,otherAgentLocX[0],otherAgentLocY[0],otherAgentLocX[1],otherAgentLocY[1]);
//        double d_left=sumdistance(x-1,y,otherAgentLocX[0],otherAgentLocY[0],otherAgentLocX[1],otherAgentLocY[1]);
//        double d_right=sumdistance(x+1,y,otherAgentLocX[0],otherAgentLocY[0],otherAgentLocX[1],otherAgentLocY[1]);
//        double mdis=Math.max(Math.max(d_up,d_down),Math.max(d_left,d_right));
//        if (mdis==d_up){
//            generalDir=TWDirection.N;
//        }else if (mdis==d_down){
//            generalDir=TWDirection.S;
//        }else if (mdis==d_left){
//            generalDir=TWDirection.W;
//        }else{
//            generalDir=TWDirection.E;
//        }
//        System.out.println("TEST");
//        System.out.println(x+" "+y);
//        System.out.println(otherAgentLocX[0]+" "+otherAgentLocY[0]);
//        System.out.println(otherAgentLocX[1]+" "+otherAgentLocY[1]);
//        System.out.println(generalDir);



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
                return new TWThought(TWAction.MOVE, getDir(x,y,hole.getX(), hole.getY()));
            } else if (tile != null) {
                System.out.println("go to tile");
                return new TWThought(TWAction.MOVE, getDir(x,y,tile.getX(), tile.getY()));
            }
        } else if (carriedTiles.size() == 0) {
            // only go to tiles, ignore holes, if null then go general dir
            if (tile != null) {
                System.out.println("go to tile");
                return new TWThought(TWAction.MOVE, getDir(x,y,tile.getX(), tile.getY()));
            }
        } else if (carriedTiles.size() == 3) {
            // only go to holes, ignore tiles, if null then go general dir
            if (hole != null) {
                System.out.println("go to hole");
                return new TWThought(TWAction.MOVE, getDir(x,y,hole.getX(), hole.getY()));
            }
        }

        System.out.println("Default case, Simple Score: " + this.score);
        System.out.println("FUEL level:"+this.fuelLevel);
        // default case, go general direction
        return new TWThought(TWAction.MOVE, generalDir);
    }
}
