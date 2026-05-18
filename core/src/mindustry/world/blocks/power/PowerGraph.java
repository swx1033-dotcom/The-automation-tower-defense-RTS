package mindustry.world.blocks.power;

import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.type.Category;

public class PowerGraph{
    private static final Queue<Building> queue = new Queue<>();
    private static final Seq<Building> outArray1 = new Seq<>();
    private static final Seq<Building> outArray2 = new Seq<>();
    private static final IntSet closedSet = new IntSet();
    private static final float loadEpsilon = 0.0001f;

    //do not modify any of these unless you know what you're doing!
    public final Seq<Building> producers = new Seq<>(false, 16, Building.class);
    public final Seq<Building> consumers = new Seq<>(false, 16, Building.class);
    public final Seq<Building> batteries = new Seq<>(false, 16, Building.class);
    public final Seq<Building> all = new Seq<>(false, 16, Building.class);

    private final @Nullable PowerGraphUpdater entity;
    private final WindowedMean powerBalance = new WindowedMean(60);
    private final IntSet autoDisabled = new IntSet();
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
        float chargedPercent = Math.min(excess/capacity, 1f);
        if(Mathf.equal(capacity, 0f)) return 0f;

        var items = batteries.items;
        for(int i = 0; i < batteries.size; i++){
            var battery = items[i];
            if(battery.enabled && battery.block.consPower.capacity > 0f){
                battery.power.status += (1f - battery.power.status) * chargedPercent;
            }
        }
        return Math.min(excess, capacity);
    }

    public void distributePower(float needed, float produced, boolean charged){
        float coverage = Mathf.zero(needed) && Mathf.zero(produced) && !charged && Mathf.zero(lastPowerStored) ? 0f : Mathf.zero(needed) ? 1f : Math.min(1, produced / needed);
        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            var cons = consumer.block.consPower;
            if(cons.buffered){
                if(!Mathf.zero(cons.capacity)){
                    float maximumRate = cons.requestedPower(consumer) * coverage * consumer.delta();
                    consumer.power.status = Mathf.clamp(consumer.power.status + maximumRate / cons.capacity);
                }
            }else{
                if(consumer.shouldConsumePower){
                    consumer.power.status = coverage;
                }else{
                    consumer.power.status = Math.min(1, produced / (needed + cons.usage * consumer.delta()));
                    if(Float.isNaN(consumer.power.status)){
                        consumer.power.status = 0f;
                    }
                }
            }
        }
    }

    private boolean isManagedConsumer(Building consumer){
        return consumer != null && consumer.isValid() && consumer.block.consPower != null && !consumer.block.outputsPower && !consumer.block.consPower.buffered;
    }

    private float getManagedConsumerDemand(Building consumer){
        return isManagedConsumer(consumer) && consumer.enabled && consumer.shouldConsumePower ? consumer.block.consPower.requestedPower(consumer) * consumer.delta() : 0f;
    }

    private float getRestoredConsumerDemand(Building consumer){
        if(!isManagedConsumer(consumer)) return 0f;
        if(consumer.enabled) return getManagedConsumerDemand(consumer);

        consumer.enabled = true;
        consumer.updateConsumption();
        float demand = consumer.shouldConsumePower ? consumer.block.consPower.requestedPower(consumer) * consumer.delta() : 0f;
        consumer.enabled = false;
        consumer.updateConsumption();
        return demand;
    }

    private void restoreAllAutoDisabled(){
        if(autoDisabled.isEmpty()) return;

        var items = consumers.items;
        for(int i = 0; i < consumers.size; i++){
            var consumer = items[i];
            if(autoDisabled.contains(consumer.pos()) && !consumer.enabled){
                consumer.enabled = true;
                consumer.updateConsumption();
            }
        }

        autoDisabled.clear();
    }

    private int getPriority(Building consumer){
        if(consumer == null || consumer.block == null || consumer.block.category == null) return 0;
        switch(consumer.block.category){
            case turret: return 5;
            case defense: return 4;
            case liquid: return 3;
            case distribution: return 2;
            case logic: return 1;
            default: return 0;
        }
    }

    private void restoreConsumers(float powerProduced){
        if(autoDisabled.isEmpty()) return;

        while(true){
            float powerNeeded = getPowerNeeded();
            Building best = null;
            float bestDemand = Float.MAX_VALUE;
            int bestPriority = -1;
            boolean restored = false;
            var items = consumers.items;

            for(int i = 0; i < consumers.size; i++){
                var consumer = items[i];
                if(!autoDisabled.contains(consumer.pos())) continue;

                if(consumer.enabled){
                    autoDisabled.remove(consumer.pos());
                    restored = true;
                    break;
                }

                float demand = getRestoredConsumerDemand(consumer);
                if(demand <= loadEpsilon){
                    consumer.enabled = true;
                    consumer.updateConsumption();
                    autoDisabled.remove(consumer.pos());
                    restored = true;
                    break;
                }

                int priority = getPriority(consumer);
                if(powerNeeded + demand <= powerProduced + loadEpsilon){
                    if(priority > bestPriority || (priority == bestPriority && demand < bestDemand)){
                        best = consumer;
                        bestDemand = demand;
                        bestPriority = priority;
                    }
                }
            }

            if(restored) continue;
            if(best == null) break;

            best.enabled = true;
            best.updateConsumption();
            autoDisabled.remove(best.pos());
        }
    }

    private void shedConsumers(float availablePower){
        float deficit = getPowerNeeded() - availablePower;
        if(deficit <= loadEpsilon) return;

        while(deficit > loadEpsilon){
            Building best = null;
            float bestDemand = 0f;
            int bestPriority = Integer.MAX_VALUE;
            var items = consumers.items;

            for(int i = 0; i < consumers.size; i++){
                var consumer = items[i];
                if(autoDisabled.contains(consumer.pos())) continue;

                float demand = getManagedConsumerDemand(consumer);
                if(demand > loadEpsilon){
                    int priority = getPriority(consumer);
                    if(priority < bestPriority || (priority == bestPriority && demand > bestDemand + loadEpsilon)){
                        best = consumer;
                        bestDemand = demand;
                        bestPriority = priority;
                    }
                }
            }

            if(best == null) break;

            best.enabled = false;
            best.updateConsumption();
            best.power.status = 0f;
            autoDisabled.add(best.pos());
            deficit -= bestDemand;
        }
    }

    public void update(){
        if(!consumers.isEmpty() && consumers.first().cheating()){
            restoreAllAutoDisabled();
            for(Building tile : consumers){
                tile.power.status = 1f;
            }

            lastPowerNeeded = lastPowerProduced = 1f;
            return;
        }

        float powerProduced = getPowerProduced();
        restoreConsumers(powerProduced);
        shedConsumers(powerProduced + getBatteryStored());

        float powerNeeded = getPowerNeeded();
        powerProduced = getPowerProduced();

        lastPowerNeeded = powerNeeded;
        lastPowerProduced = powerProduced;

        lastScaledPowerIn = (powerProduced + energyDelta) / Time.delta;
        lastScaledPowerOut = powerNeeded / Time.delta;
        lastCapacity = getTotalBatteryCapacity();
        lastPowerStored = getBatteryStored();

        powerBalance.add((lastPowerProduced - lastPowerNeeded + energyDelta) / Time.delta);
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

    public void addGraph(PowerGraph graph){
        if(graph == this) return;

        if(graph.all.size > all.size){
            graph.addGraph(this);
            return;
        }

        if(graph.entity != null) graph.entity.remove();

        for(Building tile : graph.all){
            add(tile);
        }
        checkAdd();
    }

    public void add(Building build){
        if(build == null || build.power == null) return;

        if(build.power.graph != this || !build.power.init){
            PowerGraph previous = build.power.graph;
            boolean wasAutoDisabled = previous != null && previous.autoDisabled.contains(build.pos());

            if(previous != null && previous != this){
                if(previous.entity != null) previous.entity.remove();
            }

            build.power.graph = this;
            build.power.init = true;
            all.add(build);

            if(wasAutoDisabled){
                autoDisabled.add(build.pos());
            }

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
        autoDisabled.clear();
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

    public void removeList(Building build){
        all.remove(build);
        producers.remove(build);
        consumers.remove(build);
        batteries.remove(build);
        autoDisabled.remove(build.pos());
    }

    public void remove(Building tile){

        for(Building other : tile.getPowerConnections(outArray1)){
            if(other.power.graph != this) continue;

            PowerGraph graph = new PowerGraph();
            graph.checkAdd();
            graph.add(other);
            queue.clear();
            queue.addLast(other);
            while(queue.size > 0){
                Building child = queue.removeFirst();
                graph.add(child);
                for(Building next : child.getPowerConnections(outArray2)){
                    if(next != tile && next.power.graph != graph){
                        graph.add(next);
                        queue.addLast(next);
                    }
                }
            }
            graph.update();
        }

        autoDisabled.remove(tile.pos());
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
