package com.server.server.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecommendationItemVO {

    private Long resourceId;

    private String title;

    private String coverUrl;

    private BigDecimal recScore;

    private String recReason;
}
