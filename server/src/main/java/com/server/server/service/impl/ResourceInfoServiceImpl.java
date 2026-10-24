package com.server.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.server.server.entity.ResourceInfo;
import com.server.server.mapper.ResourceInfoMapper;
import com.server.server.service.ResourceInfoService;
import org.springframework.stereotype.Service;

@Service
public class ResourceInfoServiceImpl extends ServiceImpl<ResourceInfoMapper, ResourceInfo> implements ResourceInfoService {
}
