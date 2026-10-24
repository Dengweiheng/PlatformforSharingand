package com.server.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resource_info")
public class ResourceInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;

    private String title;

    private String summary;


    private String subjectName;

    private String examType;

    private String competitionType;

    private String fileUrl;

    private String coverUrl;

    private String previewUrl;

    private Integer latestVersionNo;

    private String visibility;

    private String auditStatus;

    private Integer downloadCount;

    private Integer favoriteCount;

    private java.math.BigDecimal averageScore;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String tags;
}
