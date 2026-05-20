package mindustry.world.consumers;

import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import mindustry.world.modules.*;

public class ConsumeItems extends Consume{
    public final ItemStack[] items;

    public ConsumeItems(ItemStack[] items){
        this.items = items;
    }

    protected ConsumeItems(){
        this(ItemStack.empty);
    }

    @Override
    public void apply(Block block){
        block.hasItems = true;
        block.acceptsItems = true;
        for(var stack : items){
            if(stack.item.acceptsEquivalentQualities()){
                for(Item item : stack.item.qualityFamily()){
                    block.itemFilter[item.id] = true;
                }
            }else{
                block.itemFilter[stack.item.id] = true;
            }
        }
    }

    @Override
    public void build(Building build, Table table){
        table.table(c -> {
            int i = 0;
            for(var stack : items){
                c.add(new ReqImage(StatValues.stack(stack.item, Math.round(stack.amount * multiplier.get(build))),
                () -> build.items.hasEquivalent(stack.item, Math.round(stack.amount * multiplier.get(build))))).padRight(8);
                if(++i % 4 == 0) c.row();
            }
        }).left();
    }

    @Override
    public void trigger(Building build){
        float totalMultiplier = 0f;
        int totalAmount = 0;
        Item.Quality highestQuality = Item.Quality.normal;

        for(var stack : items){
            ItemModule.QualityConsumption result = build.items.removeEquivalent(stack.item, Math.round(stack.amount * multiplier.get(build)));
            totalMultiplier += result.totalCraftMultiplier;
            totalAmount += result.amount;
            highestQuality = Item.Quality.max(highestQuality, result.highestQuality);
        }

        build.setItemQualityState(highestQuality, totalAmount == 0 ? 1f : totalMultiplier / totalAmount);
    }

    @Override
    public float efficiency(Building build){
        if(build.consumeTriggerValid()) return 1f;

        for(ItemStack stack : items){
            if(!build.items.hasEquivalent(stack.item, Math.round(stack.amount * multiplier.get(build)))){
                return 0f;
            }
        }
        return 1f;
    }

    @Override
    public void display(Stats stats){
        stats.add(booster ? Stat.booster : Stat.input, stats.timePeriod < 0 ? StatValues.items(items) : StatValues.items(stats.timePeriod, items));
    }
}
