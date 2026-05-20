package mindustry.world.blocks.production;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.meta.*;

public class Incinerator extends ProductionBlock{
    public Effect effect = Fx.fuelburn;
    public Color flameColor = Color.valueOf("ffad9d");

    public Incinerator(String name){
        super(name);
        hasPower = true;
        hasLiquids = true;
        update = true;
        solid = true;
    }

    public class IncineratorBuild extends ProductionBuild{
        public float heat;

        @Override
        public boolean shouldConsume(){
            return productionShouldConsume();
        }

        @Override
        public void updateTile(){
            heat = Mathf.approachDelta(heat, efficiency, 0.04f);
        }

        @Override
        public BlockStatus status(){
            if(productionAutoPaused()) return BlockStatus.noOutput;
            return !enabled ? BlockStatus.logicDisable : heat > 0.5f ? BlockStatus.active : BlockStatus.noInput;
        }

        @Override
        public void draw(){
            super.draw();

            if(heat > 0f){
                float g = 0.3f;
                float r = 0.06f;

                Draw.alpha(((1f - g) + Mathf.absin(Time.time, 8f, g) + Mathf.random(r) - r) * heat);

                Draw.tint(flameColor);
                Fill.circle(x, y, 2f);
                Draw.color(1f, 1f, 1f, heat);
                Fill.circle(x, y, 1f);

                Draw.color();
            }
        }

        @Override
        public void handleItem(Building source, Item item){
            if(Mathf.chance(0.3)){
                effect.at(x, y);
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return heat > 0.5f && enabled;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            if(Mathf.chance(0.02)){
                effect.at(x, y);
            }
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return heat > 0.5f && liquid.incinerable && enabled;
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(heat);
            writeProduction(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            heat = revision >= 1 ? read.f() : 0f;
            readProduction(read, revision, 1);
        }
    }
}
