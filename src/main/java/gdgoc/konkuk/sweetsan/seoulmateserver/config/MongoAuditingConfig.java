package gdgoc.konkuk.sweetsan.seoulmateserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
    // MongoDB Auditing 활성화를 위한 설정 클래스
}
