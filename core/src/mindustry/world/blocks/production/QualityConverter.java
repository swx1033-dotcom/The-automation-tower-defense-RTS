package mindustry.world.blocks.production;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class QualityConverter extends GenericCrafter{
    public int conversionCount = 3;
    public Effect convertEffect = Fx.smeltslag;

    public QualityConverter(String name){
        super(name);
        craftTime = 120f;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.input, conversionCount + " " + Core.bundle.get("quality.normal") + " -> 1 " + Core.bundle.get("quality.refined"));
    }

    @Override
    public void init(){
        super.init();
        update = true;
        solid = true;
        hasItems = true;
    }

    public class QualityConverterBuild extends GenericCrafterBuild{
        public Item sourceItem;
        public Item targetItem;

        @Override
        public void updateTile(){
            if(efficiency > 0){
                if(sourceItem == null || !items.has(sourceItem, conversionCount)){
                    findConvertibleItem();
                }

                if(sourceItem != null && items.has(sourceItem, conversionCount)){
                    progress += getProgressIncrease(craftTime);
                    warmup = Mathf.approachDelta(warmup, warmupTarget(), warmupSpeed);

                    if(wasVisible && Mathf.chanceDelta(updateEffectChance)){
                        updateEffect.at(x + Mathf.range(size * updateEffectSpread), y + Mathf.range(size * updateEffectSpread));
                    }
                }else{
                    warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
                }

                totalProgress += warmup * Time.delta;

                if(progress >= 1f){
                    convert();
                }
            }else{
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
            }

            dumpOutputs();
        }

        protected void findConvertibleItem(){
            sourceItem = null;
            targetItem = null;

            for(int i = 0; i < items.items.length; i++){
                if(items.items[i] >= conversionCount){
                    Item item = content.item(i);
                    if(item != null && item.quality != Item.Quality.rare){
                        sourceItem = item;
                        targetItem = getHigherQualityItem(item);
                        break;
                    }
                }
            }
        }

        protected Item getHigherQualityItem(Item item){
            Item.Quality nextQuality = item.quality.next();
            if(nextQuality == item.quality) return null;

            for(int i = 0; i < content.items().size; i++){
                Item other = content.item(i);
                if(other != null && other.name.equals(item.name + "-" + nextQuality.name)){
                    return other;
                }
            }
            return null;
        }

        protected void convert(){
            if(sourceItem == null || targetItem == null) return;
            if(!items.has(sourceItem, conversionCount)) return;

            items.remove(sourceItem, conversionCount);

            if(items.get(targetItem) + 1 <= itemCapacity){
                items.add(targetItem, 1);
                produced(targetItem);
            }

            if(wasVisible){
                convertEffect.at(x, y);
            }

            progress %= 1f;
            sourceItem = null;
            targetItem = null;
        }

        @Override
        public boolean shouldConsume(){
            if(sourceItem == null) findConvertibleItem();
            return sourceItem != null && items.has(sourceItem, conversionCount) && 
                   items.get(targetItem) + 1 <= itemCapacity && enabled;
        }

        @Override
        public void drawSelect(){
            if(sourceItem != null){
                drawItemSelection(sourceItem);
            }
        }
    }
}
