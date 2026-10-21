package com.luminawalk.ai.component;

import com.luminawalk.ai.rag.indexer.AiVectorIndexService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor

public class KnowledgeBaseInitializer {

    private final AiVectorIndexService aiVectorIndexService;

    @PostConstruct
    public void init() {
        log.info(">>>>>> [AI-AGENT] checking vector indexes...");
        aiVectorIndexService.ensureInitialized();
    }
}
