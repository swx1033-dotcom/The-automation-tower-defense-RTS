package mindustry.world.blocks.production;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;
import mindustry.world.modules.*;

import static mindustry.Vars.*;

/**
 * 品质挖掘方块
 * 特性：
 * 1. 挖掘时有概率获得更高品质的物品
 * 2. 高品质物品的挖掘速度更快
 * 3. 存储物品时保持品质信息
 */
public class QualityDrill extends Block{
    public float hardnessDrillMultiplier = 50f;
    public int tier;
    public float drillTime = 300f;
    public float liquidBoostIntensity = 1.6f;
    public float warmupSpeed = 0.015f;
    public @Nullable Item blockedItem;
    public @Nullable Seq<Item> blockedItems;

    protected final ObjectIntMap<Item> oreCount = new ObjectIntMap<>();
    protected final Seq<Item> itemArray = new Seq<>();
    protected @Nullable Item returnItem;
    protected int returnCount;

    public boolean drawMineItem = true;
    public Effect drillEffect = Fx.mine;
    public float drillEffectRnd = -1f;
    public float drillEffectChance = 0.02f;
    public float rotateSpeed = 2f;
    public Effect updateEffect = Fx.pulverizeSmall;
    public float updateEffectChance = 0.02f;
    public ObjectFloatMap<Item> drillMultipliers = new ObjectFloatMap<>();
    public boolean drawRim = false;
    public boolean drawSpinSprite = true;
    public Color heatColor = Color.valueOf("ff5512");
    public @Load("@-rim") TextureRegion rimRegion;
    public @Load("@-rotator") TextureRegion rotatorRegion;
    public @Load("@-top") TextureRegion topRegion;
    public @Load(value = "@-item", fallback = "drill-item-@size") TextureRegion itemRegion;

    // 品质相关参数
    /** 获得精良品质物品的概率 (0-1) */
    public float fineChance = 0.1f;
    /** 获得稀有品质物品的概率 (0-1) */
    public float rareChance = 0.02f;

    public QualityDrill(String name){
        super(name);
        update = true;
        solid = true;
        group = BlockGroup.drills;
        hasLiquids = true;
        hasItems = true;
        ambientSound = Sounds.loopDrill;
        ambientSoundVolume = 0.019f;
        envEnabled |= Env.space;
        flags = EnumSet.of(BlockFlag.drill);
    }

    @Override
    public void init(){
        super.init();
        if(blockedItems == null && blockedItem != null){
            blockedItems = Seq.with(blockedItem);
        }
        if(drillEffectRnd < 0) drillEffectRnd = size;
    }

    @Override
    public void drawPlanConfig(BuildPlan plan, Eachable<BuildPlan> list){
        if(!plan.worldContext) return;
        Tile tile = plan.tile();
        if(tile == null) return;

        countOre(tile);
        if(returnItem == null || !drawMineItem) return;

        Draw.tint(returnItem.color);
        Draw.rect(itemRegion, plan.drawx(), plan.drawy());
        Draw.color();
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("drillspeed", (QualityDrillBuild e) ->
            new Bar(() -> Core.bundle.format("bar.drillspeed", Strings.fixed(e.lastDrillSpeed * 60 * e.timeScale(), 2)), () -> Pal.ammo, () -> e.warmup));
    }

    public Item getDrop(Tile tile){
        return tile.drop();
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        if(isMultiblock()){
            for(Tile other : tile.getLinkedTilesAs(this, tempTiles)){
                if(canMine(other)){
                    return true;
                }
            }
            return false;
        }else{
            return canMine(tile);
        }
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        Tile tile = world.tile(x, y);
        if(tile == null) return;

        countOre(tile);

        if(returnItem != null){
            float width = drawPlaceText(Core.bundle.formatFloat("bar.drillspeed", 60f / getDrillTime(returnItem) * returnCount, 2), x, y, valid);
            float dx = x * tilesize + offset - width/2f - 4f, dy = y * tilesize + offset + size * tilesize / 2f + 5f, s = iconSmall / 4f;
            Draw.mixcol(Color.darkGray, 1);
            Draw.rect(returnItem.fullIcon, dx, dy - 1, s, s);
            Draw.reset();
            Draw.rect(returnItem.fullIcon, dx, dy, s, s);

            if(drawMineItem){
                Draw.color(returnItem.color);
                Draw.rect(itemRegion, tile.worldx() + offset, tile.worldy() + offset);
                Draw.color();
            }
        }else{
            Tile to = tile.getLinkedTilesAs(this, tempTiles).find(t -> t.drop() != null && (t.drop().hardness > tier || (blockedItems != null && blockedItems.contains(t.drop()))));
            Item item = to == null ? null : to.drop();
            if(item != null){
                drawPlaceText(Core.bundle.get("bar.drilltierreq"), x, y, valid);
            }
        }
    }

    public float getDrillTime(Item item){
        return (drillTime + hardnessDrillMultiplier * item.hardness) / drillMultipliers.get(item, 1f);
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.drillTier, StatValues.drillables(drillTime, hardnessDrillMultiplier, size * size, drillMultipliers, b -> b instanceof Floor f && !f.wallOre && f.itemDrop != null &&
            f.itemDrop.hardness <= tier && (blockedItems == null || !blockedItems.contains(f.itemDrop)) && (indexer.isBlockPresent(f) || state.isMenu())));

        stats.add(Stat.drillSpeed, 60f / drillTime * size * size, StatUnit.itemsSecond);

        // 添加品质概率信息
        stats.add(Stat.output, "品质概率: 普通 " + Strings.fixed((1f - fineChance - rareChance) * 100f, 1) + "%, 精良 " + Strings.fixed(fineChance * 100f, 1) + "%, 稀有 " + Strings.fixed(rareChance * 100f, 1) + "%");

        if(liquidBoostIntensity != 1 && findConsumer(f -> f instanceof ConsumeLiquidBase && f.booster) instanceof ConsumeLiquidBase consBase){
            stats.remove(Stat.booster);
            stats.add(Stat.booster,
                StatValues.speedBoosters("{0}" + StatUnit.timesSpeed.localized(),
                consBase.amount,
                liquidBoostIntensity * liquidBoostIntensity, false, consBase::consumes)
            );
        }
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region, rotatorRegion, topRegion};
    }

    protected void countOre(Tile tile){
        returnItem = null;
        returnCount = 0;

        oreCount.clear();
        itemArray.clear();

        for(Tile other : tile.getLinkedTilesAs(this, tempTiles)){
            if(canMine(other)){
                oreCount.increment(getDrop(other), 0, 1);
            }
        }

        for(Item item : oreCount.keys()){
            itemArray.add(item);
        }

        itemArray.sort((item1, item2) -> {
            int type = Boolean.compare(!item1.lowPriority, !item2.lowPriority);
            if(type != 0) return type;
            int amounts = Integer.compare(oreCount.get(item1, 0), oreCount.get(item2, 0));
            if(amounts != 0) return amounts;
            return Integer.compare(item1.id, item2.id);
        });

        if(itemArray.size == 0){
            return;
        }

        returnItem = itemArray.peek();
        returnCount = oreCount.get(itemArray.peek(), 0);
    }

    public boolean canMine(Tile tile){
        if(tile == null || tile.block().isStatic()) return false;
        Item drops = tile.drop();
        return drops != null && drops.hardness <= tier && (blockedItems == null || !blockedItems.contains(drops));
    }

    public class QualityDrillBuild extends Building{
        public float progress;
        public float warmup;
        public float timeDrilled;
        public float lastDrillSpeed;
        public int dominantItems;
        public Item dominantItem;

        // 品质物品存储
        public QualityModule qualityItems = new QualityModule();

        @Override
        public void created(){
            super.created();
        }

        @Override
        public boolean shouldConsume(){
            return qualityItems.total() < itemCapacity && enabled && dominantItem != null;
        }

        @Override
        public boolean shouldAmbientSound(){
            return efficiency > 0.01f && qualityItems.total() < itemCapacity;
        }

        @Override
        public float ambientVolume(){
            return efficiency * (size * size) / 4f;
        }

        @Override
        public void drawSelect(){
            drawItemSelection(dominantItem);
        }

        @Override
        public void pickedUp(){
            dominantItem = null;
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            countOre(tile);
            dominantItem = returnItem;
            dominantItems = returnCount;
        }

        @Override
        public Object senseObject(LAccess sensor){
            if(sensor == LAccess.firstItem) return dominantItem;
            return super.senseObject(sensor);
        }

        @Override
        public void updateTile(){
            // 将普通物品转换为品质物品（兼容旧的输入方式）
            if(items != null && !items.empty()){
                for(Item item : content.items()){
                    int amount = items.get(item);
                    if(amount > 0){
                        qualityItems.add(item, Item.Quality.common, amount);
                        items.remove(item, amount);
                    }
                }
            }

            if(timer(timerDump, dumpTime / timeScale)){
                dumpQualityItems();
            }

            if(dominantItem == null){
                return;
            }

            timeDrilled += warmup * delta();

            // 根据可能获得的最佳品质计算挖掘速度加成
            float bestQualityBonus = Item.Quality.rare.miningSpeedBonus; // 默认最高品质的加成

            float delay = getDrillTime(dominantItem) / bestQualityBonus; // 应用品质加成到挖掘时间

            if(qualityItems.total() < itemCapacity && dominantItems > 0 && efficiency > 0){
                float speed = Mathf.lerp(1f, liquidBoostIntensity, optionalEfficiency) * efficiency;

                lastDrillSpeed = (speed * dominantItems * warmup) / delay;
                warmup = Mathf.approachDelta(warmup, speed, warmupSpeed);
                progress += delta() * dominantItems * speed * warmup;

                if(Mathf.chanceDelta(updateEffectChance * warmup))
                    updateEffect.at(x + Mathf.range(size * 2f), y + Mathf.range(size * 2f));
            }else{
                lastDrillSpeed = 0f;
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
                return;
            }

            if(dominantItems > 0 && progress >= delay && qualityItems.total() < itemCapacity){
                int amount = (int)(progress / delay);
                for(int i = 0; i < amount; i++){
                    offloadQualityItem();
                }

                progress %= delay;

                if(wasVisible && Mathf.chanceDelta(drillEffectChance * warmup)) drillEffect.at(x + Mathf.range(drillEffectRnd), y + Mathf.range(drillEffectRnd), dominantItem.color);
            }
        }

        /**
         * 卸载一个带品质的物品
         */
        protected void offloadQualityItem(){
            // 随机决定品质
            Item.Quality quality;
            float rand = Mathf.random();
            if(rand < rareChance){
                quality = Item.Quality.rare;
            }else if(rand < rareChance + fineChance){
                quality = Item.Quality.fine;
            }else{
                quality = Item.Quality.common;
            }

            qualityItems.add(dominantItem, quality, 1);
        }

        /**
         * 倾倒品质物品（从最低品质开始）
         */
        protected void dumpQualityItems(){
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
        public float progress(){
            return dominantItem == null ? 0f : Mathf.clamp(progress / getDrillTime(dominantItem));
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.progress && dominantItem != null) return progress;
            return super.sense(sensor);
        }

        @Override
        public void drawCracks(){}

        public void drawDefaultCracks(){
            super.drawCracks();
        }

        @Override
        public void draw(){
            float s = 0.3f;
            float ts = 0.6f;

            Draw.rect(region, x, y);
            Draw.z(Layer.blockCracks);
            drawDefaultCracks();

            Draw.z(Layer.blockAfterCracks);
            if(drawRim){
                Draw.color(heatColor);
                Draw.alpha(warmup * ts * (1f - s + Mathf.absin(Time.time, 3f, s)));
                Draw.blend(Blending.additive);
                Draw.rect(rimRegion, x, y);
                Draw.blend();
                Draw.color();
            }

            if(drawSpinSprite){
                Drawf.spinSprite(rotatorRegion, x, y, timeDrilled * rotateSpeed);
            }else{
                Draw.rect(rotatorRegion, x, y, timeDrilled * rotateSpeed);
            }

            Draw.rect(topRegion, x, y);

            if(dominantItem != null && drawMineItem){
                Draw.color(dominantItem.color);
                Draw.rect(itemRegion, x, y);
                Draw.color();
            }
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
            qualityItems.write(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                progress = read.f();
                warmup = read.f();
                qualityItems.read(read, false);
            }
        }
    }
}
