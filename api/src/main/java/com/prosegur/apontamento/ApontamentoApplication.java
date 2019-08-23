package com.prosegur.apontamento;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

@SpringBootApplication
@Configuration
public class ApontamentoApplication implements WebMvcConfigurer {

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void afterPropertiesSet() {
        objectMapper.findAndRegisterModules();
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ApontamentoApplication.class, args);
    }

    @Value("${spring.resources.static-locations}")
    String resourceLocations;
    @ControllerAdvice
    public static class NotFoundHandler {

        @ExceptionHandler(NoHandlerFoundException.class)
        public ResponseEntity<String> renderDefaultPage() {

            try {
                File indexFile = ResourceUtils.getFile("classpath:/static/index.html");
                FileInputStream inputStream = new FileInputStream(indexFile);
                String body = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
            } catch (IOException e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
            }
        }

    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations(resourceLocations);
    }
}
