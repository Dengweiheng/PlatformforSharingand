package com.server.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("favorite_item")
public class FavoriteItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long folderId;

    private Long userId;

    private Long resourceId;

    private LocalDateTime createdAt;
}
