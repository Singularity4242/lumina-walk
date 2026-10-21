package com.luminawalk.ai.agent;

import com.luminawalk.ai.agent.loop.ToolExecutionLog;
import com.luminawalk.ai.agent.scene.PlannerDecision;
import com.luminawalk.ai.dto.AgentRecommendItem;
import com.luminawalk.ai.dto.AgentRecommendResponse;
import com.luminawalk.ai.dto.ShopToolDTO;
import com.luminawalk.ai.tool.DianPingAgentTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShopRecommendationPreparer {

    private final DianPingAgentTools tools;

    public AgentRecommendResponse prepare(String query,
                                          Double x,
                                          Double y,
                                          PlannerDecision decision,
                                          Integer topK,
                                          List<ToolExecutionLog> toolLogs) {
        int finalTopK = topK == null || topK <= 0 ? 5 : topK;
        int recallTopK = Math.max(finalTopK * 4, 12);

        Map<Long, ShopToolDTO> candidateMap = new LinkedHashMap<>();

        List<ShopToolDTO> vectorCandidates = tools.vectorSearchShopProfiles(query, recallTopK);
        toolLogs.add(new ToolExecutionLog("vectorSearchShopProfiles", summarizeList(vectorCandidates)));
        mergeCandidates(candidateMap, vectorCandidates);

        String keyword = decision.getKeyword() == null || decision.getKeyword().isBlank() ? query : decision.getKeyword();
        if (candidateMap.size() < finalTopK) {
            List<ShopToolDTO> keywordCandidates = tools.searchShopsByKeyword(keyword);
            toolLogs.add(new ToolExecutionLog("searchShopsByKeyword", summarizeList(keywordCandidates)));
            mergeCandidates(candidateMap, keywordCandidates);
        }

        if (x != null && y != null && decision.getTypeId() != null) {
            List<ShopToolDTO> nearbyCandidates = tools.searchNearbyShops(decision.getTypeId(), x, y, 1);
            toolLogs.add(new ToolExecutionLog("searchNearbyShops", summarizeList(nearbyCandidates)));
            mergeCandidates(candidateMap, nearbyCandidates);
        }

        List<ShopToolDTO> ranked = rankCandidates(candidateMap.values(), decision, x, y);

        AgentRecommendResponse response = new AgentRecommendResponse();
        response.setQuery(query);
        if (x == null || y == null) {
            response.setMissingInfoNotice("未提供位置，本次推荐未纳入距离因素。");
        }
        else if (decision.getTypeId() == null) {
            response.setMissingInfoNotice("已提供位置，但未识别出明确分类，本次距离只作为辅助排序因素。");
        }

        ranked.stream().limit(finalTopK).map(shop -> toItem(shop, decision, x, y)).forEach(response.getItems()::add);
        if (response.getItems().isEmpty()) {
            response.setSummary("当前没有筛选出合适店铺，请补充预算、位置或更明确的口味需求。");
        }
        return response;
    }

    private List<ShopToolDTO> rankCandidates(Collection<ShopToolDTO> candidates, PlannerDecision decision, Double x, Double y) {
        List<ShopToolDTO> filtered = candidates.stream()
                .filter(shop -> shop.getShopId() != null)
                .filter(shop -> withinBudget(shop, decision))
                .map(shop -> enrichDistance(shop, x, y))
                .toList();
        if (filtered.isEmpty()) {
            return List.of();
        }

        double maxSemantic = filtered.stream().map(ShopToolDTO::getSemanticScore).filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(1.0);
        double maxComments = filtered.stream().map(ShopToolDTO::getComments).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(1);
        double maxSold = filtered.stream().map(ShopToolDTO::getSold).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(1);

        List<ShopToolDTO> scored = new ArrayList<>(filtered.size());
        for (ShopToolDTO shop : filtered) {
            double semanticScore = shop.getSemanticScore() == null ? 0.0 : shop.getSemanticScore() / maxSemantic;
            double ratingScore = shop.getRating() == null ? 0.0 : Math.min(shop.getRating() / 5.0, 1.0);
            double commentsScore = shop.getComments() == null ? 0.0 : Math.log1p(shop.getComments()) / Math.log1p(maxComments);
            double soldScore = shop.getSold() == null ? 0.0 : Math.log1p(shop.getSold()) / Math.log1p(maxSold);
            double budgetScore = budgetScore(shop, decision);
            double distanceScore = distanceScore(shop, x, y);
            double total = semanticScore * 0.30 + ratingScore * 0.22 + commentsScore * 0.16 + soldScore * 0.14 + budgetScore * 0.10 + distanceScore * 0.08;
            shop.setSemanticScore(total);
            scored.add(shop);
        }
        scored.sort(Comparator.comparing(ShopToolDTO::getSemanticScore, Comparator.nullsLast(Double::compareTo)).reversed());
        return scored;
    }

    private ShopToolDTO enrichDistance(ShopToolDTO shop, Double x, Double y) {
        if (x == null || y == null || shop.getX() == null || shop.getY() == null || shop.getDistanceMeters() != null) {
            return shop;
        }
        double distance = haversineMeters(y, x, shop.getY(), shop.getX());
        shop.setDistanceMeters(distance);
        return shop;
    }

    private boolean withinBudget(ShopToolDTO shop, PlannerDecision decision) {
        if (decision.getBudgetMin() == null && decision.getBudgetMax() == null) {
            return true;
        }
        if (shop.getAvgPrice() == null) {
            return true;
        }
        if (decision.getBudgetMin() != null && shop.getAvgPrice() < decision.getBudgetMin() * 0.5) {
            return false;
        }
        return decision.getBudgetMax() == null || shop.getAvgPrice() <= decision.getBudgetMax() * 1.6;
    }

    private double budgetScore(ShopToolDTO shop, PlannerDecision decision) {
        if (decision.getBudgetMin() == null && decision.getBudgetMax() == null) {
            return 0.5;
        }
        if (shop.getAvgPrice() == null) {
            return 0.2;
        }
        double center = ((decision.getBudgetMin() == null ? shop.getAvgPrice() : decision.getBudgetMin())
                + (decision.getBudgetMax() == null ? shop.getAvgPrice() : decision.getBudgetMax())) / 2.0;
        double gap = Math.abs(shop.getAvgPrice() - center);
        return Math.max(0.0, 1.0 - gap / Math.max(center, 1.0));
    }

    private double distanceScore(ShopToolDTO shop, Double x, Double y) {
        if (x == null || y == null) {
            return 0.5;
        }
        if (shop.getDistanceMeters() == null) {
            return 0.2;
        }
        return Math.max(0.0, 1.0 - Math.min(shop.getDistanceMeters(), 5000.0) / 5000.0);
    }

    private AgentRecommendItem toItem(ShopToolDTO shop, PlannerDecision decision, Double x, Double y) {
        AgentRecommendItem item = new AgentRecommendItem();
        item.setShopId(shop.getShopId());
        item.setShopName(shop.getName());
        item.setArea(shop.getArea());
        item.setAddress(shop.getAddress());
        item.setAvgPrice(shop.getAvgPrice());
        item.setRating(shop.getRating());
        item.setComments(shop.getComments());
        item.setSold(shop.getSold());
        item.setOpenHours(shop.getOpenHours());
        item.setDistanceMeters(shop.getDistanceMeters());
        item.setScore(shop.getSemanticScore());
        item.setReason(buildReason(shop, decision, x, y));
        return item;
    }

    private String buildReason(ShopToolDTO shop, PlannerDecision decision, Double x, Double y) {
        List<String> reasons = new ArrayList<>();
        reasons.add("语义匹配度较高");
        if (shop.getRating() != null) {
            reasons.add("评分 %.1f".formatted(shop.getRating()));
        }
        if (shop.getComments() != null) {
            reasons.add("评论量 %d".formatted(shop.getComments()));
        }
        if (shop.getSold() != null) {
            reasons.add("销量 %d".formatted(shop.getSold()));
        }
        if (shop.getAvgPrice() != null && decision.getBudgetMin() != null && decision.getBudgetMax() != null) {
            reasons.add("人均约 %d 元，接近预算区间".formatted(shop.getAvgPrice()));
        }
        if (x != null && y != null && shop.getDistanceMeters() != null) {
            reasons.add("距离约 %.0f 米".formatted(shop.getDistanceMeters()));
        }
        return String.join("；", reasons);
    }

    private void mergeCandidates(Map<Long, ShopToolDTO> candidateMap, List<ShopToolDTO> candidates) {
        for (ShopToolDTO candidate : candidates) {
            if (candidate == null || candidate.getShopId() == null) {
                continue;
            }
            ShopToolDTO existing = candidateMap.get(candidate.getShopId());
            if (existing == null) {
                candidateMap.put(candidate.getShopId(), candidate);
                continue;
            }
            if (existing.getSemanticScore() == null || (candidate.getSemanticScore() != null && candidate.getSemanticScore() > existing.getSemanticScore())) {
                existing.setSemanticScore(candidate.getSemanticScore());
            }
            if (existing.getDistanceMeters() == null || (candidate.getDistanceMeters() != null && candidate.getDistanceMeters() < existing.getDistanceMeters())) {
                existing.setDistanceMeters(candidate.getDistanceMeters());
            }
            if (existing.getRating() == null) {
                existing.setRating(candidate.getRating());
            }
            if (existing.getComments() == null) {
                existing.setComments(candidate.getComments());
            }
            if (existing.getSold() == null) {
                existing.setSold(candidate.getSold());
            }
            if (existing.getAvgPrice() == null) {
                existing.setAvgPrice(candidate.getAvgPrice());
            }
            if (existing.getOpenHours() == null) {
                existing.setOpenHours(candidate.getOpenHours());
            }
            if (existing.getArea() == null) {
                existing.setArea(candidate.getArea());
            }
            if (existing.getAddress() == null) {
                existing.setAddress(candidate.getAddress());
            }
            if (existing.getX() == null) {
                existing.setX(candidate.getX());
            }
            if (existing.getY() == null) {
                existing.setY(candidate.getY());
            }
        }
    }

    private Object summarizeList(List<ShopToolDTO> shops) {
        return shops.stream()
                .limit(5)
                .map(shop -> "%s|%s|rating=%s|avgPrice=%s|distance=%s".formatted(
                        shop.getShopId(),
                        shop.getName(),
                        shop.getRating(),
                        shop.getAvgPrice(),
                        shop.getDistanceMeters()))
                .toList();
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
