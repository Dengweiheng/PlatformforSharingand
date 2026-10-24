package com.server.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {

    // 开题说明：本项目用于实现个性化学习资源共享与推荐平台后端，
    // 当前阶段以实习可交付为目标，先完成用户中心、资源上传与行为记录等核心模块。

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

}
