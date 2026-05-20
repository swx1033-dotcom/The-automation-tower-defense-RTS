package mindustry.world.blocks.production;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.annotations.Annotations.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

public class ProductionBlock extends Block{
    public boolean hasAutoMode = true;

    public ProductionBlock(String name){
        super(name);
        sync = true;
    }

    public enum AutoShutdownReason{
        none,
        outputFull,
        noInput
    }

    public class ProductionBuild extends Building{
        public boolean autoMode = true;
        public AutoShutdownReason autoShutdownReason = AutoShutdownReason.none;

        @Override
        public boolean shouldConsume(){
            if(!enabled) return false;
            if(!autoMode) return true;
            return autoShutdownReason == AutoShutdownReason.none;
        }

        @Override
        public AutoShutdownReason getAutoShutdownReason(){
            return autoShutdownReason;
        }

        public boolean isAutoShutdown(){
            return autoMode && autoShutdownReason != AutoShutdownReason.none;
        }

        @Override
        public BlockStatus status(){
            if(!enabled){
                return BlockStatus.logicDisable;
            }

            if(isAutoShutdown()){
                return autoShutdownReason == AutoShutdownReason.outputFull ? BlockStatus.noOutput : BlockStatus.noInput;
            }

            if(!shouldConsume()){
                return BlockStatus.noOutput;
            }

            if(efficiency <= 0 || !productionValid()){
                return BlockStatus.noInput;
            }

            return BlockStatus.active;
        }

        @Override
        public void buildConfiguration(Table table){
            if(hasAutoMode){
                table.row();
                table.add(new AtomicTextureRegion("auto-on", "auto-off", () -> autoMode, bool -> {
                    autoMode = bool;
                    if(!autoMode){
                        autoShutdownReason = AutoShutdownReason.none;
                    }
                    deselect();
                })).pad();
            }
        }

        @Override
        public Boolean config(){
            return autoMode;
        }

        @Override
        public void configure(Object value){
            if(value instanceof Boolean b){
                autoMode = b;
                if(!autoMode){
                    autoShutdownReason = AutoShutdownReason.none;
                }
            }
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(autoMode);
            write.b(autoShutdownReason.ordinal());
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                autoMode = read.bool();
                autoShutdownReason = AutoShutdownReason.values()[read.b()];
            }
        }

        public void checkAutoShutdown(){
            if(!autoMode) return;
            autoShutdownReason = calculateAutoShutdownReason();
        }

        public AutoShutdownReason calculateAutoShutdownReason(){
            return AutoShutdownReason.none;
        }
    }

    public static class AtomicTextureRegion extends Table{
        private final String onRegion, offRegion;
        private final Boolp getter;
        private final Boolc setter;

        public AtomicTextureRegion(String onRegion, String offRegion, Boolp getter, Boolc setter){
            this.onRegion = onRegion;
            this.offRegion = offRegion;
            this.getter = getter;
            this.setter = setter;
            updateView();
            clicked(() -> {
                setter.get(!getter.get());
                updateView();
            });
        }

        private void updateView(){
            clear();
            TextureRegion region = getter.get() ?
                Core.atlas.find(onRegion) :
                Core.atlas.find(offRegion);
            if(region != null && region != Core.atlas.find("error")){
                add(new Image(region)).size(32);
            }
        }
    }
}