package com.server.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_message")
public class SystemMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String msgType;

    private String title;

    private String content;

    private Integer isRead;

    private LocalDateTime createdAt;
}
