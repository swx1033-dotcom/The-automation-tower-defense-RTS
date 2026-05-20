package mindustry.type;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.graphics.*;
import mindustry.graphics.MultiPacker.*;
import mindustry.logic.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class Item extends UnlockableContent implements Senseable{
    public enum Quality{
        normal(1f, 1f, Color.valueOf("8f8f8f"), "普通"),
        fine(1.2f, 1.15f, Color.valueOf("5ec4ff"), "精良"),
        rare(1.5f, 1.35f, Color.valueOf("c68cff"), "稀有");

        public static final Quality[] all = values();

        public final float drillSpeedMultiplier;
        public final float craftYieldMultiplier;
        public final Color accentColor;
        public final String displayName;

        Quality(float drillSpeedMultiplier, float craftYieldMultiplier, Color accentColor, String displayName){
            this.drillSpeedMultiplier = drillSpeedMultiplier;
            this.craftYieldMultiplier = craftYieldMultiplier;
            this.accentColor = accentColor;
            this.displayName = displayName;
        }

        public @Nullable Quality next(){
            int next = ordinal() + 1;
            return next >= all.length ? null : all[next];
        }

        public static Quality max(Quality first, Quality second){
            return first.ordinal() >= second.ordinal() ? first : second;
        }
    }

    public Color color;

    /** how explosive this item is. */
    public float explosiveness = 0f;
    /** flammability above 0.3 makes this eligible for item burners. */
    public float flammability = 0f;
    /** how radioactive this item is. */
    public float radioactivity;
    /** how electrically potent this item is. */
    public float charge = 0f;
    /** drill hardness of the item */
    public int hardness = 0;
    /**
     * base material cost of this item, used for calculating place times
     * 1 cost = 1 tick added to build time
     */
    public float cost = 1f;
    /** When this item is present in the build cost, a block's <b>default</b> health is multiplied by 1 + scaling, where 'scaling' is summed together for all item requirement types. */
    public float healthScaling = 0f;
    /** if true, this item is of the lowest priority to drills. */
    public boolean lowPriority;

    /** If >0, this item is animated. */
    public int frames = 0;
    /** Number of generated transition frames between each frame */
    public int transitionFrames = 0;
    /** Ticks in-between animation frames. */
    public float frameTime = 5f;
    /** If true, this material is used by buildings. If false, this material will be incinerated in certain cores. */
    public boolean buildable = true;
    public boolean hidden = false;

    public Quality quality = Quality.normal;
    protected Item qualityBase = this;
    protected Item[] qualityItems;

    public Item(String name, Color color){
        super(name);
        this.color = color;
    }

    protected Item(String name, Color color, Quality quality, Item qualityBase){
        this(name, color);
        this.quality = quality;
        this.qualityBase = qualityBase;
    }

    public Item(String name){
        this(name, new Color(Color.black));
    }

    public boolean isBaseItem(){
        return quality == Quality.normal && qualityBase == this;
    }

    public Item baseItem(){
        return qualityBase;
    }

    public boolean acceptsEquivalentQualities(){
        return isBaseItem();
    }

    public float drillSpeedMultiplier(){
        return quality.drillSpeedMultiplier;
    }

    public float craftYieldMultiplier(){
        return quality.craftYieldMultiplier;
    }

    public Item[] qualityFamily(){
        return qualityBase.qualityItems == null ? new Item[]{qualityBase} : qualityBase.qualityItems;
    }

    public Item withQuality(Quality quality){
        if(quality == this.quality && !acceptsEquivalentQualities()) return this;
        if(qualityBase.qualityItems == null) return qualityBase;
        return qualityBase.qualityItems[quality.ordinal()];
    }

    public Item upgradedQualityItem(){
        Quality next = quality.next();
        return next == null ? this : withQuality(next);
    }

    public Item craftedWithQuality(Quality quality){
        return acceptsEquivalentQualities() ? withQuality(quality) : this;
    }

    public void ensureQualityVariants(){
        if(!isBaseItem() || qualityItems != null) return;

        qualityItems = new Item[Quality.all.length];
        qualityItems[Quality.normal.ordinal()] = this;

        for(Quality quality : Quality.all){
            if(quality == Quality.normal) continue;

            Item variant = new Item(name + "-" + quality.name(), new Color(color).lerp(quality.accentColor, 0.22f), quality, this);
            variant.localizedName = localizedName + " " + quality.displayName;
            variant.description = description;
            variant.details = details;
            variant.credit = credit;
            variant.explosiveness = explosiveness;
            variant.flammability = flammability;
            variant.radioactivity = radioactivity;
            variant.charge = charge;
            variant.hardness = hardness;
            variant.cost = cost;
            variant.healthScaling = healthScaling;
            variant.lowPriority = lowPriority;
            variant.frames = frames;
            variant.transitionFrames = transitionFrames;
            variant.frameTime = frameTime;
            variant.buildable = buildable;
            variant.hidden = hidden;
            variant.alwaysUnlocked = alwaysUnlocked;
            variant.inlineDescription = inlineDescription;
            variant.hideDetails = hideDetails;
            variant.hideDatabase = hideDatabase;
            variant.generateIcons = generateIcons;
            variant.selectionSize = selectionSize;
            variant.allDatabaseTabs = allDatabaseTabs;
            variant.databaseCategory = databaseCategory;
            variant.databaseTag = databaseTag;
            variant.shownPlanets.addAll(shownPlanets);
            variant.databaseTabs.addAll(databaseTabs);
            variant.fullOverride = variant.name;
            variant.qualityItems = qualityItems;
            qualityItems[quality.ordinal()] = variant;
        }
    }

    @Override
    public boolean isOnPlanet(Planet planet){
        return super.isOnPlanet(planet) && !hidden;
    }

    @Override
    public boolean isHidden(){
        return hidden;
    }

    @Override
    public void loadIcon(){
        if(!isBaseItem()){
            fullIcon = Core.atlas.find(fullOverride == null ? name : fullOverride, qualityBase.fullIcon);
            uiIcon = fullIcon;
            return;
        }

        super.loadIcon();

        if(frames > 0){
            TextureRegion[] regions = new TextureRegion[frames * (transitionFrames + 1)];

            if(transitionFrames <= 0){
                for(int i = 1; i <= frames; i++){
                    regions[i - 1] = Core.atlas.find(name + i);
                }
            }else{
                for(int i = 0; i < frames; i++){
                    regions[i * (transitionFrames + 1)] = Core.atlas.find(name + (i + 1));
                    for(int j = 1; j <= transitionFrames; j++){
                        int index = i * (transitionFrames + 1) + j;
                        regions[index] = Core.atlas.find(name + "-t" + index);
                    }
                }
            }

            fullIcon = new TextureRegion(fullIcon);
            uiIcon = new TextureRegion(uiIcon);

            Events.run(Trigger.update, () -> {
                int frame = (int)(Time.globalTime / frameTime) % regions.length;

                fullIcon.set(regions[frame]);
                uiIcon.set(regions[frame]);
            });
        }
    }

    @Override
    public void setStats(){
        stats.addPercent(Stat.explosiveness, explosiveness);
        stats.addPercent(Stat.flammability, flammability);
        stats.addPercent(Stat.radioactivity, radioactivity);
        stats.addPercent(Stat.charge, charge);
    }

    @Override
    public String toString(){
        return localizedName;
    }

    @Override
    public ContentType getContentType(){
        return ContentType.item;
    }

    @Override
    public void createIcons(MultiPacker packer){
        if(!isBaseItem()){
            String regionName = qualityBase.frames > 0 ? qualityBase.name + "1" : qualityBase.name;
            Pixmap image = Core.atlas.getPixmap(regionName).crop();
            int overlay = Tmp.c1.set(quality.accentColor).a(0.32f).rgba8888();

            for(int x = 0; x < image.width; x++){
                for(int y = 0; y < image.height; y++){
                    if(image.getA(x, y) > 0){
                        image.setRaw(x, y, Pixmap.blend(overlay, image.getRaw(x, y)));
                    }
                }
            }

            packer.add(PageType.main, name, image);
            image.dispose();
            return;
        }

        super.createIcons(packer);

        if(frames > 0 && transitionFrames > 0){
            var pixmaps = new PixmapRegion[frames];

            for(int i = 0; i < frames; i++){
                pixmaps[i] = Core.atlas.getPixmap(name + (i + 1));
            }

            for(int i = 0; i < frames; i++){
                for(int j = 1; j <= transitionFrames; j++){
                    float f = (float)j / (transitionFrames + 1);
                    int index = i * (transitionFrames + 1) + j;

                    Pixmap res = Pixmaps.blend(pixmaps[i], pixmaps[(i + 1) % frames], f);
                    packer.add(PageType.main, name + "-t" + index, res);
                    res.dispose();
                }
            }
        }
    }

    @Override
    public double sense(LAccess sensor){
        if(sensor == LAccess.color) return color.toDoubleBits();
        if(sensor == LAccess.id) return getLogicId();
        return Float.NaN;
    }

    @Override
    public Object senseObject(LAccess sensor){
        if(sensor == LAccess.name) return name;
        return noSensed;
    }

    /** Allocates a new array containing all items that generate ores. */
    public static Seq<Item> getAllOres(){
        return content.blocks().select(b -> b instanceof OreBlock).map(b -> b.itemDrop);
    }
}
