package org.example.roadsimulation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 设置允许的域名
        config.addAllowedOrigin("http://localhost:5173"); // Vite默认端口
        config.addAllowedOrigin("http://127.0.0.1:5173");

        // 设置允许的请求方法
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");

        // 设置允许的请求头
        config.addAllowedHeader("*");

        // 允许携带凭证（如cookies）
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有接口应用跨域配置
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}