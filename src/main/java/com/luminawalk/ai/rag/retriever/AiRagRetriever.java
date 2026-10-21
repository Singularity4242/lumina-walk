package com.luminawalk.ai.rag.retriever;

import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.dto.BlogVectorHitDTO;
import com.luminawalk.ai.dto.KnowledgeHitDTO;
import com.luminawalk.ai.dto.ShopToolDTO;
import com.luminawalk.ai.rag.AiMetadataConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRagRetriever {

    private final AiAgentProperties aiAgentProperties;

    @Qualifier("knowledgeVectorStore")
    private final VectorStore knowledgeVectorStore;

    @Qualifier("shopProfileVectorStore")
    private final VectorStore shopProfileVectorStore;

    @Qualifier("blogReviewVectorStore")
    private final VectorStore blogReviewVectorStore;

    public List<KnowledgeHitDTO> searchKnowledge(String query) {
        return searchKnowledge(query, null);
    }

    public List<KnowledgeHitDTO> searchKnowledge(String query, Filter.Expression filterExpression) {
        return knowledgeVectorStore.similaritySearch(buildSearchRequest(query, aiAgentProperties.getRag().getTopK(), filterExpression))
                .stream()
                .map(this::toKnowledgeHit)
                .toList();
    }

    public List<ShopToolDTO> searchShopProfiles(String query, Integer topK) {
        int finalTopK = topK == null || topK <= 0 ? aiAgentProperties.getRag().getTopK() : topK;
        return shopProfileVectorStore.similaritySearch(buildSearchRequest(query, finalTopK, null))
                .stream()
                .map(this::toShopHit)
                .toList();
    }

    public List<BlogVectorHitDTO> searchBlogReviews(String query, Integer topK) {
        int finalTopK = topK == null || topK <= 0 ? aiAgentProperties.getRag().getTopK() : topK;
        return blogReviewVectorStore.similaritySearch(buildSearchRequest(query, finalTopK, null))
                .stream()
                .map(this::toBlogHit)
                .toList();
    }

    public List<BlogVectorHitDTO> searchBlogReviewsByShop(Long shopId, String query, Integer topK) {
        Filter.Expression filterExpression = new FilterExpressionBuilder().eq(AiMetadataConstants.SHOP_ID, shopId).build();
        int finalTopK = topK == null || topK <= 0 ? aiAgentProperties.getRag().getTopK() : topK;
        return blogReviewVectorStore.similaritySearch(buildSearchRequest(query, finalTopK, filterExpression))
                .stream()
                .map(this::toBlogHit)
                .toList();
    }

    public boolean hasKnowledgeDocuments() {
        return !knowledgeVectorStore.similaritySearch(buildSearchRequest("黑马点评", 1, null)).isEmpty();
    }

    public boolean hasShopProfileDocuments() {
        return !shopProfileVectorStore.similaritySearch(buildSearchRequest("推荐店铺", 1, null)).isEmpty();
    }

    public boolean hasBlogReviewDocuments() {
        return !blogReviewVectorStore.similaritySearch(buildSearchRequest("探店评价", 1, null)).isEmpty();
    }

    private SearchRequest buildSearchRequest(String query, int topK, Filter.Expression filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(StringUtils.hasText(query) ? query : "黑马点评")
                .topK(topK)
                .similarityThreshold(aiAgentProperties.getRag().getSimilarityThreshold());
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }
        return builder.build();
    }

    private KnowledgeHitDTO toKnowledgeHit(Document document) {
        KnowledgeHitDTO dto = new KnowledgeHitDTO();
        dto.setId(document.getId());
        dto.setContent(document.getText());
        dto.setScore(document.getScore());
        dto.setSourceId(asString(document.getMetadata(), AiMetadataConstants.SOURCE_ID));
        return dto;
    }

    private ShopToolDTO toShopHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        ShopToolDTO dto = new ShopToolDTO();
        dto.setShopId(asLong(metadata, AiMetadataConstants.SHOP_ID));
        dto.setName(asString(metadata, AiMetadataConstants.SHOP_NAME));
        dto.setTypeId(asLong(metadata, AiMetadataConstants.TYPE_ID));
        dto.setArea(asString(metadata, AiMetadataConstants.AREA));
        dto.setAddress(asString(metadata, AiMetadataConstants.ADDRESS));
        dto.setAvgPrice(asLong(metadata, AiMetadataConstants.AVG_PRICE));
        dto.setRating(asDouble(metadata, AiMetadataConstants.RATING));
        dto.setComments(asInteger(metadata, AiMetadataConstants.COMMENTS));
        dto.setSold(asInteger(metadata, AiMetadataConstants.SOLD));
        dto.setOpenHours(asString(metadata, AiMetadataConstants.OPEN_HOURS));
        dto.setX(asDouble(metadata, AiMetadataConstants.X));
        dto.setY(asDouble(metadata, AiMetadataConstants.Y));
        dto.setSemanticScore(document.getScore());
        return dto;
    }

    private BlogVectorHitDTO toBlogHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        BlogVectorHitDTO dto = new BlogVectorHitDTO();
        dto.setBlogId(asLong(metadata, AiMetadataConstants.SOURCE_ID));
        dto.setShopId(asLong(metadata, AiMetadataConstants.SHOP_ID));
        dto.setUserId(asLong(metadata, AiMetadataConstants.USER_ID));
        dto.setTitle(asString(metadata, AiMetadataConstants.TITLE));
        dto.setContent(document.getText());
        dto.setScore(document.getScore());
        return dto;
    }

    private String asString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer asInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Double asDouble(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }
}
