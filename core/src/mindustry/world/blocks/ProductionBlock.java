package mindustry.world.blocks;

import arc.graphics.g2d.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class ProductionBlock extends Block{
    public static final byte autoPauseNone = 0;
    public static final byte autoPauseOutputFull = 1;

    public ProductionBlock(String name){
        super(name);
        setupBlock(this);
    }

    public static void setupBlock(Block block){
        block.configurable = true;
        block.clearOnDoubleTap = false;
        block.config(Boolean.class, (ProductionBuild build, Boolean value) -> build.autoMode = value);
    }

    public static void wrapPowerConsumer(Block block){
        if(block.consPower != null && !(block.consPower instanceof AutoPausedConsumePower)){
            ConsumePower power = block.consPower;
            block.removeConsumer(power);
            block.consume(new AutoPausedConsumePower(power));
        }
    }

    @Override
    public void init(){
        wrapPowerConsumer(this);
        super.init();
    }

    public static class AutoPausedConsumePower extends ConsumePower{
        public final ConsumePower base;

        public AutoPausedConsumePower(ConsumePower base){
            super(base.usage, base.capacity, base.buffered);
            this.base = base;
            optional = base.optional;
            booster = base.booster;
            update = base.update;
        }

        @Override
        public boolean ignore(){
            return base.ignore();
        }

        @Override
        public float efficiency(Building build){
            return base.efficiency(build);
        }

        @Override
        public void display(Stats stats){
            base.display(stats);
        }

        @Override
        public float requestedPower(Building entity){
            if(entity instanceof ProductionBuild build && !build.productionPowerActive()){
                return 0f;
            }
            return base.requestedPower(entity);
        }
    }

    public static class ProductionBuild extends Building{
        public boolean autoMode = true;

        public boolean productionShouldConsume(){
            return enabled && !productionAutoPaused();
        }

        public boolean productionPowerActive(){
            return !productionAutoPaused();
        }

        public boolean productionAutoPaused(){
            return autoMode && productionPauseReason() != autoPauseNone;
        }

        public byte productionPauseReason(){
            return productionOutputsFull() && !productionInputsMissing() ? autoPauseOutputFull : autoPauseNone;
        }

        public boolean productionOutputsFull(){
            return false;
        }

        public boolean productionInputsMissing(){
            for(Consume consume : block.nonOptionalConsumers){
                if(consume == block.consPower) continue;
                if(consume.efficiency(this) < 0.9999f){
                    return true;
                }
            }
            return false;
        }

        public void writeProduction(Writes write){
            write.bool(autoMode);
        }

        public void readProduction(Reads read, byte revision, int autoModeRevision){
            autoMode = revision >= autoModeRevision ? read.bool() : true;
        }

        @Override
        public Object config(){
            return autoMode;
        }

        @Override
        public BlockStatus status(){
            if(productionAutoPaused()){
                return BlockStatus.noOutput;
            }
            return super.status();
        }

        @Override
        public void buildConfiguration(Table table){
            table.table(Styles.black6, t -> {
                t.defaults().size(90f, 40f);
                t.button("Auto", Styles.togglet, () -> configure(true)).checked(button -> autoMode);
                t.button("Manual", Styles.togglet, () -> configure(false)).checked(button -> !autoMode);

                Image image = t.image().size(40f).padLeft(4f).get();
                image.update(() -> {
                    image.visible = productionAutoPaused();
                    image.setDrawable(Icon.pause);
                    image.setColor(Pal.remove);
                });
            });
        }

        @Override
        public void drawStatus(){
            super.drawStatus();

            if(!productionAutoPaused()) return;

            float multiplier = block.size > 1 ? 1f : 0.64f;
            float cx = x - (block.size * tilesize / 2f) + (tilesize * multiplier / 2f);
            float cy = y + (block.size * tilesize / 2f) - (tilesize * multiplier / 2f);
            float size = 6f * multiplier;

            Draw.z(Layer.power + 1.1f);
            Draw.color(Pal.gray);
            Fill.square(cx, cy, 3f * multiplier, 45f);
            Draw.color(Pal.remove);
            Draw.rect(Icon.pause.getRegion(), cx, cy, size, size);
            Draw.color();
        }
    }
}
