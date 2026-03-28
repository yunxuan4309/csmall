// Deleted:package com.cooxiao.mall.resource.config;
// Deleted:
// Deleted:import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
// Deleted:import org.springframework.beans.factory.annotation.Autowired;
// Deleted:import org.springframework.context.annotation.Bean;
// Deleted:import org.springframework.context.annotation.Configuration;
// Deleted:import springfox.documentation.builders.ApiInfoBuilder;
// Deleted:import springfox.documentation.builders.PathSelectors;
// Deleted:import springfox.documentation.builders.RequestHandlerSelectors;
// Deleted:import springfox.documentation.service.ApiInfo;
// Deleted:import springfox.documentation.service.Contact;
// Deleted:import springfox.documentation.spi.DocumentationType;
// Deleted:import springfox.documentation.spring.web.plugins.Docket;
// Deleted:import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

package com.cooxiao.mall.resource.config;

import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j（OpenAPI3）的配置
 */
@Configuration
public class Knife4jConfiguration {

    /**
     * 标题
     */
    private String title = "酷鲨商城在线API文档--资源管理";
    /**
     * 简介
     */
    private String description = "酷鲨商城在线API文档--资源管理";
    /**
     * 服务条款URL
     */
    private String termsOfServiceUrl = "http://www.apache.org/licenses/LICENSE-2.0";
    /**
     * 联系人
     */
    private String contactName = "Java教学研发部";
    /**
     * 联系网址
     */
    private String contactUrl = "http://java.cooxiao.com";
    /**
     * 联系邮箱
     */
    private String contactEmail = "java@cooxiao.com";
    /**
     * 版本号
     */
    private String version = "1.0.0";
    
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title(title)
                        .description(description)
                        .termsOfService(termsOfServiceUrl)
                        .contact(new Contact()
                                .name(contactName)
                                .url(contactUrl)
                                .email(contactEmail))
                        .version(version));
    }
}
