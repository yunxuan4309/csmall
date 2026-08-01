package com.cooxiao.mall.sso.controller;

import com.cooxiao.mall.common.restful.JsonResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@Api(tags = "管理员仪表盘")
@Slf4j
public class DashboardController {

    @Autowired
    @Qualifier("db1JdbcTemplate")
    private JdbcTemplate adminJdbc;

    @Autowired
    @Qualifier("db2JdbcTemplate")
    private JdbcTemplate userJdbc;

    @Autowired
    @Qualifier("orderJdbcTemplate")
    private JdbcTemplate orderJdbc;

    @GetMapping
    @ApiOperation("获取仪表盘概览数据")
    @PreAuthorize("isAuthenticated()")
    public JsonResult<DashboardVO> dashboard() {
        DashboardVO vo = new DashboardVO();

        // 今日新增用户
        Integer todayUsers = userJdbc.queryForObject(
                "SELECT COUNT(*) FROM ums_user WHERE DATE(gmt_create) = CURDATE()", Integer.class);
        vo.setTodayNewUsers(todayUsers != null ? todayUsers : 0);

        // 累计用户数
        Integer totalUsers = userJdbc.queryForObject(
                "SELECT COUNT(*) FROM ums_user", Integer.class);
        vo.setTotalUsers(totalUsers != null ? totalUsers : 0);

        // 今日订单数 + 金额
        try {
            Map<String, Object> orderStats = orderJdbc.queryForMap(
                    "SELECT COUNT(*) AS cnt, COALESCE(SUM(amount_of_actual_pay), 0) AS revenue " +
                    "FROM oms_order WHERE DATE(gmt_pay) = CURDATE() AND state = 3");
            vo.setTodayOrders(((Number) orderStats.get("cnt")).intValue());
            vo.setTodayRevenue((BigDecimal) orderStats.get("revenue"));
        } catch (Exception e) {
            vo.setTodayOrders(0);
            vo.setTodayRevenue(BigDecimal.ZERO);
        }

        // 待支付订单
        try {
            Integer pending = orderJdbc.queryForObject(
                    "SELECT COUNT(*) FROM oms_order WHERE state = 0", Integer.class);
            vo.setPendingOrders(pending != null ? pending : 0);
        } catch (Exception e) {
            vo.setPendingOrders(0);
        }

        // 近 7 天订单趋势
        vo.setWeeklyTrend(getWeeklyTrend());

        return JsonResult.ok(vo);
    }

    private List<TrendItem> getWeeklyTrend() {
        List<TrendItem> trend = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = orderJdbc.queryForList(
                    "SELECT DATE(gmt_create) AS dt, COUNT(*) AS cnt, " +
                    "COALESCE(SUM(amount_of_actual_pay), 0) AS revenue " +
                    "FROM oms_order WHERE gmt_pay >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) AND state = 3 " +
                    "GROUP BY dt ORDER BY dt");
            for (Map<String, Object> row : rows) {
                TrendItem item = new TrendItem();
                item.setDate(row.get("dt").toString());
                item.setOrders(((Number) row.get("cnt")).intValue());
                item.setRevenue((BigDecimal) row.get("revenue"));
                trend.add(item);
            }
        } catch (Exception e) {
            log.warn("查询订单趋势失败", e);
        }
        return trend;
    }

    @Data
    public static class DashboardVO {
        private int todayNewUsers;
        private int totalUsers;
        private int todayOrders;
        private BigDecimal todayRevenue;
        private int pendingOrders;
        private List<TrendItem> weeklyTrend;
    }

    @Data
    public static class TrendItem {
        private String date;
        private int orders;
        private BigDecimal revenue;
    }
}
