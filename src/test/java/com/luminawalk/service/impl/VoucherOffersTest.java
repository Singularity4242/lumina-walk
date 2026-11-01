package com.luminawalk.service.impl;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.Voucher;
import com.luminawalk.mapper.VoucherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoucherOffersTest {
    private final VoucherServiceImpl service = new VoucherServiceImpl();
    private final VoucherMapper mapper = mock(VoucherMapper.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void pageUsesLookaheadWithoutLeakingExtraRecord() {
        List<Voucher> rows = IntStream.range(0, 13)
                .mapToObj(i -> new Voucher().setId((long) i).setShopName("演示门店")).toList();
        when(mapper.queryOffers(12L, 13)).thenReturn(rows);
        Result result = service.queryOffers(2);
        assertTrue(result.getSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(true, data.get("hasMore"));
        assertEquals(rows.subList(0, 12), data.get("records"));
    }

    @Test
    void lastPageAndEmptyPageHaveNoNextPage() {
        when(mapper.queryOffers(0L, 13)).thenReturn(List.of(new Voucher().setId(1L)));
        assertEquals(false, ((Map<?, ?>) service.queryOffers(1).getData()).get("hasMore"));
        when(mapper.queryOffers(12L, 13)).thenReturn(List.of());
        assertEquals(Map.of("records", List.of(), "hasMore", false), service.queryOffers(2).getData());
    }

    @Test
    void invalidPageDoesNotQueryDatabaseAndLargePageDoesNotOverflow() {
        assertFalse(service.queryOffers(0).getSuccess());
        assertFalse(service.queryOffers(-1).getSuccess());
        assertFalse(service.queryOffers(null).getSuccess());
        verifyNoInteractions(mapper);
        long offset = ((long) Integer.MAX_VALUE - 1) * 12;
        when(mapper.queryOffers(offset, 13)).thenReturn(List.of());
        assertTrue(service.queryOffers(Integer.MAX_VALUE).getSuccess());
        verify(mapper).queryOffers(offset, 13);
    }
}
