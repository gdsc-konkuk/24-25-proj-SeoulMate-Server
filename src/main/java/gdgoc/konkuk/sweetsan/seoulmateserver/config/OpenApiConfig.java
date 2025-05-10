package gdgoc.konkuk.sweetsan.seoulmateserver.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("SeoulMate API")
                        .description("SeoulMate Application REST API Documentation")
                        .version("v1.0.0")
                        .contact(new Contact().name("Team Sweetsan")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .servers(servers());
    }

    private List<Server> servers() {
        Server productionServer = new Server()
                .url("https://whitepiano-codeserver.pe.kr/")
                .description("Production server");
        Server localServer = new Server().url("http://localhost:8080").description("Local server");

        return List.of(productionServer, localServer);
    }
}
