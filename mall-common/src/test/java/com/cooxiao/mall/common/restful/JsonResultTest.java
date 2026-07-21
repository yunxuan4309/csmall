package com.cooxiao.mall.common.restful;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonResult 单元测试 — 不依赖 Spring 容器，直接验证响应封装逻辑。
 */
class JsonResultTest {

    @Test
    void okShouldReturn200WithData() {
        JsonResult<String> result = JsonResult.ok("hello");
        assertEquals(200, result.getState());
        assertEquals("hello", result.getData());
    }

    @Test
    void okShouldReturn200WithNullData() {
        JsonResult<Void> result = JsonResult.ok();
        assertEquals(200, result.getState());
        assertNull(result.getData());
    }

    @Test
    void failedShouldReturnCorrectCode() {
        JsonResult<Void> result = JsonResult.failed(ResponseCode.BAD_REQUEST, "参数错误");
        assertEquals(400, result.getState());
        assertEquals("参数错误", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void failedShouldReturn500ForServerError() {
        JsonResult<Void> result = JsonResult.failed(ResponseCode.INTERNAL_SERVER_ERROR, "系统异常");
        assertEquals(500, result.getState());
    }

    @Test
    void setStateAndMessageShouldOverride() {
        JsonResult<String> result = JsonResult.ok("data");
        result.setState(302);
        result.setMessage("redirected");
        assertEquals(302, result.getState());
        assertEquals("redirected", result.getMessage());
        assertEquals("data", result.getData());
    }
}
