package com.lcq.aiagent.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "健康检查")
public class HealthController {

    /**
     * 检查应用是否能够正常处理 HTTP 请求。
     *
     * @return 固定文本 {@code ok}
     */
    @Operation(summary = "检查应用是否运行", description = "检查 HTTP 服务是否可访问，不调用模型或数据库。")
    @GetMapping(value = "/api/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "ok";
    }
}
