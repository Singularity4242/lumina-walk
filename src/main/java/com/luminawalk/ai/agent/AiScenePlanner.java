package com.luminawalk.ai.agent;

import com.luminawalk.ai.agent.prompt.PlannerPromptProvider;
import com.luminawalk.ai.agent.scene.AiAgentScene;
import com.luminawalk.ai.agent.scene.PlannerDecision;
import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.entity.ShopType;
import com.luminawalk.service.IShopTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor

public class AiScenePlanner {

    private static final Pattern SHOP_ID_PATTERN = Pattern.compile("(?:shopId|店铺|商户|店)\\D{0,6}(\\d{1,8})");
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(?:人均|预算|价位)\\D{0,4}(\\d{2,4})");
    private static final Pattern AROUND_PRICE_PATTERN = Pattern.compile("(\\d{2,4})\\s*(?:左右|上下)");

    private final ChatClient aiChatClient;

    private final PlannerPromptProvider plannerPromptProvider;

    private final IShopTypeService shopTypeService;

    private final AiAgentProperties aiAgentProperties;

    public PlannerDecision plan(String userPrompt, Double x, Double y) {
        List<ShopType> shopTypes = shopTypeService.list();
        if (!aiAgentProperties.getPlanner().isLlmEnabled()) {
            return normalize(heuristicDecision(userPrompt), userPrompt, x, y, shopTypes);
        }

        BeanOutputConverter<PlannerDecision> converter = new BeanOutputConverter<>(PlannerDecision.class);
        String shopTypeHints = shopTypes.stream()
                .map(type -> type.getId() + ":" + type.getName())
                .collect(Collectors.joining(", "));
        PlannerDecision decision;
        try {
            decision = aiChatClient.prompt()
                    .system("你是黑马点评 AI Agent 的场景规划器，只返回 JSON。")
                    .user(plannerPromptProvider.buildPrompt(userPrompt, x, y, shopTypeHints, converter.getFormat()))
                    .call()
                    .entity(converter);
        }
        catch (Exception ex) {
            decision = heuristicDecision(userPrompt);
        }
        return normalize(decision, userPrompt, x, y, shopTypes);
    }

    public PlannerDecision planWithoutModel(String userPrompt, Double x, Double y, AiAgentScene sceneOverride) {
        List<ShopType> shopTypes = shopTypeService.list();
        PlannerDecision decision = heuristicDecision(userPrompt);
        if (sceneOverride != null) {
            decision.setScene(sceneOverride.name());
        }
        return normalize(decision, userPrompt, x, y, shopTypes);
    }

    private PlannerDecision normalize(PlannerDecision decision, String userPrompt, Double x, Double y, List<ShopType> shopTypes) {
        PlannerDecision normalized = decision == null ? new PlannerDecision() : decision;
        if (!StringUtils.hasText(normalized.getKeyword())) {
            normalized.setKeyword(userPrompt);
        }
        if (!StringUtils.hasText(normalized.getScene())) {
            normalized.setScene(heuristicDecision(userPrompt).getScene());
        }
        if (normalized.getNeedNearby() == null) {
            normalized.setNeedNearby(hasNearbyIntent(userPrompt));
        }
        if (normalized.getShopId() == null) {
            normalized.setShopId(extractShopId(userPrompt));
        }
        if (normalized.getTypeId() == null) {
            normalized.setTypeId(extractTypeId(userPrompt, shopTypes));
        }
        if (normalized.getBudgetMin() == null && normalized.getBudgetMax() == null) {
            Integer budget = extractBudget(userPrompt);
            if (budget != null) {
                normalized.setBudgetMin(Math.max(1, budget - 20));
                normalized.setBudgetMax(budget + 20);
            }
        }
        if (normalized.getInformationGap() == null) {
            normalized.setInformationGap("");
        }
        AiAgentScene scene = AiAgentScene.from(normalized.getScene());
        if (scene == AiAgentScene.SHOP_RECOMMEND && (x == null || y == null)) {
            normalized.setInformationGap(joinGap(normalized.getInformationGap(), "缺少用户位置，无法纳入距离排序"));
        }
        if (scene == AiAgentScene.SHOP_INSIGHT && normalized.getShopId() == null && !StringUtils.hasText(normalized.getKeyword())) {
            normalized.setInformationGap(joinGap(normalized.getInformationGap(), "缺少店铺ID或明确店铺名称"));
        }
        return normalized;
    }

    private PlannerDecision heuristicDecision(String userPrompt) {
        PlannerDecision decision = new PlannerDecision();
        decision.setKeyword(userPrompt);
        if (containsAny(userPrompt, "推荐", "附近", "约会", "火锅", "人均", "评价不错", "找一家")) {
            decision.setScene(AiAgentScene.SHOP_RECOMMEND.name());
        }
        else if (containsAny(userPrompt, "分析", "优缺点", "招牌菜", "环境", "服务", "评价怎么样", "评论怎么样")) {
            decision.setScene(AiAgentScene.SHOP_INSIGHT.name());
        }
        else {
            decision.setScene(AiAgentScene.CHAT.name());
        }
        decision.setNeedNearby(hasNearbyIntent(userPrompt));
        decision.setReason("fallback heuristic");
        decision.setInformationGap("");
        return decision;
    }

    private boolean hasNearbyIntent(String userPrompt) {
        return containsAny(userPrompt, "附近", "离我近", "周边", "最近");
    }

    private Long extractShopId(String userPrompt) {
        Matcher matcher = SHOP_ID_PATTERN.matcher(userPrompt);
        if (matcher.find()) {
            return Long.valueOf(matcher.group(1));
        }
        return null;
    }

    private Integer extractTypeId(String userPrompt, List<ShopType> shopTypes) {
        for (ShopType shopType : shopTypes) {
            if (userPrompt.contains(shopType.getName())) {
                return shopType.getId().intValue();
            }
        }
        if (containsAny(userPrompt, "火锅", "茶餐厅", "西餐", "烤肉", "寿司")) {
            return 1;
        }
        if (containsAny(userPrompt, "ktv", "KTV", "唱歌")) {
            return 2;
        }
        return null;
    }

    private Integer extractBudget(String userPrompt) {
        Matcher matcher = BUDGET_PATTERN.matcher(userPrompt);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        matcher = AROUND_PRICE_PATTERN.matcher(userPrompt);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String joinGap(String existing, String next) {
        if (!StringUtils.hasText(existing)) {
            return next;
        }
        if (existing.contains(next)) {
            return existing;
        }
        return existing + "；" + next;
    }
}
