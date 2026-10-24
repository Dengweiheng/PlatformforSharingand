package com.server.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resource_audit_log")
public class ResourceAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private Long adminId;

    private String auditResult;

    private String auditRemark;

    private LocalDateTime createdAt;
}
