package mindustry.world.blocks.production;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;

public class ProductionBlock extends Block {

    public ProductionBlock(String name) {
        super(name);
        update = true;
        solid = true;
        hasPower = true;
        group = BlockGroup.production;
        configurable = true;
        
        config(Boolean.class, (ProductionBlockBuild tile, Boolean auto) -> tile.autoMode = auto);
    }

    public class ProductionBlockBuild extends Building {
        public boolean autoMode = true;
        public boolean isStopped = false;
        public int stopReason = 0; // 0: none, 1: output full

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.button(b -> {
                b.label(() -> autoMode ? "Auto" : "Manual");
            }, Styles.defaultb, () -> {
                configure(!autoMode);
                deselect();
            }).size(80, 40);
        }

        @Override
        public void updateTile() {
            super.updateTile();
            if (autoMode) {
                if (isProductFull()) {
                    isStopped = true;
                    stopReason = 1;
                } else if (isMaterialShort()) {
                    isStopped = false;
                    stopReason = 0;
                } else {
                    isStopped = false;
                    stopReason = 0;
                }
            } else {
                isStopped = false;
                stopReason = 0;
            }
        }

        @Override
        public boolean shouldConsume() {
            if (isStopped) return false;
            return super.shouldConsume();
        }

        @Override
        public void draw() {
            super.draw();
            if (isStopped && stopReason == 1) {
                Draw.color(Color.red);
                Draw.rect(Icon.cancel.getRegion(), x, y);
                Draw.color();
            }
        }

        public boolean isProductFull() {
            return false;
        }

        public boolean isMaterialShort() {
            return !productionValid();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(autoMode);
            write.bool(isStopped);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            autoMode = read.bool();
            isStopped = read.bool();
        }
    }
}