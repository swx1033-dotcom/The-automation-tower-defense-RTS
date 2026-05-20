package mindustry.world.blocks.production;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;
import mindustry.world.modules.*;

import static mindustry.Vars.*;

/**
 * 品质合成方块
 * 可以：
 * 1. 将3个普通品质物品合成为1个精良品质物品
 * 2. 将3个精良品质物品合成为1个稀有品质物品
 * 3. 使用高品质物品作为原料时，获得额外产出
 */
public class QualityCrafter extends Block{
    public float craftTime = 120f;
    public Effect craftEffect = Fx.smeltsmoke;
    public Color heatColor = Color.valueOf("ff7744");

    public QualityCrafter(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        group = BlockGroup.crafters;
        ambientSound = Sounds.smelter;
        ambientSoundVolume = 0.06f;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.productionTime, craftTime / 60f, StatUnit.seconds);
    }

    public class QualityCrafterBuild extends Building{
        public float progress;
        public float totalProgress;
        public float warmup;
        // 我们保留标准的 items 用于兼容，但主要使用 qualityItems
        public QualityModule qualityItems = new QualityModule();

        @Override
        public void created(){
            super.created();
        }

        @Override
        public void updateTile(){
            // 尝试将普通物品转换为品质物品（用于兼容旧的输入方式）
            if(items != null && !items.empty()){
                for(Item item : content.items()){
                    int amount = items.get(item);
                    if(amount > 0){
                        qualityItems.add(item, Item.Quality.common, amount);
                        items.remove(item, amount);
                    }
                }
            }

            // 检查是否可以进行品质升级合成
            boolean canCraft = false;
            Item craftItem = null;
            Item.Quality fromQuality = null;
            Item.Quality toQuality = null;

            for(Item item : content.items()){
                // 检查普通 -> 精良
                if(qualityItems.has(item, Item.Quality.common, 3)){
                    canCraft = true;
                    craftItem = item;
                    fromQuality = Item.Quality.common;
                    toQuality = Item.Quality.fine;
                    break;
                }
                // 检查精良 -> 稀有
                else if(qualityItems.has(item, Item.Quality.fine, 3)){
                    canCraft = true;
                    craftItem = item;
                    fromQuality = Item.Quality.fine;
                    toQuality = Item.Quality.rare;
                    break;
                }
            }

            if(canCraft && efficiency > 0){
                progress += getProgressIncrease(craftTime);
                warmup = Mathf.approachDelta(warmup, 1f, 0.015f);
                totalProgress += warmup * Time.delta;

                if(progress >= 1f){
                    // 执行合成
                    qualityItems.remove(craftItem, fromQuality, 3);
                    qualityItems.add(craftItem, toQuality, 1);
                    craftEffect.at(x, y, heatColor);
                    progress %= 1f;
                }
            }else{
                warmup = Mathf.approachDelta(warmup, 0f, 0.015f);
            }

            // 倾倒物品
            if(timer(timerDump, dumpTime / timeScale)){
                dumpItems();
            }
        }

        /**
         * 倾倒物品（从最低品质开始）
         */
        protected void dumpItems(){
            for(Item item : content.items()){
                for(Item.Quality quality : Item.Quality.values()){
                    if(qualityItems.has(item, quality)){
                        if(dump(item)){
                            qualityItems.remove(item, quality, 1);
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public void handleItem(Building source, Item item){
            // 默认添加为普通品质
            qualityItems.add(item, Item.Quality.common, 1);
        }

        @Override
        public int getMaximumAccepted(Item item){
            return itemCapacity;
        }

        @Override
        public boolean shouldAmbientSound(){
            return efficiency > 0;
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            Drawf.heat(heatRegion, x, y, warmup);
        }

        @Override
        public float progress(){
            return Mathf.clamp(progress);
        }

        @Override
        public float warmup(){
            return warmup;
        }

        @Override
        public float totalProgress(){
            return totalProgress;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(progress);
            write.f(warmup);
            qualityItems.write(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            progress = read.f();
            warmup = read.f();
            qualityItems.read(read, false);
        }
    }
}
