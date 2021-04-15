package tileworld.agent;

import tileworld.Parameters;
import tileworld.environment.*;
import tileworld.exceptions.CellBlockedException;
import tileworld.planners.AstarPathGenerator;
import tileworld.planners.TWPath;

import javax.swing.plaf.nimbus.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static tileworld.environment.TWDirection.N;
import static tileworld.environment.TWDirection.S;

public class GYAgent extends TWAgent {

    public enum STATE {

        SPLIT_REGION, MOVE_TO_CORNER, FIND_FUEL_STATION, PLAN_GREEDY, PLAN_TO_REFUEL, FULL_FUEL, LOW_FUEL;
    }

    private String name;
    private Boolean hasNewMessage;
    // private Boolean foundFuel; use fuelStationX != -1
    private Boolean searchX;
    private Boolean prevMoveBlocked;
    private Boolean diverted;
    private TWDirection prevDir;
    private Message message;
    private STATE state;
    private int targetX, targetY;
    private int temptargetX=-1,temptargetY=-1;
    private TWDirection tempdir;
    private int tempflag=-1;
    private String[] otherAgentName;
    private int[] otherAgentLocX = new int[2];
    private int[] otherAgentLocY = new int[2];
    private long[] otherAgentTimestamp = new long[2];
    private int distanceToFuelStation = -1;
    private TWDirection[] generalDir = new TWDirection[4];
    private AstarPathGenerator astarPath;
    private boolean goGeneralDirection = false;

    // depend on name of the agent, set the fixedLoc
    private int[] fixedLoc1;
    private int[] fixedLoc2;
    private boolean goLoc1;

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
        // this.foundFuel = false;
        this.prevMoveBlocked = false;
        this.message = new Message(this.name,"","");
        this.state = STATE.SPLIT_REGION;
        this.targetX = this.targetY = -1;
        this.otherAgentName = new String[2];
//        this.score = 0; done in super function
        this.fuelLevel = fuelLevel;
        this.diverted = false;

        this.astarPath = new AstarPathGenerator(this.getEnvironment(), this, 999);
//        this.carriedTiles = new ArrayList<TWTile>();
//        this.sensor = new TWAgentSensor(this, Parameters.defaultSensorRange);
//        this.memory = new TWAgentWorkingMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
        int xDim = getEnvironment().getxDimension();
        int yDim = getEnvironment().getyDimension();
        if (name.equals("agent1")) {
            fixedLoc1 = new int[]{xDim/8, yDim/6};
            fixedLoc2 = new int[]{xDim*3/8, yDim*3/6};
        } else if (name.equals("agent2")) {
            fixedLoc1 = new int[]{xDim*7/8, yDim/6};
            fixedLoc2 = new int[]{xDim*5/8, yDim*3/6};
        } else if (name.equals("agent3")) {
            fixedLoc1 = new int[]{xDim*1/8, yDim*5/6};
            fixedLoc2 = new int[]{xDim*7/8, yDim*5/6};
        }
        System.out.println("fixed 1 " + fixedLoc1[0] + " " + fixedLoc1[1]);
        System.out.println("fixed 2 " + fixedLoc2[0] + " " + fixedLoc2[1]);
        goLoc1 = true;
    }





    public void communicate() {
        if (hasNewMessage) {
            //Message message = new Message(this.name,"b",m);
            this.getEnvironment().receiveMessage(this.message); // this will send the message to the broadcast channel of the environment
            hasNewMessage = false;
        }
    }

    protected TWThought think() {
        //System.out.println("FUELSTATION:::::::::::::::::::::");
        //System.out.println(fuelStationX);
        //System.out.println(fuelStationY);
        TWThought thought;
        checkMessage();
        //System.out.println("I'm' "+this.name+" my position is "+this.x+","+this.y);
        //System.out.println("____________________________________________________________________________________________");

        if (fuelStationX != -1) distanceToFuelStation = (int)astarPath.getMovementCost(x,y,fuelStationX,fuelStationY);
        checkLowFuelLevel(); // if fuel level < 50 or fuel level < 2 * distance, change to low_fuel mode

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
                sendMyLocation();
                thought = planGreedy();
                break;
            case LOW_FUEL: // case 1: fuel level < distance * 2; case 2: fuel level < 50
                thought= planGostationnow();
                break;
            default:
                thought = new TWThought(TWAction.IDLE, TWDirection.Z);
                break;
        }


        //to refuel, find the path to fuel station, then follow the direction
        //if (this.fuelLevel200 100)
        //highest level: lowest fuellvel
        // if (this.fuelLevel>=100 && this.fuelLevel<=200)
        // state=STATE.PLAN_TO_REFUEL
        //thought=new TWThought(LAN_TO_REFUEL)
        //    if (this.fuelLevel<=200

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
                        System.out.println("Blocked, finding the next possible direction");
                        if (goGeneralDirection) {
                            int generalDirIndex = 1;
                            while (generalDirIndex <= 3 && getEnvironment().isCellBlocked(x+dir.dx, y+dir.dy)) {
                                dir = generalDir[generalDirIndex++];
                            }
                        } else {
                            prevMoveBlocked = true;
                            prevDir = dir;
                            dir = getAltDirection(dir); // randomly pick one direction from two
                        }
                    } else
                        prevMoveBlocked = false;
                    move(dir);
                } catch (CellBlockedException ex) {
                    System.out.println("Blocked twice, not move in this round"); //should not happen because checked before unless surrounded by 4 obstacles;
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
                        //System.out.println(m.getFrom() + " is at x-" + m.getX() + " y-" + m.getY() + " @" + m.getTimestamp());
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
                        if (fuelStationX == -1)
                            if(m.getFuelStationX() != -1) {
                                this.fuelStationX = m.getFuelStationX();
                                this.fuelStationY = m.getFuelStationY();
                            }
                        if (state == STATE.SPLIT_REGION)
                            getRegion();
                        break;
                    case FOUND_FUEL:
                        this.fuelStationX = m.getFuelStationX();
                        this.fuelStationY = m.getFuelStationY();
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
        this.message.setFuelStationX(fuelStationX);
        this.message.setFuelStationY(fuelStationY);
        this.message.setTimestamp();
        this.message.setMessage("nothing here");
        hasNewMessage = true;
    }

    private void sendFuelStationLocation(){
        this.message.setMessageType(Message.MESSAGE_TYPE.FOUND_FUEL);
        this.message.setX(fuelStationX);
        this.message.setY(fuelStationY);
        this.message.setFuelStationX(fuelStationX);
        this.message.setFuelStationY(fuelStationY);
        this.message.setTimestamp();
        this.message.setMessage("nothing here");
        hasNewMessage = true;
    }

    /**
     *
     * Helper Functions
     *
     */

    private TWDirection getDirSimple(int x, int y, int targetX, int targetY) {
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
                dir = N;
            } else if (targetY > y) {
                dir = TWDirection.S;
            } else {
                dir = TWDirection.Z;
            }
        }
        return dir;
    }

    private TWDirection getDir(int x, int y, int targetX, int targetY) {
        TWPath path_to_station =astarPath.findPath(this.x, this.y, targetX, targetY);
        if (path_to_station == null) return getDirSimple(x, y, targetX, targetY);
        TWDirection dir = path_to_station.getStep(0).getDirection();
        return dir;
    }

    private TWDirection getAltDirection(TWDirection dir){
        Random random = new Random();
        int num = random.nextInt(2);
        switch (dir) {
            case N: case S:
                return num == 0 ? TWDirection.W : TWDirection.E;
            case W: case E:
                return num == 0 ? TWDirection.N : TWDirection.S;
        }
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
            int[] xCorner = new int[]{0,
                    (int)Math.floor(getEnvironment().getxDimension()/3),
                    (int)Math.floor(getEnvironment().getxDimension()*2/3)};
            this.targetX = xCorner[index] + Parameters.defaultSensorRange-1;
            if(y<getEnvironment().getyDimension()/2) {
                this.targetY = Parameters.defaultSensorRange - 1;
                searchX = false;
            }
            else{
                this.targetY = getEnvironment().getyDimension() - Parameters.defaultSensorRange;
                searchX = false;
            }

            state = STATE.MOVE_TO_CORNER;
        }
    }



    private boolean checkLowFuelLevel() {
        if ((distanceToFuelStation != -1 && fuelLevel <= 2 * distanceToFuelStation) || (this.fuelLevel<=50)) {
            state = STATE.LOW_FUEL;
            goGeneralDirection = false;
            return true;
        }
        return false;
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

    private TWDirection collectNearByOnMyWay(TWDirection nextDir){
        if (!diverted) {
            if (nextDir == TWDirection.S | nextDir == TWDirection.N) {
                if (x > 0 && x < getEnvironment().getxDimension() - 1) {
                    if (this.getEnvironment().doesCellContainObject(x - 1, y)) {
                        TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                                .get(this.getX() - 1, this.getY());
                        if (e instanceof TWTile) {
                            if (this.carriedTiles.size() < 3) {
                                prevDir = TWDirection.E;
                                diverted = true;
                                return TWDirection.W;
                            }
                        } else if (e instanceof TWHole) {
                            if (this.hasTile()) {
                                prevDir = TWDirection.E;
                                diverted = true;
                                return TWDirection.W;
                            }
                        }
                    } else if (this.getEnvironment().doesCellContainObject(x + 1, y)) {
                        //System.out.println("something here");
                        TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                                .get(this.getX() + 1, this.getY());
                        if (e instanceof TWTile) {
                            if (this.carriedTiles.size() < 3) {
                                prevDir = TWDirection.W;
                                diverted = true;
                                return TWDirection.E;
                            }
                        } else if (e instanceof TWHole) {
                            if (this.hasTile()) {
                                prevDir = TWDirection.W;
                                diverted = true;
                                return TWDirection.E;
                            }
                        }
                    }
                }
            } else if (nextDir == TWDirection.E | nextDir == TWDirection.W) {
                //System.out.println(name + " " + nextDir);
                if (y > 0 && y < getEnvironment().getyDimension() - 1) {
                    if (this.getEnvironment().doesCellContainObject(x, y - 1)) {
                        TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                                .get(this.getX(), this.getY() - 1);
                        if (e instanceof TWTile) {
                            if (this.carriedTiles.size() < 3) {
                                prevDir = TWDirection.S;
                                diverted = true;
                                return TWDirection.N;
                            }
                        } else if (e instanceof TWHole) {
                            if (this.hasTile()) {
                                prevDir = TWDirection.S;
                                diverted = true;
                                return TWDirection.N;
                            }
                        }
                    } else if (this.getEnvironment().doesCellContainObject(x, y + 1)) {
                        //System.out.println("something here");
                        TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                                .get(this.getX(), this.getY() + 1);
                        if (e instanceof TWTile) {
                            if (this.carriedTiles.size() < 3) {
                                prevDir = TWDirection.N;
                                diverted = true;
                                return TWDirection.S;
                            }
                        } else if (e instanceof TWHole) {
                            if (this.hasTile()) {
                                prevDir = TWDirection.N;
                                diverted = true;
                                return TWDirection.S;
                            }
                        }
                    }
                }
            }
        }
        else
        {
            nextDir = prevDir;
            diverted = false;
        }
        return nextDir;
    }

    private void checkSwitchTarget() {
        // target is loc1
        if (goLoc1 && (int)astarPath.getMovementCost(x,y,fixedLoc1[0],fixedLoc1[1]) <= 4) {
            goLoc1 = false;
            if (name.equals("agent1")) {
                System.out.println("switched");
            }
            return;
        }
        // target is loc2, get near
        if (goLoc1 == false && (int)astarPath.getMovementCost(x,y,fixedLoc2[0],fixedLoc2[1]) <= 4) {
            goLoc1 = true;
            if (name.equals("agent1")) {
                System.out.println("switched");
            }
            return;
        }
    }

    private void computeGeneralDirRanking() {
        HashMap<TWDirection, Integer> scores = new HashMap<>();
        scores.put(TWDirection.N, 0);
        scores.put(TWDirection.S, 0);
        scores.put(TWDirection.W, 0);
        scores.put(TWDirection.E, 0);

        // scores from distance to fuel station
        TWDirection[] ranking = new TWDirection[4];

        if (fuelLevel <= 500 && fuelLevel >= 400) { // away from fuel station
            ranking = rankDirections(x, y, fuelStationX, fuelStationY);
            addScores(scores, ranking, 3, 2, -2, -3);
//        } else if (fuelLevel >= 300 && fuelLevel <= 400) {
//            ranking = rankDirections(x, y, fuelStationX, fuelStationY);
//            addScores(scores, ranking, 3, 2, -2, -3);
//        } else if (fuelLevel >= 200 && fuelLevel <= 300) {
//            ranking = rankDirections(x, y, fuelStationX, fuelStationY);
//            addScores(scores, ranking, 0, 0, 0, 0);
        } else if (fuelLevel >= 100 && fuelLevel <= 200) {
            ranking = rankDirections(x, y, fuelStationX, fuelStationY);
            addScores(scores, ranking, -4, -3, 3, 4);
        } else if (fuelLevel <= 100) {
            ranking = rankDirections(x, y, fuelStationX, fuelStationY);
            addScores(scores, ranking, -6, -5, 5, 6);
        }

        // scores from distance between each other
        // to be implemented: add scores to the hashmap
        int distanceToAgent1 = (int)astarPath.getMovementCost(x,y,otherAgentLocX[0], otherAgentLocY[0]);
        int distanceToAgent2 = (int)astarPath.getMovementCost(x,y,otherAgentLocX[1], otherAgentLocY[1]);
        if (distanceToAgent1 <= 6) { //get away with highest motivation
            ranking = rankDirections(x, y, otherAgentLocX[0], otherAgentLocY[0]);
            addScores(scores, ranking, 3, 2, -2, -3);
        } else if (distanceToAgent1 > 6 && distanceToAgent1 <= 20) {
            ranking = rankDirections(x, y, otherAgentLocX[0], otherAgentLocY[0]);
            addScores(scores, ranking, 2, 1, -1, -2);
        } // don't set the upper limit too high so that agents will not be pushed to corner

        if (distanceToAgent2 <= 6) { //get away with highest motivation
            ranking = rankDirections(x, y, otherAgentLocX[1], otherAgentLocY[1]);
            addScores(scores, ranking, 3, 2, -2, -3);
        } else if (distanceToAgent2 > 6 && distanceToAgent2 <= 20) {
            ranking = rankDirections(x, y, otherAgentLocX[1], otherAgentLocY[1]);
            addScores(scores, ranking, 2, 1, -1, -2);
        } // don't set the upper limit too high so that agents will not be pushed to corner

        // scores to go to fixed location
        int[] fixedLoc = goLoc1 == true ? fixedLoc1 : fixedLoc2;
        ranking = rankDirections(x,y,fixedLoc[0], fixedLoc[1]); //the ranking is to go away
        addScores(scores, ranking, -5, -4, 4,5);

        // get the final ranking based on scores
        ArrayList<TWDirection> finalRanking = new ArrayList<>();
        scores.entrySet().stream()
                .sorted((k1, k2) -> (k2.getValue() - k1.getValue()))
                .forEach(k -> finalRanking.add(k.getKey()));
        for (int i = 0; i < 4; i++) {
            generalDir[i] = finalRanking.get(i);
        }
        if (name == "agent1") System.out.println("\nnew turn fuel level = " + fuelLevel + " x = " + x + " y = " + y + " N " + scores.get(TWDirection.N) + " S " + scores.get(TWDirection.S) + " W " + scores.get(TWDirection.W)+ " E " + scores.get(TWDirection.E));
        // if (this.name == "agent1") System.out.println("" + generalDir[0] + " " + generalDir[1] + " " + generalDir[2] + " " + generalDir[3]);
    }

    private void addScores(HashMap<TWDirection, Integer> scores, TWDirection[] ranking, int score1, int score2, int score3, int score4) {
        scores.put(ranking[0], scores.get(ranking[0]) + score1);
        scores.put(ranking[1], scores.get(ranking[1]) + score2);
        scores.put(ranking[2], scores.get(ranking[2]) + score3);
        scores.put(ranking[3], scores.get(ranking[3]) + score4);
    }

    private TWDirection[] rankDirections(int x, int y, int targetX, int targetY) {
        // Attaction, should go shorter edge first; for seperation, should go longer edge first. Attraction is dominated here.
        TWDirection[] ranking = null;
        int xDiff = x - targetX;
        int yDiff = y - targetY;
//        if (this.name == "agent1") {
//            System.out.println("x = " + x + " y = " + y + " fuelX = " + fuelStationX + " fuelY = " + fuelStationY);
//            System.out.println(" xDiff= " + xDiff + " ,yDiff= " + yDiff);
//        }
        if (xDiff > 0) { // go E
            if (yDiff < 0) { //go N
                if (Math.abs(xDiff) < Math.abs(yDiff)) { //North-South is the longer edge, move first
                    ranking = new TWDirection[]{TWDirection.N, TWDirection.E, TWDirection.W, TWDirection.S};
                } else {
                    ranking = new TWDirection[]{TWDirection.E, TWDirection.N, TWDirection.S, TWDirection.W};
                }
            } else { // go S
                if (Math.abs(xDiff) < Math.abs(yDiff)) { // (previously) North-South is the shorter edge, N/S first, cover more space
                    ranking = new TWDirection[]{TWDirection.S, TWDirection.E, TWDirection.W, TWDirection.N};
                } else {
                    ranking = new TWDirection[]{TWDirection.E, TWDirection.S, TWDirection.N, TWDirection.W};
                }
            }
        } else { // go W
            if (yDiff < 0) { // go N
                if (Math.abs(xDiff) < Math.abs(yDiff)) { // (previously) North-South is the shorter edge, N/S first, cover more space
                    ranking = new TWDirection[]{TWDirection.N, TWDirection.W, TWDirection.E, TWDirection.S};
                } else {
                    ranking = new TWDirection[]{TWDirection.W, TWDirection.N, TWDirection.S, TWDirection.E};
                }
            } else { //go S
                if (Math.abs(xDiff) < Math.abs(yDiff)) { // (previously) North-South is the shorter edge, N/S first, cover more space
                    ranking = new TWDirection[]{TWDirection.S, TWDirection.W, TWDirection.E, TWDirection.N};
                } else {
                    ranking = new TWDirection[]{TWDirection.W, TWDirection.S, TWDirection.N, TWDirection.E};
                }
            }
        }

        return ranking;
    }

    private boolean checkTargetNotBlock()
    {
        if (Math.abs(this.x - targetX) < Parameters.defaultSensorRange)
            if (Math.abs(this.y - targetY) < Parameters.defaultSensorRange)
                if(getEnvironment().isCellBlocked(targetX, targetY))
                    return true;

        return false;

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

        if (fuelStationX != -1) {
            sendFuelStationLocation();
            state = STATE.PLAN_GREEDY;
            return new TWThought(TWAction.IDLE, TWDirection.Z);
        }

        collectPointsOnMyWay();

        if (x==targetX && y==targetY){
            state=STATE.FIND_FUEL_STATION;
            return new TWThought(TWAction.IDLE, TWDirection.Z);
        }
        else{
            if (checkTargetNotBlock()) {
                state = STATE.FIND_FUEL_STATION;
                return new TWThought(TWAction.IDLE, TWDirection.Z);
            }

            TWPath pathToTarget =astarPath.findPath(this.x, this.y, targetX, targetY);
            TWDirection nextdir;
            if (pathToTarget == null)
                nextdir = getDirSimple(x, y, targetX, targetY);
            else
                nextdir = pathToTarget.getStep(0).getDirection();

            if (fuelLevel > 300)
                nextdir = collectNearByOnMyWay(nextdir);

            return new TWThought(TWAction.MOVE, nextdir);
        }
            //return new TWThought(TWAction.MOVE, getDir(x,y,targetX, targetY));
    }

    private TWThought planFindFuelStation(){
        if(!searchX){
            if (y<Parameters.defaultSensorRange*2) {
                this.targetY = getEnvironment().getyDimension() - Parameters.defaultSensorRange;
            }
            else{
                this.targetY = Parameters.defaultSensorRange;
            }
            searchX = true;
        }else{
            this.targetX += (2*(Parameters.defaultSensorRange));

            if (this.x >= getEnvironment().getxDimension()-Parameters.defaultSensorRange)
                this.targetX = Parameters.defaultSensorRange-1;

            if (this.targetX >= getEnvironment().getxDimension())
                this.targetX = getEnvironment().getxDimension()-Parameters.defaultSensorRange;

            searchX = false;
        }
        state = STATE.MOVE_TO_CORNER;
        return new TWThought(TWAction.IDLE, TWDirection.Z);
    }

    private TWThought planGostationnow(){
        if (this.x==fuelStationX &&this.y==fuelStationY) {
            state = STATE.PLAN_GREEDY;
            return new TWThought(TWAction.REFUEL, TWDirection.Z);
        } else {
            return new TWThought(TWAction.MOVE, getDir(this.x, this.y, fuelStationX, fuelStationY));
        }

        //return null;
    }

    private double sumdistance(double xx,double yy,double x0,double y0,double x1,double y1){
        return Math.sqrt(Math.pow(xx-x0,2)+Math.pow(yy-y0,2))+Math.sqrt(Math.pow(xx-x1,2)+Math.pow(yy-y1,2));
    }

    private TWThought planGreedy(){
        checkSwitchTarget(); // if it gets near to a target, should switch to another target
        computeGeneralDirRanking(); // update generalDir
        if (this.name == "agent1") System.out.println("memory size: " + memory.getMemorySize() + " carried Tiles: " + carriedTiles.size());
        if (memory.getMemorySize() == 0) {
            goGeneralDirection = true;
            return new TWThought(TWAction.MOVE, generalDir[0]);
        }

        TWHole hole = memory.getNearbyHole(x,y,10);
        TWTile tile = memory.getNearbyTile(x,y,10);

        goGeneralDirection = false;

        // if the current location is a hole/ tile, check conditions and do
        if (hole != null && hole.getX() == x && hole.getY() == y && carriedTiles.size() != 0) {
            return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
        } else if (tile != null && tile.getX() == x && tile.getY() == y && carriedTiles.size() < 3) {
            return new TWThought(TWAction.PICKUP, TWDirection.Z);
        } else if (x == fuelStationX && y == fuelStationY && fuelLevel <= 250) {
            return new TWThought(TWAction.REFUEL, TWDirection.Z);
        }
        // if very near to fuel station and less than half fuel, directly go refuel
        if (astarPath.getMovementCost(x,y,fuelStationX,fuelStationY) <= 5 && fuelLevel <= 250) return new TWThought(TWAction.MOVE, getDir(x,y,fuelStationX, fuelStationY));

        // go for the direction of the nearest hole/ tile, depend on the size of carriedTiles
        if (carriedTiles.size() != 0 && carriedTiles.size() < 3) {
            // go to holes first, if null, then go to tile
            if (hole != null) {
                if (name == "agent1") System.out.println("1/2 go to hole x: " + hole.getX() + " y: " + hole.getY());
                return new TWThought(TWAction.MOVE, getDir(x,y,hole.getX(), hole.getY()));
            } else if (tile != null) {
                if (name == "agent1") System.out.println("1/2 go to tile x: " + tile.getX() + " y: " + tile.getY());
                return new TWThought(TWAction.MOVE, getDir(x,y,tile.getX(), tile.getY()));
            }
        } else if (carriedTiles.size() == 0) {
            // only go to tiles, ignore holes, if null then go general dir
            if (tile != null) {
                if (name == "agent1") System.out.println("0 go to tile x: "  + tile.getX() + " y: " + tile.getY());
                return new TWThought(TWAction.MOVE, getDir(x,y,tile.getX(), tile.getY()));
            }
        } else if (carriedTiles.size() == 3) {
            // only go to holes, ignore tiles, if null then go general dir
            if (hole != null) {
                if (name == "agent1") System.out.println("3 go to hole x: " + hole.getX() + " y: " + hole.getY());
                return new TWThought(TWAction.MOVE, getDir(x,y,hole.getX(), hole.getY()));
            }
        }
        if (name == "agent1") System.out.println("go general direction");

        //System.out.println("Default case, Simple Score: " + this.score);
        //System.out.println("FUEL level:"+this.fuelLevel);
        // default case, go general direction
        goGeneralDirection = true;
        return new TWThought(TWAction.MOVE, generalDir[0]);
    }
}
