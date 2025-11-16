package com.example.ragchatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static files from the Angular build
        // In Docker, files are copied to /app/static
        String staticPath = "/app/static";
        boolean staticDirExists = Files.exists(Paths.get(staticPath));
        
        if (staticDirExists) {
            // Docker deployment: serve from file system
            registry.addResourceHandler("/**")
                    .addResourceLocations("file:" + staticPath + "/")
                    .resourceChain(false)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) throws IOException {
                            // Skip API and actuator endpoints
                            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                                return null;
                            }
                            
                            Resource requestedResource = location.createRelative(resourcePath);
                            // If resource doesn't exist, serve index.html for Angular routing
                            if (!requestedResource.exists()) {
                                requestedResource = location.createRelative("index.html");
                            }
                            return requestedResource.exists() ? requestedResource : null;
                        }
                    });
        } else {
            // Local development: serve from classpath (if static files are packaged)
            registry.addResourceHandler("/**")
                    .addResourceLocations("classpath:/static/")
                    .resourceChain(false)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) throws IOException {
                            // Skip API and actuator endpoints
                            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                                return null;
                            }
                            
                            Resource requestedResource = location.createRelative(resourcePath);
                            // If resource doesn't exist, serve index.html for Angular routing
                            if (!requestedResource.exists()) {
                                requestedResource = location.createRelative("index.html");
                            }
                            return requestedResource.exists() ? requestedResource : null;
                        }
                    });
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect root to index.html for Angular routing
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}

