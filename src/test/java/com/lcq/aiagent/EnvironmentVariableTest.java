package com.lcq.aiagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 检查当前测试进程的环境变量，不启动 Spring，也不调用模型或打印密钥。
 * 在 IDEA 的此测试运行配置中导入 .env 后执行。
 * 测试通过只表示变量已加载，不代表 Key 有效，也不代表后端运行配置已导入。
 */
class EnvironmentVariableTest {

    @Test
    void shouldLoadOpenAiApiKey() {
        String key = System.getenv("OPENAI_API_KEY");

        assertNotNull(key, "没有读取到 OPENAI_API_KEY，请检查此测试运行配置是否导入 .env");
        assertFalse(key.isBlank(), "OPENAI_API_KEY 是空值");
        assertFalse(key.contains("${"), "读取到的是占位符，不是真实密钥");
    }
}
