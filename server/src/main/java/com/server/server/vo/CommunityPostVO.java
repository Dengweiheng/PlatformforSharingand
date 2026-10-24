package com.server.server.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommunityPostVO {

    private Long id;

    private Long userId;

    private String username;

    private String userRole;

    private String postType;

    private Long resourceId;

    private String title;

    private Integer score;

    private String content;

    private Integer commentCount;

    private LocalDateTime createdAt;
}
