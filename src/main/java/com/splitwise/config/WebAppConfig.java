package com.splitwise.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Serves the React app (built into classpath:/static by the {@code frontend} Maven profile) from the same origin as
 * the API. Page routes such as /groups/12 have no file of their own, so they get index.html and the app's router takes
 * over; API paths and missing files still get a 404. Without a built app nothing changes: there is no index.html to
 * fall back to.
 */
@Configuration
public class WebAppConfig implements WebMvcConfigurer {

    private static final String LOCATION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite puts a content hash in every asset name, so a file under /assets never changes
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(LOCATION + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // index.html names the current assets, so the browser checks for a newer one on every visit
        registry.addResourceHandler("/**")
                .addResourceLocations(LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new AppPageResolver());
    }

    /** Falls back to index.html for paths that look like app pages. */
    static class AppPageResolver extends PathResourceResolver {

        private static final List<String> NOT_PAGES = List.of("api/", "actuator/", "v3/", "swagger-ui/");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource file = super.getResource(resourcePath, location);
            if (file != null) {
                return file;
            }
            boolean isPage = !resourcePath.contains(".") && NOT_PAGES.stream().noneMatch(resourcePath::startsWith);
            return isPage ? super.getResource("index.html", location) : null;
        }
    }
}
