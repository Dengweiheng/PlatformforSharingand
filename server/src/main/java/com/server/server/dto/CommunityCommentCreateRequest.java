package com.server.server.dto;

import lombok.Data;

@Data
public class CommunityCommentCreateRequest {

    private Long userId;

    private String content;
}
