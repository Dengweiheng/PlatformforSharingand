package com.server.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resource_file_version")
public class ResourceFileVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private Integer versionNo;

    private String fileUrl;

    private String fileName;

    private String changeLog;

    private LocalDateTime createdAt;
}
