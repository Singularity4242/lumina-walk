package com.luminawalk.ai.rag.indexer;

import com.luminawalk.ai.dto.IndexRebuildResponse;

public interface AiVectorIndexService {

    IndexRebuildResponse rebuildAll();

    IndexRebuildResponse rebuildShop(Long shopId);

    int rebuildKnowledge();

    void ensureInitialized();
}
