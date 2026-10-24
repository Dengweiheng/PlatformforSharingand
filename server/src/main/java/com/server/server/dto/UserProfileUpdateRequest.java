package com.server.server.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private String majorName;

    private String interestTags;
}
