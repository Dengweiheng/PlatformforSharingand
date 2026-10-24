package com.server.server.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteFolderItemVO {

    private Long itemId;

    private Long resourceId;

    private String title;

    private String coverUrl;

    private String fileUrl;

    private LocalDateTime createdAt;
}
