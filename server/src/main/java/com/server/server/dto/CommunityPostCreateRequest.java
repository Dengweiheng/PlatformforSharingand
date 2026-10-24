package com.server.server.dto;

import lombok.Data;

@Data
public class CommunityPostCreateRequest {

    private Long userId;

    private String postType;

    private Long resourceId;

    private String title;

    private Integer score;

    private String content;
}
