package com.server.server.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommunityCommentVO {

    private Long id;

    private Long postId;

    private Long userId;

    private String username;

    private String userRole;

    private String content;

    private LocalDateTime createdAt;
}
