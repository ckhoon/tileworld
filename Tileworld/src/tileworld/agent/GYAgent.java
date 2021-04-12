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
    private TWDirection prevDir;
    private Message message;
    private STATE state;
    private int targetX, targetY;
    private int temptargetX,temptargetY;
    private String[] otherAgentName;
    private int[] otherAgentLocX = new int[2];
    private int[] otherAgentLocY = new int[2];
    private long[] otherAgentTimestamp = new long[2];
    private int distanceToFuelStation = -1;
    AstarPathGenerator a = new AstarPathGenerator(this.getEnvironment(), this, 999);
    private TWDirection[] generalDir = new TWDirection[4];
    private int generalDirIndex = 0; //representing the index of generalDir
    private AstarPathGenerator astarPath;


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

        this.astarPath = new AstarPathGenerator(this.getEnvironment(), this, 999);
//        this.carriedTiles = new ArrayList<TWTile>();
//        this.sensor = new TWAgentSensor(this, Parameters.defaultSensorRange);
//        this.memory = new TWAgentWorkingMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
    }

    public STATE checkFuel(){
        if (this.fuelLevel>=250)
            //0 means the fuel level is enough
            return STATE.FULL_FUEL;
        else if (this.fuelLevel>=100)
            return STATE.PLAN_TO_REFUEL;
        else return STATE.LOW_FUEL;


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
        sendMyLocation();

        if (fuelStationX != -1) distanceToFuelStation = (int)a.getMovementCost(x,y,fuelStationX,fuelStationY);
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
                        if (state == STATE.PLAN_GREEDY) {
                            if (generalDirIndex < 3) generalDirIndex++;
                            dir = generalDir[generalDirIndex];
                        } else {
                            //System.out.println("cell blocked");
                            prevMoveBlocked = true;
                            prevDir = dir;
                            dir = getAltDirection(dir); // randomly pick one direction from two
                            // generalDir = dir;
                        }
                    } else
                        prevMoveBlocked = false;
                    move(dir);
                } catch (CellBlockedException ex) {
                    System.out.println("Blocked"); //should not happen because checked before unless surrounded by 4 obstacles;
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
                        if (state == STATE.SPLIT_REGION)
                            getRegion();
                        break;
                    case FOUND_FUEL:
                        this.fuelStationX = m.getX();
                        this.fuelStationY = m.getY();
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

    /**
     *
     * Helper Functions
     *
     */


    //replace this with astarpathgenerator? The first step is the direction? I use this in path to fuelstation --By zizhao
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
                dir = N;
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



    private boolean checkLowFuelLevel() {
        if ((distanceToFuelStation != -1 && fuelLevel <= 2 * distanceToFuelStation) || (this.fuelLevel<=50)) {
            state = STATE.LOW_FUEL;
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
        if (nextDir == TWDirection.S | nextDir == TWDirection.N){
            if (x > 0 && x < getEnvironment().getxDimension()-1) {
                if (this.getEnvironment().doesCellContainObject(x - 1, y)) {
                    TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                            .get(this.getX()-1, this.getY());
                    if (e instanceof TWTile) {
                        if (this.carriedTiles.size() < 3) {
                            return TWDirection.W;
                        }
                    } else if (e instanceof TWHole) {
                        if (this.hasTile())
                            return TWDirection.W;
                    }
                } else if (this.getEnvironment().doesCellContainObject(x + 1, y)) {
                    //System.out.println("something here");
                    TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                            .get(this.getX()+1, this.getY());
                    if (e instanceof TWTile) {
                        if (this.carriedTiles.size() < 3) {
                            return TWDirection.E;
                        }
                    } else if (e instanceof TWHole) {
                        if (this.hasTile())
                            return TWDirection.E;
                    }
                }
            }
        }else if (nextDir == TWDirection.E | nextDir == TWDirection.W){
            //System.out.println(name + " " + nextDir);
            if (y > 0 && y < getEnvironment().getyDimension()-1) {
                if (this.getEnvironment().doesCellContainObject(x, y-1)) {
                    TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                            .get(this.getX(), this.getY()-1);
                    if (e instanceof TWTile) {
                        if (this.carriedTiles.size() < 3) {
                            return TWDirection.N;
                        }
                    } else if (e instanceof TWHole) {
                        if (this.hasTile())
                            return TWDirection.N;
                    }
                } else if (this.getEnvironment().doesCellContainObject(x, y+1)) {
                    //System.out.println("something here");
                    TWEntity e = (TWEntity) this.getMemory().getMemoryGrid()
                            .get(this.getX(), this.getY()+1);
                    if (e instanceof TWTile) {
                        if (this.carriedTiles.size() < 3) {
                            return TWDirection.S;
                        }
                    } else if (e instanceof TWHole) {
                        if (this.hasTile())
                            return TWDirection.S;
                    }
                }
            }
        }
        return nextDir;
    }

    private void computeGeneralDirRanking() {
        generalDirIndex = 0; //reset index
        HashMap<TWDirection, Integer> scores = new HashMap<>();
        scores.put(TWDirection.N, 0);
        scores.put(TWDirection.S, 0);
        scores.put(TWDirection.W, 0);
        scores.put(TWDirection.E, 0);

        // scores from distance to fuel station
        TWDirection[] ranking = new TWDirection[4];
        if (fuelLevel <= 500 && fuelLevel >= 400) { // away from fuel station
            ranking = rankDirections();
            addScores(scores, ranking, 3, 1, -1, -3);
        } else if (fuelLevel >= 300 && fuelLevel <= 400) {
            ranking = rankDirections();
            addScores(scores, ranking, 1, 0, 0, -1);
        } else if (fuelLevel >= 200 && fuelLevel <= 300) {
            ranking = rankDirections();
            addScores(scores, ranking, 0, 0, 0, 0);
        } else if (fuelLevel >= 100 && fuelLevel <= 200) {
            ranking = rankDirections();
            addScores(scores, ranking, -1, 0, 0, 1);
        } else if (fuelLevel <= 100) {
            ranking = rankDirections();
            addScores(scores, ranking, -3, -1, 1, 3);
        }

        // scores from distance between each other
        // to be implemented: add scores to the hashmap

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

    private TWDirection[] rankDirections() {
        TWDirection[] ranking = null;
        int xDiff = x - fuelStationX;
        int yDiff = y - fuelStationY;
//        if (this.name == "agent1") {
//            System.out.println("x = " + x + " y = " + y + " fuelX = " + fuelStationX + " fuelY = " + fuelStationY);
//            System.out.println(" xDiff= " + xDiff + " ,yDiff= " + yDiff);
//        }
        if (xDiff > 0) { // go E
            if (yDiff < 0) { //go N
                if (Math.abs(xDiff) > Math.abs(yDiff)) { //North-South is the shorter edge, N/S first, cover more space
                    ranking = new TWDirection[]{TWDirection.N, TWDirection.E, TWDirection.W, TWDirection.S};
                } else {
                    ranking = new TWDirection[]{TWDirection.E, TWDirection.N, TWDirection.S, TWDirection.W};
                }
            } else { // go S
                if (Math.abs(xDiff) > Math.abs(yDiff)) { //North-South is the shorter edge, N/S first, cover more space
                    ranking = new TWDirection[]{TWDirection.S, TWDirection.E, TWDirection.W, TWDirection.N};
                } else {
                    ranking = new TWDirection[]{TWDirection.E, TWDirection.S, TWDirection.N, TWDirection.W};
                }
            }
        } else { // go W
            if (yDiff < 0) { // go N
                if (Math.abs(xDiff) > Math.abs(yDiff)) { //North-South is the shorter edge, N/S first, cover more space
                    ranking = new TWDirection[]{TWDirection.N, TWDirection.W, TWDirection.E, TWDirection.S};
                } else {
                    ranking = new TWDirection[]{TWDirection.W, TWDirection.N, TWDirection.S, TWDirection.E};
                }
            } else { //go S
                if (Math.abs(xDiff) > Math.abs(yDiff)) { //North-South is the shorter edge, N/S first, cover more space
                    ranking = new TWDirection[]{TWDirection.S, TWDirection.W, TWDirection.E, TWDirection.N};
                } else {
                    ranking = new TWDirection[]{TWDirection.W, TWDirection.S, TWDirection.N, TWDirection.E};
                }
            }
        }
        // if (this.name == "agent1") System.out.println("" + ranking[0] + " " + ranking[1] + " " + ranking[2] + " " + ranking[3]);
        return ranking;
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
            //System.out.println("FFFFFFFFFFFFFFFFFFFF");
            state = STATE.PLAN_GREEDY;
            return new TWThought(TWAction.IDLE, TWDirection.Z);
        }

        collectPointsOnMyWay();

        if (x==targetX && y==targetY){
            state=STATE.FIND_FUEL_STATION;
            return new TWThought(TWAction.IDLE, TWDirection.Z);
        }
        else{
            TWPath pathToTarget =astarPath.findPath(this.x, this.y, targetX, targetY);
            TWDirection nextdir;
            try {
                nextdir = pathToTarget.getStep(0).getDirection();
            }
            catch(Exception e)
            {
                nextdir = TWDirection.Z;
            }

            nextdir = collectNearByOnMyWay(nextdir);

            return new TWThought(TWAction.MOVE, nextdir);
        }
        //return new TWThought(TWAction.MOVE, getDir(x,y,targetX, targetY));
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

    private TWThought planGostationnow(){
        //maybe the value is set to size*2 is better?

        if (this.x==fuelStationX &&this.y==fuelStationY) {
            state = STATE.PLAN_GREEDY;
            return new TWThought(TWAction.REFUEL, TWDirection.Z);
        }
        else{
            //find path

            //the idea is during the way to fuelstation, if new hole or tile is close to agent, then
            //check the hole/tile location,
            // set to temptargetX,temptargetY
            // compare the distance
            TWHole hole = memory.getNearbyHole(x,y,10);
            TWTile tile = memory.getNearbyTile(x,y,10);
            //temptargetX=hole.getX();
            //temptargetY=hole.getY();
            TWPath path_to_station =a.findPath(this.x, this.y, fuelStationX, fuelStationY);
            //TWPath path_to_temp=a.findPath(this.x, this.y, temptargetX,temptargetY);
            //TWPath temp_to_station=a.findPath(this.x, this.y, temptargetX,temptargetY);


            //if path_to_temp+temp_to_station is much larger than path_to_station,refused to do so.---halfway idea

            TWDirection nextdir=path_to_station.getStep(0).getDirection();
            return new TWThought(TWAction.MOVE,nextdir);
        }

        //return null;
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
        computeGeneralDirRanking(); // update generalDir
        if (this.name == "agent1") System.out.println("memory size: " + memory.getMemorySize() + " carried Tiles: " + carriedTiles.size());
        if (memory.getMemorySize() == 0) {
            return new TWThought(TWAction.MOVE, generalDir[0]);
        }
        TWHole hole = memory.getNearbyHole(x,y,10);
        TWTile tile = memory.getNearbyTile(x,y,10);
        if (this.name == "agent1") System.out.println(hole == null? "hole is null" : ("hole??? " + hole.getX() + " " + hole.getY()));
        if (this.name == "agent1") System.out.println(tile == null? "tile is null" : ("tile??? " + tile.getX() + " " + tile.getY()));

        // if the current location is a hole/ tile, check conditions and do
        if (hole != null && hole.getX() == x && hole.getY() == y && carriedTiles.size() != 0) {
            return new TWThought(TWAction.PUTDOWN, generalDir[0]);
        } else if (tile != null && tile.getX() == x && tile.getY() == y && carriedTiles.size() < 3) {
            return new TWThought(TWAction.PICKUP, generalDir[0]);
        }

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
        return new TWThought(TWAction.MOVE, generalDir[0]);
    }
}
