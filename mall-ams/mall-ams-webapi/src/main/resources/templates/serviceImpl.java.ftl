<#if config.packageInfo.parent?? && config.packageInfo.parent != "">package ${config.packageInfo.parent}.${config.moduleName}.service.impl;
<#else>package ${config.moduleName}.service.impl;</#if>

import ${config.packageInfo.parent}.${config.moduleName}.model.${entityName};
import ${config.packageInfo.parent}.${config.moduleName}.mapper.${entityName}Mapper;
import ${config.packageInfo.parent}.${config.moduleName}.service.${entityName}Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>${table.comment!} 服务实现类</p>
 *
 * @author ${author}
 * @since ${now?string("yyyy-MM-dd HH:mm:ss")}
 */
@Service
public class ${entityName}ServiceImpl extends ServiceImpl<${entityName}Mapper, ${entityName}> implements ${entityName}Service {

}