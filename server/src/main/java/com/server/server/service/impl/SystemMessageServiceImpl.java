package com.server.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.server.server.entity.SystemMessage;
import com.server.server.mapper.SystemMessageMapper;
import com.server.server.service.SystemMessageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemMessageServiceImpl extends ServiceImpl<SystemMessageMapper, SystemMessage> implements SystemMessageService {

    @Override
    public List<SystemMessage> listByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<SystemMessage>()
                .eq(SystemMessage::getUserId, userId)
                .orderByDesc(SystemMessage::getCreatedAt));
    }

    @Override
    public boolean markRead(Long messageId) {
        SystemMessage message = this.getById(messageId);
        if (message == null) {
            return false;
        }
        message.setIsRead(1);
        return this.updateById(message);
    }

    @Override
    public boolean createMessage(SystemMessage message) {
        if (message == null || message.getUserId() == null) {
            return false;
        }
        if (message.getIsRead() == null) {
            message.setIsRead(0);
        }
        return this.save(message);
    }
}
