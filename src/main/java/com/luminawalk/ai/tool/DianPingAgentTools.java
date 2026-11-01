package com.luminawalk.ai.tool;

import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.dto.BlogVectorHitDTO;
import com.luminawalk.ai.dto.KnowledgeHitDTO;
import com.luminawalk.ai.dto.ShopReviewSummaryDTO;
import com.luminawalk.ai.dto.ShopToolDTO;
import com.luminawalk.ai.rag.retriever.AiRagRetriever;
import com.luminawalk.dto.Result;
import com.luminawalk.entity.Blog;
import com.luminawalk.entity.Shop;
import com.luminawalk.service.IBlogService;
import com.luminawalk.service.IShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@RequiredArgsConstructor

public class DianPingAgentTools {

    private static final int REVIEW_SAMPLE_THRESHOLD = 5;

    private final IShopService shopService;

    private final IBlogService blogService;

    private final AiRagRetriever aiRagRetriever;

    private final AiAgentProperties aiAgentProperties;

    @Tool(description = "获取单个摄影地点、商业摄影空间或器材门店的现有资料；均价不是拍摄费用，缺失字段不能自行补齐")
    public ShopToolDTO getShopDetail(@ToolParam(description = "地点或器材门店ID，即 shopId") Long shopId) {
        Result result = shopService.queryById(shopId);
        if (result == null || result.getData() == null) {
            return null;
        }
        return toShopToolDTO((Shop) result.getData());
    }

    @Tool(description = "按分类和坐标搜索摄影地点或器材门店；无完整坐标时仅按分类查询，不能比较距离；此工具没有城市筛选参数")
    public List<ShopToolDTO> searchNearbyShops(
            @ToolParam(description = "平台实际分类ID，不要猜测分类与ID的对应关系") Integer typeId,
            @ToolParam(description = "经度") Double x,
            @ToolParam(description = "纬度") Double y,
            @ToolParam(description = "分页页码") Integer page) {
        int currentPage = page == null || page < 1 ? 1 : page;
        Result result = shopService.queryShopByType(typeId, currentPage, x, y);
        if (result == null || result.getData() == null) {
            return List.of();
        }
        List<Shop> shops = (List<Shop>) result.getData();
        return shops.stream().map(this::toShopToolDTO).toList();
    }

    @Tool(description = "按关键词匹配摄影地点或器材门店的名称、商圈和地址；不是独立的城市筛选，需核对候选地址是否符合用户范围")
    public List<ShopToolDTO> searchShopsByKeyword(@ToolParam(description = "搜索关键词") String keyword) {
        return shopService.lambdaQuery()
                .like(Shop::getName, keyword)
                .or(wrapper -> wrapper.like(Shop::getArea, keyword).or().like(Shop::getAddress, keyword))
                .last("limit 10")
                .list()
                .stream()
                .map(this::toShopToolDTO)
                .toList();
    }

    @Tool(description = "搜索平台知识库，返回语义相关片段；需核对是否适用于寻光漫游，旧平台规则不能直接沿用，通用知识不能替代具体券资料")
    public List<KnowledgeHitDTO> searchKnowledgeBase(@ToolParam(description = "用户查询") String query) {
        return aiRagRetriever.searchKnowledge(query);
    }

    @Tool(description = "获取指定地点关联的摄影笔记文本样本，优先返回点赞高的笔记；这是笔记而非消费评论，文本可能被截断，不代表全部笔记")
    public List<String> getShopReviewTexts(@ToolParam(description = "地点或器材门店ID，即 shopId") Long shopId) {
        List<Blog> blogs = blogService.lambdaQuery()
                .eq(Blog::getShopId, shopId)
                .orderByDesc(Blog::getLiked)
                .last("limit " + Math.max(aiAgentProperties.getInsight().getMaxReviewSamples(), 1))
                .list();
        if (CollectionUtils.isEmpty(blogs)) {
            return List.of();
        }
        return blogs.stream()
                .map(blog -> "【" + safe(blog.getTitle()) + "】" + truncate(safe(blog.getContent()), aiAgentProperties.getInsight().getMaxReviewCharsPerSample()))
                .toList();
    }

    @Tool(description = "获取指定地点关联摄影笔记的文本汇总和实际样本量；无笔记不代表已检查全部用户口碑，不得编造拍摄经验")
    public ShopReviewSummaryDTO getShopReviewSummary(@ToolParam(description = "地点或器材门店ID，即 shopId") Long shopId) {
        List<String> reviewTexts = getShopReviewTexts(shopId);
        ShopReviewSummaryDTO dto = new ShopReviewSummaryDTO();
        dto.setShopId(shopId);
        dto.setSampleCount(reviewTexts.size());
        dto.setReviewTexts(reviewTexts);
        dto.setCombinedText(truncate(String.join("\n---\n", reviewTexts), aiAgentProperties.getInsight().getMaxCombinedChars()));
        if (reviewTexts.isEmpty()) {
            dto.setSampleSizeNotice("当前未取得该地点关联的摄影笔记，不能据此总结拍摄经验或用户口碑。");
        }
        else if (reviewTexts.size() < REVIEW_SAMPLE_THRESHOLD) {
            dto.setSampleSizeNotice("当前仅取得 " + reviewTexts.size() + " 条关联摄影笔记样本，样本量较少，结论仅供参考。");
        }
        else {
            dto.setSampleSizeNotice("当前基于 " + reviewTexts.size() + " 条关联摄影笔记样本进行分析，不代表全部笔记。");
        }
        return dto;
    }

    @Tool(description = "搜索地点画像向量，返回语义相关的摄影地点或器材门店候选；语义相关不保证位于用户城市或距离最近")
    public List<ShopToolDTO> vectorSearchShopProfiles(
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "召回条数") Integer topK) {
        return aiRagRetriever.searchShopProfiles(query, topK);
    }

    @Tool(description = "搜索摄影笔记向量，返回语义相关笔记样本；可能来自不同地点或未关联地点，不能直接作为指定地点的证据")
    public List<BlogVectorHitDTO> vectorSearchBlogs(
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "召回条数") Integer topK) {
        return aiRagRetriever.searchBlogReviews(query, topK);
    }

    @Tool(description = "按指定地点ID搜索摄影笔记向量，返回该地点关联的语义相关笔记样本，用于有来源的地点经验分析")
    public List<BlogVectorHitDTO> vectorSearchBlogsByShop(
            @ToolParam(description = "地点或器材门店ID，即 shopId") Long shopId,
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "召回条数") Integer topK) {
        return aiRagRetriever.searchBlogReviewsByShop(shopId, query, topK);
    }


    private ShopToolDTO toShopToolDTO(Shop shop) {
        if (shop == null) {
            return null;
        }
        ShopToolDTO dto = new ShopToolDTO();
        dto.setShopId(shop.getId());
        dto.setName(shop.getName());
        dto.setTypeId(shop.getTypeId());
        dto.setArea(shop.getArea());
        dto.setAddress(shop.getAddress());
        dto.setAvgPrice(shop.getAvgPrice());
        dto.setRating(shop.getScore() == null ? null : shop.getScore() / 10.0);
        dto.setComments(shop.getComments());
        dto.setSold(shop.getSold());
        dto.setOpenHours(shop.getOpenHours());
        dto.setX(shop.getX());
        dto.setY(shop.getY());
        dto.setDistanceMeters(shop.getDistance());
        return dto;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength || maxLength <= 0) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
