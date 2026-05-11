package com.EyeOfHarmonyBuffer.example;

import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Chapters;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.builder.RewardBuilders;
import com.hfstudio.bqapi.api.builder.TaskBuilders;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;

import java.util.UUID;

import static com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.MODID;

public final class ExampleQuestRegistration {

    static final UUID START = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    public static void registerAll() {
        QuestDefinition Arknights_Section_One_1 = Quests.quest("eyeofharmonybuffer.Arknights_Section_One_1")
            .task(TaskBuilders.retrieval("collect_logs")
                .item(MODID + ":YuanShiKuang", 64, 0)
                .consume(false)
                .build())
            /*.reward(RewardBuilders.item("starter_reward")
                .item("minecraft:iron_ingot", 4, 0)
                .build())*/
            .main(true)
            .icon(MODID + ":YuanShiKuang", 64, 0)
            .build();

        QuestDefinition Arknights_Section_One_2 = Quests.quest("eyeofharmonybuffer.Arknights_Section_One_2")
            .prerequisite(Arknights_Section_One_1)
            .task(TaskBuilders.retrieval("collect_logs")
                .item("gregtech:gt.blockmachines", 1, 23008)
                .consume(false)
                .build())
            .main(true)
            .icon("gregtech:gt.blockmachines", 1, 23008)
            .build();

        ChapterDefinition Section_One = Chapters.chapter("eyeofharmonybuffer.Arknights_Section_One")
            .uuid(START)
            .icon(MODID + ":YuanShi", 1, 0)
            .quest(new QuestPlacementDefinition(Arknights_Section_One_1, 0, 0, 20, 20))
            .quest(new QuestPlacementDefinition(Arknights_Section_One_2, 40,0, 20, 20))
            .build();

        BQApi.register(Section_One);

        //System.out.println("Chapter name key = " + BQApiLangKeys.chapterName(START));
    }

    private ExampleQuestRegistration() {}
}
