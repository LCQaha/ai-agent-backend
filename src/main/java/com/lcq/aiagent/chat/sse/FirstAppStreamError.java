package com.lcq.aiagent.chat.sse;

import com.lcq.aiagent.chat.exception.EmptyModelResponseException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

public record FirstAppStreamError(String code, String message) {

    public static FirstAppStreamError from(Throwable failure) {
        boolean networkError = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof EmptyModelResponseException) {
                return new FirstAppStreamError("empty_response", "模型没有返回正文，可以重新发送。");
            }
            if (cause instanceof SSLHandshakeException) {
                return new FirstAppStreamError("upstream_tls", "与模型服务建立安全连接时失败，请检查网络或代理后重试。");
            }
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) {
                return timeout();
            }
            if (cause instanceof OpenAIServiceException serviceError) {
                int status = serviceError.statusCode();
                if (status == 401 || status == 403) {
                    return new FirstAppStreamError("upstream_access", "模型服务拒绝了访问，请检查服务端的密钥和模型权限。");
                }
                if (status == 429) {
                    return new FirstAppStreamError("upstream_rate_limit", "模型服务限制了本次请求。请稍后重试，或检查调用额度。");
                }
                if (status == 408 || status == 504) {
                    return timeout();
                }
                if (status >= 500 && status < 600) {
                    return new FirstAppStreamError("upstream_unavailable", "模型服务暂时无法处理请求，请稍后重试。");
                }
            }
            // SDK 的网络异常可能包着更具体的 TLS 或超时异常，继续检查原因链。
            if (cause instanceof OpenAIIoException || cause instanceof SocketException || cause instanceof UnknownHostException) {
                networkError = true;
            }
        }
        if (networkError) {
            return new FirstAppStreamError("upstream_network", "与模型服务的网络连接异常，请检查网络后重试。");
        }
        return new FirstAppStreamError("generation_failed", "这一轮回答没有完成，请稍后再试。");
    }

    private static FirstAppStreamError timeout() {
        return new FirstAppStreamError("upstream_timeout", "等待模型服务响应超时，请稍后重试。");
    }
}
