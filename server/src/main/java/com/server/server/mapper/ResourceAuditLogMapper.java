package com.server.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.server.server.entity.ResourceAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResourceAuditLogMapper extends BaseMapper<ResourceAuditLog> {
}
