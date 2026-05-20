package mindustry.world.blocks.production;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

public class QualityConverter extends Block{
    public float craftTime = 90f;

    public QualityConverter(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        sync = true;
        itemCapacity = 10;
        flags = EnumSet.of(BlockFlag.factory);
    }

    @Override
    public void load(){
        super.load();
        region = Core.atlas.find("separator", region);
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    @Override
    public void setStats(){
        stats.timePeriod = craftTime;
        super.setStats();
        stats.add(Stat.productionTime, craftTime / 60f, StatUnit.seconds);
    }

    public class QualityConverterBuild extends Building{
        public float progress;
        public float warmup;

        public Item currentInput(){
            for(Item item : content.items()){
                if(items.get(item) > 0 && item.quality.next() != null){
                    return item;
                }
            }
            return null;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(items.total() >= itemCapacity) return false;
            if(item.quality.next() == null) return false;

            Item current = currentInput();
            return current == null || current == item;
        }

        @Override
        public boolean shouldAmbientSound(){
            return currentInput() != null && efficiency > 0f;
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);

            Item input = currentInput();
            if(input != null){
                Draw.color(input.color);
                Draw.rect(input.fullIcon, x, y, 8f, 8f);
                Draw.color();
            }
        }

        @Override
        public void updateTile(){
            Item input = currentInput();

            if(timer(timerDump, dumpTime / timeScale)){
                dump();
            }

            if(input == null || items.get(input) < 3 || efficiency <= 0f){
                warmup = Mathf.approachDelta(warmup, 0f, 0.02f);
                return;
            }

            warmup = Mathf.approachDelta(warmup, 1f, 0.02f);
            progress += getProgressIncrease(craftTime);

            while(progress >= 1f && items.get(input) >= 3){
                items.remove(input, 3);
                offload(input.upgradedQualityItem());
                progress -= 1f;
            }
        }

        @Override
        public boolean canDump(Building to, Item item){
            Item input = currentInput();
            return input == null || item != input;
        }

        @Override
        public int getMaximumAccepted(Item item){
            return itemCapacity;
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
