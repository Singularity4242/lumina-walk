package com.luminawalk.ai.tool;

import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.dto.ShopReviewSummaryDTO;
import com.luminawalk.ai.rag.retriever.AiRagRetriever;
import com.luminawalk.service.IBlogService;
import com.luminawalk.service.IShopService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class DianPingAgentToolsTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 5})
    void shouldDescribeActualAssociatedPhotographySamples(int count) {
        DianPingAgentTools tools = spy(new DianPingAgentTools(mock(IShopService.class),
                mock(IBlogService.class), mock(AiRagRetriever.class), new AiAgentProperties()));
        doReturn(Collections.nCopies(count, "【逆光人像】拍摄经验")).when(tools).getShopReviewTexts(42L);

        ShopReviewSummaryDTO result = tools.getShopReviewSummary(42L);
        assertEquals(42L, result.getShopId());
        assertEquals(count, result.getSampleCount());
        assertEquals(count, result.getReviewTexts().size());
        verify(tools).getShopReviewTexts(42L);
        assertTrue(result.getSampleSizeNotice().contains("摄影笔记"));
        assertFalse(result.getSampleSizeNotice().contains("探店"));
        if (count == 0) {
            assertTrue(result.getSampleSizeNotice().contains("不能据此总结"));
            assertEquals("", result.getCombinedText());
        }
        else {
            assertTrue(result.getSampleSizeNotice().contains(count + " 条"));
            assertTrue(result.getCombinedText().contains("逆光人像"));
        }
    }
}
