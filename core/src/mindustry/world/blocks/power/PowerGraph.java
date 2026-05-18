package mindustry.world.blocks.power;

import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;

public class PowerGraph{
    private static final Queue<Building> queue = new Queue<>();
    private static final Seq<Building> outArray1 = new Seq<>();
    private static final Seq<Building> outArray2 = new Seq<>();
    private static final IntSet closedSet = new IntSet();

    //do not modify any of these unless you know what you're doing!
    public final Seq<Building> producers = new Seq<>(false, 16, Building.class);
    public final Seq<Building> consumers = new Seq<>(false, 16, Building.class);
    public final Seq<Building> batteries = new Seq<>(false, 16, Building.class);
    public final Seq<Building> all = new Seq<>(false, 16, Building.class);

    private final @Nullable PowerGraphUpdater entity;
    private final WindowedMean powerBalance = new WindowedMean(60);
    private float lastPowerProduced, lastPowerNeeded, lastPowerStored;
    private float lastScaledPowerIn, lastScaledPowerOut, lastCapacity;
    //diodes workaround for correct energy production info
    private float energyDelta = 0f;

    private final int graphID;
    private static int lastGraphID;
    
    // Smart power load balancing
    private boolean smartLoadBalancing = true;
    private final Seq<Building> disabledConsumers = new Seq<>(false, 16, Building.class);
    private float lastPowerDeficit = 0f;

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
            if(consumer.shouldConsumePower){
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
            //TODO how would it even be null
            var cons = consumer.block.consPower;
            
            // Check if consumer is disabled by smart load balancing
            boolean isDisabled = disabledConsumers.contains(consumer);
            
            if(cons.buffered){
                if(!Mathf.zero(cons.capacity)){
                    // Add an equal percentage of power to all buffers, based on the global power coverage in this graph
                    // But disabled consumers get no power
                    float maximumRate = isDisabled ? 0f : cons.requestedPower(consumer) * coverage * consumer.delta();
                    if(isDisabled){
                        // For disabled buffered consumers, gradually discharge
                        consumer.power.status = Mathf.clamp(consumer.power.status - 0.01f * consumer.delta());
                    }else{
                        consumer.power.status = Mathf.clamp(consumer.power.status + maximumRate / cons.capacity);
                    }
                }
            }else{
                //valid consumers get power as usual
                if(consumer.shouldConsumePower()){
                    consumer.power.status = isDisabled ? 0f : coverage;
                }else{ //invalid consumers get an estimate, if they were to activate
                    if(!isDisabled){
                        consumer.power.status = Math.min(1, produced / (needed + cons.usage * consumer.delta()));
                        //just in case
                        if(Float.isNaN(consumer.power.status)){
                            consumer.power.status = 0f;
                        }
                    }else{
                        consumer.power.status = 0f;
                    }
                }
            }
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

        float powerNeeded = getPowerNeeded();
        float powerProduced = getPowerProduced();
        float batteryStored = getBatteryStored();
        float batteryCapacity = getTotalBatteryCapacity();
        
        lastPowerNeeded = powerNeeded;
        lastPowerProduced = powerProduced;
        lastPowerStored = batteryStored;

        lastScaledPowerIn = (powerProduced + energyDelta) / Time.delta;
        lastScaledPowerOut = powerNeeded / Time.delta;
        lastCapacity = batteryCapacity;

        powerBalance.add((lastPowerProduced - lastPowerNeeded + energyDelta) / Time.delta);
        energyDelta = 0f;

        if(!(consumers.size == 0 && producers.size == 0 && batteries.size == 0)){
            boolean charged = false;
            float totalAvailablePower = powerProduced + batteryStored;
            
            // Smart load balancing
            if(smartLoadBalancing && powerNeeded > totalAvailablePower + 0.01f){
                // Power deficit, disable non-critical buildings
                performSmartLoadBalancing(powerProduced, batteryStored, powerNeeded);
            }else if(smartLoadBalancing && disabledConsumers.size > 0 && totalAvailablePower >= powerNeeded + 0.1f){
                // Power restored, try to re-enable buildings
                tryRestoreDisabledBuildings(powerProduced, batteryStored, powerNeeded);
            }
            
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
    
    /**
     * Performs smart load balancing when there's a power deficit.
     * Disables non-critical buildings first based on their powerPriority.
     */
    private void performSmartLoadBalancing(float powerProduced, float batteryStored, float powerNeeded){
        float powerDeficit = powerNeeded - (powerProduced + batteryStored);
        lastPowerDeficit = powerDeficit;
        
        // Sort consumers by priority (lower priority first - will be disabled first)
        Seq<Building> sortedConsumers = new Seq<>(consumers);
        sortedConsumers.sort((a, b) -> Integer.compare(a.block.powerPriority, b.block.powerPriority));
        
        float powerToSave = powerDeficit;
        
        for(Building consumer : sortedConsumers){
            if(powerToSave <= 0f) break;
            
            // Don't disable already disabled consumers, batteries, or producers
            if(disabledConsumers.contains(consumer) || consumer.block.outputsPower) continue;
            
            // Get the power this consumer is using
            float consumerPowerUsage = 0f;
            if(consumer.shouldConsumePower() && consumer.block.consPower != null){
                consumerPowerUsage = consumer.block.consPower.requestedPower(consumer) * consumer.delta();
            }
            
            if(consumerPowerUsage > 0f){
                // Disable this consumer to save power
                disableConsumer(consumer);
                powerToSave -= consumerPowerUsage;
                
                // Also update the original power needed calculation
                powerNeeded -= consumerPowerUsage;
            }
        }
        
        // Update last power needed after balancing
        lastPowerNeeded = powerNeeded;
    }
    
    /**
     * Tries to restore disabled buildings when power is available.
     */
    private void tryRestoreDisabledBuildings(float powerProduced, float batteryStored, float powerNeeded){
        float availablePower = (powerProduced + batteryStored) - powerNeeded;
        
        if(availablePower <= 0.01f) return;
        
        // Sort disabled consumers by priority (higher priority first - will be enabled first)
        Seq<Building> sortedDisabled = new Seq<>(disabledConsumers);
        sortedDisabled.sort((a, b) -> Integer.compare(b.block.powerPriority, a.block.powerPriority));
        
        float powerUsed = 0f;
        
        for(Building consumer : sortedDisabled){
            // Check if we have enough power to restore this consumer
            float consumerPowerUsage = 0f;
            if(consumer.block.consPower != null){
                // Assume it will consume its normal power if restored
                consumerPowerUsage = consumer.block.consPower.requestedPower(consumer) * consumer.delta();
            }
            
            if(consumerPowerUsage > 0f && powerUsed + consumerPowerUsage <= availablePower + 0.01f){
                // Restore this consumer
                enableConsumer(consumer);
                powerUsed += consumerPowerUsage;
            }
        }
    }
    
    /**
     * Disables a consumer building.
     */
    private void disableConsumer(Building consumer){
        if(!disabledConsumers.contains(consumer)){
            disabledConsumers.add(consumer);
            // Note: We don't directly disable the building here, 
            // as that would interfere with the normal power distribution logic.
            // Instead, we track it and adjust power distribution accordingly.
        }
    }
    
    /**
     * Enables a previously disabled consumer building.
     */
    private void enableConsumer(Building consumer){
        disabledConsumers.remove(consumer);
    }
    
    /**
     * Gets the power needed, excluding disabled consumers for smart balancing.
     */
    @Override
    public float getPowerNeeded(){
        float powerNeeded = 0f;
        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            // Skip disabled consumers for power calculation
            if(disabledConsumers.contains(consumer)) continue;
            
            var consumePower = consumer.block.consPower;
            if(consumer.shouldConsumePower()){
                powerNeeded += consumePower.requestedPower(consumer) * consumer.delta();
            }
        }
        return powerNeeded;
    }
    
    /**
     * Sets whether smart load balancing is enabled.
     */
    public void setSmartLoadBalancing(boolean enabled){
        this.smartLoadBalancing = enabled;
    }
    
    /**
     * Gets whether smart load balancing is enabled.
     */
    public boolean isSmartLoadBalancing(){
        return smartLoadBalancing;
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
            // Also add to disabled consumers if needed
            if(graph.disabledConsumers.contains(tile) && !disabledConsumers.contains(tile)){
                disabledConsumers.add(tile);
            }
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
        disabledConsumers.clear();
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
        disabledConsumers.remove(build);
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
