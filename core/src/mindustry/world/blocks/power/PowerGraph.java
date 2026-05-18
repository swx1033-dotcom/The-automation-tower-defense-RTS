package mindustry.world.blocks.power;

import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.world.meta.*;

public class PowerGraph{
    private static final Queue<Building> queue = new Queue<>();
    private static final Seq<Building> outArray1 = new Seq<>();
    private static final Seq<Building> outArray2 = new Seq<>();
    private static final IntSet closedSet = new IntSet();
    private static final Seq<Building> smartLoadSeq1 = new Seq<>(false, 16, Building.class);

    //do not modify any of these unless you know what you're doing!
    public final Seq<Building> producers = new Seq<>(false, 16, Building.class);
    public final Seq<Building> consumers = new Seq<>(false, 16, Building.class);
    public final Seq<Building> batteries = new Seq<>(false, 16, Building.class);
    public final Seq<Building> all = new Seq<>(false, 16, Building.class);

    // --- Smart Load Distribution ---
    /** If true, the smart load system will auto-disable non-critical buildings during power deficit. */
    public boolean smartLoadEnabled = true;
    /** Buildings that have been automatically disabled due to power shortage. */
    public final Seq<Building> autoDisabledConsumers = new Seq<>(false, 8, Building.class);
    /** Consecutive ticks of power deficit (production + battery < demand). Used for hysteresis. */
    private int deficitTicks = 0;
    /** Consecutive ticks of power surplus. Used for hysteresis before re-enabling buildings. */
    private int surplusTicks = 0;
    /** Number of ticks of sustained deficit before auto-disabling begins. */
    public int deficitThreshold = 30;
    /** Number of ticks of sustained surplus before auto-disabled buildings are re-enabled. */
    public int surplusThreshold = 60;
    // --- End Smart Load ---

    private final @Nullable PowerGraphUpdater entity;
    private final WindowedMean powerBalance = new WindowedMean(60);
    private float lastPowerProduced, lastPowerNeeded, lastPowerStored;
    private float lastScaledPowerIn, lastScaledPowerOut, lastCapacity;
    //diodes workaround for correct energy production info
    private float energyDelta = 0f;

    private final int graphID;
    private static int lastGraphID;

    public PowerGraph(){
        entity = PowerGraphUpdater.create();
        entity.graph = this;
        graphID = lastGraphID++;
    }

    public PowerGraph(boolean noEntity){
        entity = null;
        graphID = lastGraphID++;
    }

    public int getID(){
        return graphID;
    }

    public float getLastScaledPowerIn(){
        return lastScaledPowerIn;
    }

    public float getLastScaledPowerOut(){
        return lastScaledPowerOut;
    }

    public float getLastCapacity(){
        return lastCapacity;
    }

    public float getPowerBalance(){
        return powerBalance.rawMean();
    }

    public boolean hasPowerBalanceSamples(){
        return powerBalance.hasEnoughData();
    }

    public float getLastPowerNeeded(){
        return lastPowerNeeded;
    }

    public float getLastPowerProduced(){
        return lastPowerProduced;
    }

    public float getLastPowerStored(){
        return lastPowerStored;
    }

    public static int getPowerPriority(Building build){
        var flags = build.block.flags;
        if(flags.contains(BlockFlag.core) || flags.contains(BlockFlag.turret) ||
            flags.contains(BlockFlag.repair) || flags.contains(BlockFlag.shield)){
            return 2;
        }
        if(flags.contains(BlockFlag.factory) || flags.contains(BlockFlag.drill) ||
            flags.contains(BlockFlag.reactor) || flags.contains(BlockFlag.extinguisher)){
            return 1;
        }
        return 0;
    }

    public boolean isAutoDisabled(Building build){
        return autoDisabledConsumers.contains(build);
    }

    public void transferPower(float amount){
        if(amount > 0){
            chargeBatteries(amount);
        }else{
            useBatteries(-amount);
        }
        energyDelta += amount;
    }

    public float getSatisfaction(){
        if(Mathf.zero(lastPowerProduced)){
            return 0f;
        }else if(Mathf.zero(lastPowerNeeded)){
            return 1f;
        }
        return Mathf.clamp(lastPowerProduced / lastPowerNeeded);
    }

    public float getPowerProduced(){
        float powerProduced = 0f;
        var items = producers.items;
        for(int i = 0; i < producers.size; i++){
            var producer = items[i];
            powerProduced += producer.getPowerProduction() * producer.delta();
        }
        return powerProduced;
    }

    public float getPowerNeeded(){
        float powerNeeded = 0f;
        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            var consumePower = consumer.block.consPower;
            if(consumer.shouldConsumePower && !autoDisabledConsumers.contains(consumer)){
                powerNeeded += consumePower.requestedPower(consumer) * consumer.delta();
            }
        }
        return powerNeeded;
    }

    public float getBatteryStored(){
        float totalAccumulator = 0f;
        var items = batteries.items;
        for(int i = 0; i < batteries.size; i++){
            var battery = items[i];
            if(battery.enabled){
                totalAccumulator += battery.power.status * battery.block.consPower.capacity;
            }
        }
        return totalAccumulator;
    }

    public float getBatteryCapacity(){
        float totalCapacity = 0f;
        var items = batteries.items;
        for(int i = 0; i < batteries.size; i++){
            var battery = items[i];
            if(battery.enabled){
                totalCapacity += (1f - battery.power.status) * battery.block.consPower.capacity;
            }
        }
        return totalCapacity;
    }

    public float getTotalBatteryCapacity(){
        float totalCapacity = 0f;
        var items = batteries.items;
        for(int i = 0; i < batteries.size; i++){
            var battery = items[i];
            if(battery.enabled){
                totalCapacity += battery.block.consPower.capacity;
            }
        }
        return totalCapacity;
    }

    public float useBatteries(float needed){
        float stored = getBatteryStored();
        if(Mathf.equal(stored, 0f)) return 0f;

        float used = Math.min(stored, needed);
        float consumedPowerPercentage = Math.min(1.0f, needed / stored);
        var items = batteries.items;
        for(int i = 0; i < batteries.size; i++){
            var battery = items[i];
            if(battery.enabled){
                battery.power.status *= (1f-consumedPowerPercentage);
            }
        }
        return used;
    }

    public float chargeBatteries(float excess){
        float capacity = getBatteryCapacity();
        //how much of the missing in each battery % is charged
        float chargedPercent = Math.min(excess/capacity, 1f);
        if(Mathf.equal(capacity, 0f)) return 0f;

        var items = batteries.items;
        for(int i = 0; i < batteries.size; i++){
            var battery = items[i];
            //TODO why would it be 0
            if(battery.enabled && battery.block.consPower.capacity > 0f){
                battery.power.status += (1f - battery.power.status) * chargedPercent;
            }
        }
        return Math.min(excess, capacity);
    }

    public void distributePower(float needed, float produced, boolean charged){
        //distribute even if not needed. this is because some might be requiring power but not using it; it updates consumers
        float coverage = Mathf.zero(needed) && Mathf.zero(produced) && !charged && Mathf.zero(lastPowerStored) ? 0f : Mathf.zero(needed) ? 1f : Math.min(1, produced / needed);
        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            //auto-disabled consumers get zero power
            if(autoDisabledConsumers.contains(consumer)){
                consumer.power.status = 0f;
                continue;
            }
            //TODO how would it even be null
            var cons = consumer.block.consPower;
            if(cons.buffered){
                if(!Mathf.zero(cons.capacity)){
                    // Add an equal percentage of power to all buffers, based on the global power coverage in this graph
                    float maximumRate = cons.requestedPower(consumer) * coverage * consumer.delta();
                    consumer.power.status = Mathf.clamp(consumer.power.status + maximumRate / cons.capacity);
                }
            }else{
                //valid consumers get power as usual
                if(consumer.shouldConsumePower){
                    consumer.power.status = coverage;
                }else{ //invalid consumers get an estimate, if they were to activate
                    consumer.power.status = Math.min(1, produced / (needed + cons.usage * consumer.delta()));
                    //just in case
                    if(Float.isNaN(consumer.power.status)){
                        consumer.power.status = 0f;
                    }
                }
            }
        }
    }

    public void smartLoadUpdate(float initialPowerNeeded, float initialPowerProduced, float batteryStored){
        if(!smartLoadEnabled || consumers.isEmpty()) return;

        boolean hasDeficit = (initialPowerProduced + batteryStored) < initialPowerNeeded;
        boolean hasSurplus = (initialPowerProduced + batteryStored) > initialPowerNeeded;

        if(hasDeficit){
            deficitTicks++;
            surplusTicks = 0;

            if(deficitTicks >= deficitThreshold && !consumers.isEmpty()){
                smartLoadSeq1.clear();
                for(Building consumer : consumers){
                    if(consumer.enabled && consumer.shouldConsumePower && consumer.block.consPower != null &&
                       !autoDisabledConsumers.contains(consumer) && getPowerPriority(consumer) == 0){
                        smartLoadSeq1.add(consumer);
                    }
                }

                smartLoadSeq1.sort((a, b) -> Float.compare(
                    a.block.consPower != null ? a.block.consPower.usage : 0f,
                    b.block.consPower != null ? b.block.consPower.usage : 0f
                ));

                for(int i = smartLoadSeq1.size - 1; i >= 0; i--){
                    Building consumer = smartLoadSeq1.items[i];

                    if(initialPowerNeeded <= initialPowerProduced + batteryStored) break;

                    if(!autoDisabledConsumers.contains(consumer)){
                        autoDisabledConsumers.add(consumer);
                        float usage = consumer.block.consPower.requestedPower(consumer) * consumer.delta();
                        initialPowerNeeded -= usage;
                    }
                }

                deficitTicks = 0;
            }
        }else if(hasSurplus){
            surplusTicks++;
            deficitTicks = 0;

            if(surplusTicks >= surplusThreshold && !autoDisabledConsumers.isEmpty()){
                int toReenable = 0;
                float available = initialPowerProduced + batteryStored - initialPowerNeeded;
                while(toReenable < autoDisabledConsumers.size && available > 0){
                    Building candidate = autoDisabledConsumers.get(toReenable);
                    float needed = candidate.block.consPower != null ? candidate.block.consPower.requestedPower(candidate) * candidate.delta() : 0f;

                    if(needed <= available || toReenable == autoDisabledConsumers.size() - 1){
                        autoDisabledConsumers.removeIndex(toReenable);
                        available -= needed;
                    }else{
                        toReenable++;
                    }
                }
                surplusTicks = 0;
            }
        }else{
            deficitTicks = 0;
            surplusTicks = 0;
        }
    }

    public void update(){
        if(!consumers.isEmpty() && consumers.first().cheating()){
            //when cheating, just set status to 1
            for(Building tile : consumers){
                tile.power.status = 1f;
            }

            lastPowerNeeded = lastPowerProduced = 1f;
            return;
        }

        float initialPowerNeeded = getTotalPowerNeeded();
        float initialPowerProduced = getPowerProduced();
        float batteryStored = getBatteryStored();

        smartLoadUpdate(initialPowerNeeded, initialPowerProduced, batteryStored);

        float powerNeeded = getPowerNeeded();
        float powerProduced = initialPowerProduced;

        lastPowerNeeded = powerNeeded;
        lastPowerProduced = powerProduced;

        lastScaledPowerIn = (powerProduced + energyDelta) / Time.delta;
        lastScaledPowerOut = powerNeeded / Time.delta;
        lastCapacity = getTotalBatteryCapacity();
        lastPowerStored = batteryStored;

        powerBalance.add((initialPowerProduced - initialPowerNeeded + energyDelta) / Time.delta);
        energyDelta = 0f;

        if(!(consumers.size == 0 && producers.size == 0 && batteries.size == 0)){
            boolean charged = false;

            if(!Mathf.equal(powerNeeded, powerProduced)){
                if(powerNeeded > powerProduced){
                    float powerBatteryUsed = useBatteries(powerNeeded - powerProduced);
                    powerProduced += powerBatteryUsed;
                    lastPowerProduced += powerBatteryUsed;
                }else if(powerProduced > powerNeeded){
                    charged = true;
                    powerProduced -= chargeBatteries(powerProduced - powerNeeded);
                }
            }

            distributePower(powerNeeded, powerProduced, charged);
        }
    }

    public float getTotalPowerNeeded(){
        float powerNeeded = 0f;
        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            var consumePower = consumer.block.consPower;
            if(consumer.shouldConsumePower){
                powerNeeded += consumePower.requestedPower(consumer) * consumer.delta();
            }
        }
        return powerNeeded;
    }

    public void addGraph(PowerGraph graph){
        if(graph == this) return;

        //merge into other graph instead.
        if(graph.all.size > all.size){
            graph.addGraph(this);
            return;
        }

        //other entity should be removed as the graph was merged
        if(graph.entity != null) graph.entity.remove();

        for(Building tile : graph.all){
            add(tile);
        }
        checkAdd();
    }

    public void add(Building build){
        if(build == null || build.power == null) return;

        if(build.power.graph != this || !build.power.init){
            //any old graph that is added here MUST be invalid, remove it
            if(build.power.graph != null && build.power.graph != this){
                if(build.power.graph.entity != null) build.power.graph.entity.remove();
            }

            build.power.graph = this;
            build.power.init = true;
            all.add(build);

            if(build.block.outputsPower && build.block.consumesPower && !build.block.consPower.buffered){
                producers.add(build);
                consumers.add(build);
            }else if(build.block.outputsPower && build.block.consumesPower){
                batteries.add(build);
            }else if(build.block.outputsPower){
                producers.add(build);
            }else if(build.block.consumesPower && build.block.consPower != null){
                consumers.add(build);
            }
        }
    }

    public void checkAdd(){
        if(entity != null) entity.add();
    }

    public void clear(){
        all.clear();
        producers.clear();
        consumers.clear();
        batteries.clear();
        //nothing left
        if(entity != null) entity.remove();
    }

    public void reflow(Building tile){
        queue.clear();
        queue.addLast(tile);
        closedSet.clear();
        while(queue.size > 0){
            Building child = queue.removeFirst();
            add(child);
            checkAdd();
            for(Building next : child.getPowerConnections(outArray2)){
                if(closedSet.add(next.pos())){
                    queue.addLast(next);
                }
            }
        }
    }

    /** Used for unit tests only. */
    public void removeList(Building build){
        all.remove(build);
        producers.remove(build);
        consumers.remove(build);
        batteries.remove(build);
    }

    /** Note that this does not actually remove the building from the graph;
     * it creates *new* graphs that contain the correct buildings. Doing this invalidates the graph. */
    public void remove(Building tile){

        //go through all the connections of this tile
        for(Building other : tile.getPowerConnections(outArray1)){
            //a graph has already been assigned to this tile from a previous call, skip it
            if(other.power.graph != this) continue;

            //create graph for this branch
            PowerGraph graph = new PowerGraph();
            graph.checkAdd();
            graph.add(other);
            //add to queue for BFS
            queue.clear();
            queue.addLast(other);
            while(queue.size > 0){
                //get child from queue
                Building child = queue.removeFirst();
                //add it to the new branch graph
                graph.add(child);
                //go through connections
                for(Building next : child.getPowerConnections(outArray2)){
                    //make sure it hasn't looped back, and that the new graph being assigned hasn't already been assigned
                    //also skip closed tiles
                    if(next != tile && next.power.graph != graph){
                        graph.add(next);
                        queue.addLast(next);
                    }
                }
            }
            //update the graph once so direct consumers without any connected producer lose their power
            graph.update();
        }

        //implied empty graph here
        if(entity != null) entity.remove();
    }

    public int getId(){
        return graphID;
    }

    @Override
    public String toString(){
        return "PowerGraph{" +
        "producers=" + producers +
        ", consumers=" + consumers +
        ", batteries=" + batteries +
        ", all=" + all +
        ", graphID=" + graphID +
        '}';
    }
}
