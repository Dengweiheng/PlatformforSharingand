package com.server.server.dto;

import lombok.Data;

@Data
public class MessageCreateRequest {

    private Long userId;

    private String msgType;

    private String title;

    private String content;
}
