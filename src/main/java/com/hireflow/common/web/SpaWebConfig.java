package com.hireflow.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Lets one backend deployment serve the built Angular app as well as the API, so the live
 * deploy is a single URL instead of two separate Render services. The Angular build output is
 * copied into {@code src/main/resources/static} at Docker build time (see the root Dockerfile's
 * frontend-build stage) - Spring Boot serves static content from there automatically, but not
 * with SPA-style fallback routing, so an Angular client-side route like {@code /settings} hit
 * directly (or refreshed) would 404 without this.
 *
 * <p>{@code @RestController}-mapped routes (everything under {@code /api/**}) are matched by
 * Spring's {@code DispatcherServlet} before this resource handler is ever consulted, so this
 * never intercepts a real API call - the {@code resourcePath.startsWith("api/")} check below is
 * just a defensive belt-and-suspenders guard, not the actual mechanism keeping the two separate.
 * The same pattern as nginx's {@code try_files $uri $uri/ /index.html} (see
 * {@code frontend/nginx.conf}, used by the separate Docker Compose frontend container).
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
