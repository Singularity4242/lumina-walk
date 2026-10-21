package com.luminawalk.ai.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "ai.agent")
public class AiAgentProperties {

    private Memory memory = new Memory();

    private Rag rag = new Rag();

    private Model model = new Model();

    private Insight insight = new Insight();

    private MultiAgent multiAgent = new MultiAgent();

    @Data
    public static class Memory {
        private int windowSize = 8;
    }

    @Data
    public static class Rag {
        private int topK = 5;
        private double similarityThreshold = 0.65;
        private double knowledgeSimilarityThreshold = 0.35;
        private Collections collections = new Collections();
    }

    @Data
    public static class Model {
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 90;
        private int writeTimeoutSeconds = 90;
        private int callTimeoutSeconds = 120;
    }

    @Data
    public static class Insight {
        private int maxReviewSamples = 8;
        private int maxReviewCharsPerSample = 280;
        private int maxCombinedChars = 2200;
        private int maxVectorHits = 4;
        private int maxVectorHitChars = 180;
    }

    @Data
    public static class MultiAgent {
        private int maxSteps = 20;
    }

    @Data
    public static class Collections {
        private String knowledge = "knowledge_vector";
        private String shopProfile = "shop_profile_vector";
        private String blogReview = "blog_review_vector";
    }
}
