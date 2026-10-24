package com.server.server.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AdminDashboardVO {

    private LocalDate statDate;

    private Integer registerUserCount;

    private Integer totalResourceCount;

    private Integer dailyActiveUserCount;

    private BigDecimal recClickRate;
}
