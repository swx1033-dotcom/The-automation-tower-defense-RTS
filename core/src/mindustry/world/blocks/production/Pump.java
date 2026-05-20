package mindustry.world.blocks.production;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.game.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class Pump extends LiquidBlock{
    public float pumpAmount = 0.2f;
    public float consumeTime = 60f * 5f;
    public float warmupSpeed = 0.019f;
    public DrawBlock drawer = new DrawMulti(new DrawDefault(), new DrawPumpLiquid());

    public Pump(String name){
        super(name);
        ProductionBlock.setupBlock(this);
        group = BlockGroup.liquids;
        floating = true;
        envEnabled = Env.terrestrial;
    }

    @Override
    public void init(){
        ProductionBlock.wrapPowerConsumer(this);
        super.init();
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.output, 60f * pumpAmount * size * size, StatUnit.liquidSecond);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        Tile tile = world.tile(x, y);

        if(valid && tile != null){
            float amount = 0f;
            Liquid liquidDrop = null;

            for(Tile other : tile.getLinkedTilesAs(this, tempTiles)){
                if(canPump(other)){
                    if(liquidDrop != null && other.floor().liquidDrop != liquidDrop){
                        liquidDrop = null;
                        break;
                    }
                    liquidDrop = other.floor().liquidDrop;
                    amount += other.floor().liquidMultiplier;
                }
            }

            if(liquidDrop != null){
                float width = drawPlaceText(Core.bundle.formatFloat("bar.pumpspeed", amount * pumpAmount * 60f, 0), x, y, valid);
                float dx = x * tilesize + offset - width/2f - 4f, dy = y * tilesize + offset + size * tilesize / 2f + 5, s = iconSmall / 4f;
                float ratio = (float)liquidDrop.fullIcon.width / liquidDrop.fullIcon.height;
                Draw.mixcol(Color.darkGray, 1f);
                Draw.rect(liquidDrop.fullIcon, dx, dy - 1, s * ratio, s);
                Draw.reset();
                Draw.rect(liquidDrop.fullIcon, dx, dy, s * ratio, s);
            }
        }
    }

    @Override
    public void load(){
        super.load();
        drawer.load(this);
    }

    @Override
    public TextureRegion[] icons(){
        return drawer.finalIcons(this);
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        if(isMultiblock()){
            Liquid last = null;
            for(Tile other : tile.getLinkedTilesAs(this, tempTiles)){
                if(other.floor().liquidDrop == null) continue;
                if(other.floor().liquidDrop != last && last != null) return false;
                last = other.floor().liquidDrop;
            }
            return last != null;
        }else{
            return canPump(tile);
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        addLiquidBar((PumpBuild build) -> build.liquidDrop);
    }

    protected boolean canPump(Tile tile){
        return tile != null && tile.floor().liquidDrop != null;
    }

    public class PumpBuild extends ProductionBlock.ProductionBuild{
        public float warmup, totalProgress;
        public float consTimer;
        public float amount = 0f;
        public @Nullable Liquid liquidDrop = null;

        @Override
        public void draw(){
            drawer.draw(this);
        }

        @Override
        public void drawLight(){
            super.drawLight();
            drawer.drawLight(this);
        }

        @Override
        public void pickedUp(){
            amount = 0f;
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.efficiency) return shouldConsume() ? efficiency : 0f;
            if(sensor == LAccess.totalLiquids) return liquidDrop == null ? 0f : liquids.get(liquidDrop);
            return super.sense(sensor);
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();

            amount = 0f;
            liquidDrop = null;

            for(Tile other : tile.getLinkedTiles(tempTiles)){
                if(canPump(other)){
                    liquidDrop = other.floor().liquidDrop;
                    amount += other.floor().liquidMultiplier;
                }
            }
        }

        @Override
        public boolean shouldConsume(){
            return productionShouldConsume() && liquidDrop != null && liquids.get(liquidDrop) < liquidCapacity - 0.01f;
        }

        @Override
        public boolean productionOutputsFull(){
            return liquidDrop != null && liquids.get(liquidDrop) >= liquidCapacity - 0.01f;
        }

        @Override
        public boolean productionInputsMissing(){
            return liquidDrop == null || amount <= 0.0001f || super.productionInputsMissing();
        }

        @Override
        public void updateTile(){
            if(efficiency > 0 && liquidDrop != null){
                float maxPump = Math.min(liquidCapacity - liquids.get(liquidDrop), amount * pumpAmount * edelta());
                liquids.add(liquidDrop, maxPump);

                if((consTimer += delta()) >= consumeTime){
                    consume();
                    consTimer %= 1f;
                }

                warmup = Mathf.approachDelta(warmup, maxPump > 0.001f ? 1f : 0f, warmupSpeed);
            }else{
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
            }

            totalProgress += warmup * Time.delta;

            if(liquidDrop != null){
                dumpLiquid(liquidDrop);
            }
        }

        @Override
        public float warmup(){
            return warmup;
        }

        @Override
        public float progress(){
            return Mathf.clamp(consTimer / consumeTime);
        }

        @Override
        public float totalProgress(){
            return totalProgress;
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(warmup);
            write.f(totalProgress);
            write.f(consTimer);
            writeProduction(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                warmup = read.f();
                totalProgress = read.f();
                consTimer = read.f();
            }
            readProduction(read, revision, 1);
        }
    }
}
