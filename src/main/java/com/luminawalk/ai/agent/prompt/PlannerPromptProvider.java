package com.luminawalk.ai.agent.prompt;

import org.springframework.stereotype.Component;

@Component
public class PlannerPromptProvider {

    public String buildPrompt(String userPrompt, Double x, Double y, String shopTypeHints, String format) {
        return """
                你是黑马点评 AI Agent 的 planner，只负责识别场景和提取参数。
                你只能输出 JSON，不要输出额外解释。

                场景只能是以下三种之一：
                - CHAT
                - SHOP_RECOMMEND
                - SHOP_INSIGHT

                店铺分类映射参考：
                %s

                用户输入：%s
                用户位置：x=%s, y=%s

                提取要求：
                1. 如果是通用聊天、平台规则问答、系统说明，scene 设为 CHAT。
                2. 如果是推荐店铺、找店、附近、预算、口味、适合某场景消费，scene 设为 SHOP_RECOMMEND。
                3. 如果是分析某个具体店铺的评价、优缺点、菜品、环境、服务，scene 设为 SHOP_INSIGHT。
                4. 能提取出 shopId 就提取；提取不出来填 null。
                5. 能识别预算区间就填 budgetMin/budgetMax；没有就填 null，不能假设预算。
                6. 能识别分类时给出 typeId；识别不出填 null。
                7. 如果用户想找附近/离我近/周边，needNearby 设为 true，否则 false。
                8. 如果信息不足，informationGap 中用一句中文说明缺少什么；否则填空字符串。
                9. keyword 填最能代表搜索意图的简短关键词。

                输出格式要求：
                %s
                """.formatted(shopTypeHints, userPrompt, x, y, format);
    }
}
