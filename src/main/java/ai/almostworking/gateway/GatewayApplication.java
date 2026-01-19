package ai.almostworking.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(p -> p.path("/hello")
                        .filters(f -> f.tokenRelay())
                        .uri("http://localhost:8080"))
                .route(p -> p.path("/greetings").uri("http://localhost:8080"))
                .route(p -> p.path("/minio/**").uri("http://localhost:8080"))
                .route(p -> p.query("X-Amz-Signature")
                        .uri("http://localhost:9000"))
                .route(p -> p.path("/api/**")
                        .uri("http://localhost:8081"))
                .build();
    }

}
