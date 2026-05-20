package mindustry.world.blocks.production;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.type.Item.ItemQuality;
import mindustry.world.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

public class QualityConverter extends Block{
    public float craftTime = 180f;

    public QualityConverter(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        itemCapacity = 10;
        sync = true;
        flags = EnumSet.of(BlockFlag.factory);
    }

    @Override
    public void setStats(){
        stats.timePeriod = craftTime;
        super.setStats();
        stats.add(Stat.productionTime, craftTime / 60f, StatUnit.seconds);
    }

    @Override
    public void init(){
        super.init();
        for(Item item : content.items()){
            if(item.quality != ItemQuality.RARE){
                itemFilter[item.id] = true;
            }
        }
    }

    @Override
    public boolean acceptsItems(){
        return true;
    }

    public class QualityConverterBuild extends Building{
        public float progress;
        public float warmup;
        public float totalProgress;
        public @Nullable Item currentItem;

        @Override
        public boolean shouldConsume(){
            return items.total() < itemCapacity && enabled;
        }

        @Override
        public boolean shouldAmbientSound(){
            return efficiency > 0;
        }

        @Override
        public void draw(){
            super.draw();

            if(currentItem != null){
                Draw.color(currentItem.color);
                Draw.rect(currentItem.fullIcon, x, y, 4f, 4f);
                Draw.color();
            }
        }

        @Override
        public void updateTile(){
            totalProgress += warmup * delta();

            if(efficiency > 0 && currentItem != null){
                progress += getProgressIncrease(craftTime);
                warmup = Mathf.lerpDelta(warmup, 1f, 0.02f);
            }else{
                warmup = Mathf.lerpDelta(warmup, 0f, 0.02f);
            }

            if(currentItem == null || !items.has(currentItem, Item.QUALITY_CONVERSION_COUNT)){
                currentItem = findConvertible();
            }

            if(currentItem != null && progress >= 1f){
                progress %= 1f;

                Item upgraded = Item.findQualityVariant(currentItem, currentItem.quality.upgrade());
                if(upgraded != null && items.has(currentItem, Item.QUALITY_CONVERSION_COUNT)){
                    items.remove(currentItem, Item.QUALITY_CONVERSION_COUNT);
                    if(items.get(upgraded) < itemCapacity){
                        offload(upgraded);
                    }
                }

                currentItem = null;
            }

            if(timer(timerDump, dumpTime / timeScale)){
                dump();
            }
        }

        public @Nullable Item findConvertible(){
            for(Item item : content.items()){
                if(item.quality == ItemQuality.RARE) continue;
                if(items.has(item, Item.QUALITY_CONVERSION_COUNT)){
                    Item upgraded = Item.findQualityVariant(item, item.quality.upgrade());
                    if(upgraded != null && items.get(upgraded) < itemCapacity){
                        return item;
                    }
                }
            }
            return null;
        }

        @Override
        public float warmup(){
            return warmup;
        }

        @Override
        public float progress(){
            return progress;
        }

        @Override
        public float totalProgress(){
            return totalProgress;
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.progress) return progress;
            return super.sense(sensor);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(progress);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                progress = read.f();
                warmup = read.f();
            }
        }
    }
}