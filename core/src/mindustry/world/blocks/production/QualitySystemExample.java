package mindustry.world.blocks.production;

import mindustry.type.*;

/**
 * 品质系统使用示例
 *
 * 本文件展示了如何使用新增的品质系统
 */
public class QualitySystemExample{

    /**
     * 示例1: 基本的品质枚举使用
     */
    public static void basicQualityUsage(){
        // 获取品质
        Item.Quality common = Item.Quality.common;
        Item.Quality fine = Item.Quality.fine;
        Item.Quality rare = Item.Quality.rare;

        // 品质的属性
        System.out.println("普通品质 - 挖掘速度加成: " + common.miningSpeedBonus + ", 合成加成: " + common.craftingBonus);
        System.out.println("精良品质 - 挖掘速度加成: " + fine.miningSpeedBonus + ", 合成加成: " + fine.craftingBonus);
        System.out.println("稀有品质 - 挖掘速度加成: " + rare.miningSpeedBonus + ", 合成加成: " + rare.craftingBonus);

        // 获取下一级品质
        Item.Quality nextFromCommon = common.next(); // 精良
        Item.Quality nextFromFine = fine.next(); // 稀有
        Item.Quality nextFromRare = rare.next(); // 稀有（已是最高级）

        // 获取上一级品质
        Item.Quality prevFromRare = rare.prev(); // 精良
        Item.Quality prevFromCommon = common.prev(); // 普通（已是最低级）
    }

    /**
     * 示例2: 使用带品质的 ItemStack
     */
    public static void itemStackWithQuality(Item copper){
        // 创建不同品质的 ItemStack
        ItemStack commonCopper = new ItemStack(copper, 10, Item.Quality.common);
        ItemStack fineCopper = new ItemStack(copper, 5, Item.Quality.fine);
        ItemStack rareCopper = new ItemStack(copper, 2, Item.Quality.rare);

        // 复制
        ItemStack copy = commonCopper.copy();

        // 比较（会同时比较物品类型和品质）
        boolean equals = commonCopper.equals(fineCopper); // false

        System.out.println(commonCopper); // 输出: copper[common]: 10
        System.out.println(fineCopper); // 输出: copper[fine]: 5
    }

    /**
     * 示例3: 使用 QualityModule 存储品质物品
     */
    public static void qualityModuleUsage(Item copper, Item lead){
        // 创建 QualityModule
        mindustry.world.modules.QualityModule module = new mindustry.world.modules.QualityModule();

        // 添加物品
        module.add(copper, Item.Quality.common, 10);
        module.add(copper, Item.Quality.fine, 5);
        module.add(lead, Item.Quality.rare, 2);

        // 获取特定品质的物品数量
        int commonCopper = module.get(copper, Item.Quality.common); // 10
        int fineCopper = module.get(copper, Item.Quality.fine); // 5
        int totalCopper = module.getTotal(copper); // 15

        // 检查是否有特定品质的物品
        boolean hasRareLead = module.has(lead, Item.Quality.rare); // true

        // 移除物品
        module.remove(copper, Item.Quality.common, 3); // 移除3个普通铜
        module.remove(copper, 5); // 移除5个铜（从最低品质开始）

        // 遍历所有品质物品
        module.each((item, quality, amount) -> {
            System.out.println(item.name + " [" + quality.name() + "]: " + amount);
        });

        // 清空
        module.clear();
    }

    /**
     * 示例4: 品质转换计算
     */
    public static void qualityConversion(){
        // 3个普通 = 1个精良
        int commonToFine = 3;

        // 3个精良 = 1个稀有
        int fineToRare = 3;

        // 9个普通 = 1个稀有
        int commonToRare = 9;

        System.out.println("品质转换比例:");
        System.out.println("普通 → 精良: " + commonToFine + ":1");
        System.out.println("精良 → 稀有: " + fineToRare + ":1");
        System.out.println("普通 → 稀有: " + commonToRare + ":1");
    }

    /**
     * 示例5: 计算效率加成
     */
    public static void calculateBonuses(Item.Quality quality){
        float baseDrillTime = 300f; // 基础挖掘时间
        float baseCraftOutput = 1; // 基础合成产出

        // 计算挖掘时间（品质越高，挖掘越快）
        float adjustedDrillTime = baseDrillTime / quality.miningSpeedBonus;
        System.out.println(quality.name() + " 品质挖掘时间: " + adjustedDrillTime + " 帧");

        // 计算合成产出（品质越高，产出越多）
        float adjustedCraftOutput = baseCraftOutput * quality.craftingBonus;
        System.out.println(quality.name() + " 品质合成产出: " + adjustedCraftOutput + " 倍");
    }

    /**
     * 完整工作流程示例
     */
    public static void fullWorkflowExample(Item copper){
        System.out.println("=== 品质系统完整工作流程 ===");

        // 1. 使用 QualityDrill 挖掘品质矿石
        System.out.println("\n1. 挖掘阶段:");
        System.out.println("- QualityDrill 会随机产出不同品质的铜矿");
        System.out.println("- 普通: 88%, 精良: 10%, 稀有: 2%");
        System.out.println("- 挖掘速度受稀有品质加成: " + Item.Quality.rare.miningSpeedBonus + " 倍");

        // 2. 收集品质矿石
        System.out.println("\n2. 收集阶段:");
        System.out.println("- 矿石被存储在 QualityModule 中");
        System.out.println("- 不同品质的物品分开计数");

        // 3. 使用 QualityCrafter 升级品质
        System.out.println("\n3. 品质升级阶段:");
        System.out.println("- 3个普通铜矿 → 1个精良铜矿");
        System.out.println("- 3个精良铜矿 → 1个稀有铜矿");

        // 4. 使用高品质物品进行合成
        System.out.println("\n4. 高级合成阶段:");
        System.out.println("- 使用稀有品质铜矿作为原料");
        System.out.println("- 合成产出: " + Item.Quality.rare.craftingBonus + " 倍");

        System.out.println("\n=== 工作流程结束 ===");
    }
}
