package mindustry.world.blocks.production;

import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;
import mindustry.world.meta.*;

public class ProductionBlock extends Block{

    public ProductionBlock(String name){
        super(name);
    }

    public class ProductionBuild extends Building{
        public boolean autoMode = true;

        public boolean isOutputFull(){
            return false;
        }

        @Override
        public boolean shouldConsume(){
            if(!autoMode) return enabled;
            return enabled && !isOutputFull();
        }

        @Override
        public BlockStatus status(){
            if(!enabled) return BlockStatus.logicDisable;
            if(autoMode && isOutputFull()) return BlockStatus.noOutput;
            return super.status();
        }

        @Override
        public void drawStatus(){
            if(block.enableDrawStatus && block.consumers.length > 0){
                float multiplier = block.size > 1 ? 1 : 0.64f;
                float brcx = x + (block.size * tilesize / 2f) - (tilesize * multiplier / 2f);
                float brcy = y - (block.size * tilesize / 2f) + (tilesize * multiplier / 2f);

                Draw.z(Layer.power + 1);
                Draw.color(Pal.gray);
                Fill.square(brcx, brcy, 2.5f * multiplier, 45);
                Draw.color(status().color);
                Fill.square(brcx, brcy, 1.5f * multiplier, 45);
                Draw.color();
            }
        }

        @Override
        public void drawSelect(){
            block.drawOverlay(x, y, rotation);
        }

        @Override
        public void buildConfiguration(Table table){
            table.button(autoMode ? Icon.pause : Icon.play, Styles.clearNoneTogglei, () -> {
                autoMode = !autoMode;
                configureAny(autoMode);
            }).size(50f).update(b -> b.setChecked(autoMode));
        }

        @Override
        public Object config(){
            return autoMode;
        }

        @Override
        public void configured(Unit builder, Object value){
            if(value instanceof Boolean b){
                autoMode = b;
            }
        }

        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(autoMode);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                autoMode = read.bool();
            }
        }
    }
}