package mindustry.world.modules;

import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.type.*;

import static mindustry.Vars.*;

/**
 * 用于存储带品质的物品模块
 * 每个物品类型和品质组合都有独立的计数
 */
public class QualityModule extends BlockModule{
    /** 存储带品质物品的映射：item.id -> (quality.ordinal() -> count) */
    private ObjectIntMap<Item.Quality>[] items;
    /** 总物品数量 */
    protected int total;
    /** 用于轮询取出物品的旋转索引 */
    protected int takeRotation;

    @SuppressWarnings("unchecked")
    public QualityModule(){
        int itemCount = content.items().size;
        items = new ObjectIntMap[itemCount];
        for(int i = 0; i < itemCount; i++){
            items[i] = new ObjectIntMap<>();
        }
    }

    public QualityModule copy(){
        QualityModule out = new QualityModule();
        out.set(this);
        return out;
    }

    public void set(QualityModule other){
        total = other.total;
        takeRotation = other.takeRotation;
        for(int i = 0; i < items.length; i++){
            items[i].clear();
            for(var entry : other.items[i]){
                items[i].put(entry.key, entry.value);
            }
        }
    }

    /**
     * 获取特定品质的物品数量
     */
    public int get(Item item, Item.Quality quality){
        return items[item.id].get(quality, 0);
    }

    /**
     * 获取物品的总数量（所有品质之和）
     */
    public int getTotal(Item item){
        int sum = 0;
        for(var entry : items[item.id]){
            sum += entry.value;
        }
        return sum;
    }

    /**
     * 检查是否有特定品质的物品
     */
    public boolean has(Item item, Item.Quality quality){
        return get(item, quality) > 0;
    }

    /**
     * 检查是否有特定物品（任意品质）
     */
    public boolean has(Item item){
        return getTotal(item) > 0;
    }

    /**
     * 检查是否有足够数量的特定品质物品
     */
    public boolean has(Item item, Item.Quality quality, int amount){
        return get(item, quality) >= amount;
    }

    /**
     * 设置特定品质物品的数量
     */
    public void set(Item item, Item.Quality quality, int amount){
        int old = items[item.id].get(quality, 0);
        total += (amount - old);
        items[item.id].put(quality, amount);
    }

    /**
     * 添加特定品质的物品
     */
    public void add(Item item, Item.Quality quality, int amount){
        items[item.id].increment(quality, 0, amount);
        total += amount;
    }

    /**
     * 添加物品（默认普通品质）
     */
    public void add(Item item, int amount){
        add(item, Item.Quality.common, amount);
    }

    /**
     * 移除特定品质的物品
     */
    public void remove(Item item, Item.Quality quality, int amount){
        int current = items[item.id].get(quality, 0);
        int toRemove = Math.min(amount, current);
        if(toRemove > 0){
            items[item.id].put(quality, current - toRemove);
            total -= toRemove;
        }
    }

    /**
     * 移除物品（从最低品质开始）
     */
    public void remove(Item item, int amount){
        for(Item.Quality quality : Item.Quality.values()){
            if(amount <= 0) break;
            int current = items[item.id].get(quality, 0);
            int toRemove = Math.min(amount, current);
            if(toRemove > 0){
                items[item.id].put(quality, current - toRemove);
                total -= toRemove;
                amount -= toRemove;
            }
        }
    }

    /**
     * 尝试取一个物品（轮询方式）
     */
    public @Nullable ItemStack take(){
        int startRotation = takeRotation;
        do{
            int index = takeRotation % items.length;
            takeRotation++;

            for(Item.Quality quality : Item.Quality.values()){
                if(items[index].get(quality, 0) > 0){
                    items[index].increment(quality, 0, -1);
                    total--;
                    return new ItemStack(content.item(index), 1, quality);
                }
            }
        }while(takeRotation % items.length != startRotation);

        return null;
    }

    /**
     * 获取模块总物品数量
     */
    public int total(){
        return total;
    }

    /**
     * 检查模块是否为空
     */
    public boolean empty(){
        return total == 0;
    }

    /**
     * 清空模块
     */
    public void clear(){
        for(var map : items){
            map.clear();
        }
        total = 0;
    }

    /**
     * 遍历所有带品质的物品
     */
    public void each(QualityItemConsumer cons){
        for(int i = 0; i < items.length; i++){
            Item item = content.item(i);
            for(var entry : items[i]){
                if(entry.value > 0){
                    cons.accept(item, entry.key, entry.value);
                }
            }
        }
    }

    @Override
    public void write(Writes write){
        // 统计有物品的条目数
        int count = 0;
        for(int i = 0; i < items.length; i++){
            for(var entry : items[i]){
                if(entry.value > 0) count++;
            }
        }

        write.s(count);

        // 写入所有有物品的条目
        for(int i = 0; i < items.length; i++){
            Item item = content.item(i);
            for(var entry : items[i]){
                if(entry.value > 0){
                    write.s(i); // 物品ID
                    write.b(entry.key.ordinal()); // 品质索引
                    write.i(entry.value); // 数量
                }
            }
        }
    }

    @Override
    public void read(Reads read, boolean legacy){
        clear();

        int count = legacy ? read.ub() : read.s();

        for(int j = 0; j < count; j++){
            int itemid = legacy ? read.ub() : read.s();
            int qualityOrd = read.ub();
            int amount = read.i();

            Item item = content.item(itemid);
            if(item != null && qualityOrd >= 0 && qualityOrd < Item.Quality.values().length){
                Item.Quality quality = Item.Quality.values()[qualityOrd];
                add(item, quality, amount);
            }
        }
    }

    @Override
    public String toString(){
        var res = new StringBuilder();
        res.append("QualityModule{");
        boolean first = true;
        for(int i = 0; i < items.length; i++){
            Item item = content.item(i);
            for(var entry : items[i]){
                if(entry.value > 0){
                    if(!first) res.append(",");
                    res.append(item.name).append("[").append(entry.key.name()).append("]:").append(entry.value);
                    first = false;
                }
            }
        }
        res.append("}");
        return res.toString();
    }

    /**
     * 带品质的物品消费者接口
     */
    public interface QualityItemConsumer{
        void accept(Item item, Item.Quality quality, int amount);
    }
}
