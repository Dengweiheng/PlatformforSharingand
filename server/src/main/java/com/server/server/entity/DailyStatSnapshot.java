package com.server.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("daily_stat_snapshot")
public class DailyStatSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Integer registerUserCount;

    private Integer totalResourceCount;

    private Integer dailyActiveUserCount;

    private BigDecimal recClickRate;
}
