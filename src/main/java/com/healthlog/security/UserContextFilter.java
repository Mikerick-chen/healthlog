package com.healthlog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 從每個請求的 X-User-Id 標頭讀取使用者 ID，存入 {@link CurrentUser} ThreadLocal，
 * 請求結束後清除，避免執行緒重用造成資料外洩。
 */
@Component
@Order(1)
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String header = request.getHeader("X-User-Id");
            if (header != null && !header.isBlank()) {
                try {
                    CurrentUser.set(Long.parseLong(header.trim()));
                } catch (NumberFormatException ignored) {
                    // 非數字標頭忽略，沿用預設使用者
                }
            }
            chain.doFilter(request, response);
        } finally {
            CurrentUser.clear();
        }
    }
}
