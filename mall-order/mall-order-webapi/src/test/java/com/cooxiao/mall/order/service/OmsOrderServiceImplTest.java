package com.cooxiao.mall.order.service;

import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.order.mapper.OmsOrderMapper;
import com.cooxiao.mall.order.mapper.OmsPaymentRecordMapper;
import com.cooxiao.mall.order.payment.PaymentResult;
import com.cooxiao.mall.order.payment.PaymentStrategy;
import com.cooxiao.mall.order.payment.PaymentStrategyFactory;
import com.cooxiao.mall.order.service.impl.OmsOrderServiceImpl;
import com.cooxiao.mall.pojo.order.dto.PayOrderDTO;
import com.cooxiao.mall.pojo.order.enums.PaymentTypeEnum;
import com.cooxiao.mall.pojo.order.model.OmsOrder;
import com.cooxiao.mall.pojo.order.vo.PayOrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OmsOrderServiceImplTest {

    @Mock private OmsOrderMapper omsOrderMapper;
    @Mock private OmsPaymentRecordMapper paymentRecordMapper;
    @Mock private PaymentStrategyFactory paymentStrategyFactory;
    @Mock private PaymentStrategy paymentStrategy;
    @Mock private RabbitTemplate rabbitTemplate;
    @InjectMocks private OmsOrderServiceImpl omsOrderService;

    @BeforeEach
    void setUp() {
        CsmallAuthenticationInfo auth = new CsmallAuthenticationInfo();
        auth.setId(1L);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(null, auth, null);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private PayOrderDTO payReq(Long id, Integer paymentType) {
        PayOrderDTO dto = new PayOrderDTO();
        dto.setId(id);
        dto.setPaymentType(paymentType);
        return dto;
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(omsOrderMapper.selectOrderById(999L)).thenReturn(null);

        CoolSharkServiceException ex = assertThrows(CoolSharkServiceException.class, () ->
                omsOrderService.payOrder(payReq(999L, 2)));

        assertEquals(ResponseCode.BAD_REQUEST.getValue(), ex.getResponseCode().getValue());
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    void shouldThrowWhenOrderAlreadyPaid() {
        OmsOrder paid = new OmsOrder();
        paid.setId(100L);
        paid.setState(3);
        paid.setUserId(1L);
        when(omsOrderMapper.selectOrderById(100L)).thenReturn(paid);

        CoolSharkServiceException ex = assertThrows(CoolSharkServiceException.class, () ->
                omsOrderService.payOrder(payReq(100L, 2)));

        assertEquals(ResponseCode.BAD_REQUEST.getValue(), ex.getResponseCode().getValue());
        assertTrue(ex.getMessage().contains("不支持支付"));
    }

    @Test
    void shouldThrowWhenOrderBelongsToOtherUser() {
        OmsOrder otherUsersOrder = new OmsOrder();
        otherUsersOrder.setId(200L);
        otherUsersOrder.setState(0);
        otherUsersOrder.setUserId(999L);
        when(omsOrderMapper.selectOrderById(200L)).thenReturn(otherUsersOrder);

        CoolSharkServiceException ex = assertThrows(CoolSharkServiceException.class, () ->
                omsOrderService.payOrder(payReq(200L, 2)));

        assertEquals(ResponseCode.FORBIDDEN.getValue(), ex.getResponseCode().getValue());
        assertTrue(ex.getMessage().contains("无权操作"));
    }

    @Test
    void shouldPaySuccessfullyWithSimulatedMode() {
        OmsOrder unpaid = new OmsOrder();
        unpaid.setId(300L);
        unpaid.setSn("SN-300");
        unpaid.setState(0);
        unpaid.setUserId(1L);
        unpaid.setAmountOfActualPay(new BigDecimal("99.00"));
        when(omsOrderMapper.selectOrderById(300L)).thenReturn(unpaid);
        when(paymentStrategyFactory.getStrategy(anyInt())).thenReturn(paymentStrategy);
        when(paymentStrategy.initiatePayment(unpaid))
                .thenReturn(PaymentResult.builder().success(true).tradeNo("SIM123").build());

        PayOrderVO result = omsOrderService.payOrder(payReq(300L, 2));

        assertNotNull(result);
        assertEquals(300L, result.getId());
        assertEquals("SN-300", result.getSn());
        assertEquals(3, result.getState());
        verify(paymentRecordMapper).insertRecord(any());
        verify(omsOrderMapper).updateOrderById(any());
    }

    @Test
    void shouldFailWhenPaymentStrategyFails() {
        OmsOrder unpaid = new OmsOrder();
        unpaid.setId(400L);
        unpaid.setState(0);
        unpaid.setUserId(1L);
        unpaid.setAmountOfActualPay(new BigDecimal("99.00"));
        when(omsOrderMapper.selectOrderById(400L)).thenReturn(unpaid);
        when(paymentStrategyFactory.getStrategy(anyInt())).thenReturn(paymentStrategy);
        when(paymentStrategy.initiatePayment(unpaid))
                .thenReturn(PaymentResult.builder().success(false).errorMsg("支付宝不可用").build());

        CoolSharkServiceException ex = assertThrows(CoolSharkServiceException.class, () ->
                omsOrderService.payOrder(payReq(400L, 2)));

        assertTrue(ex.getMessage().contains("支付发起失败"));
        verify(paymentRecordMapper, never()).insertRecord(any());
    }
}
