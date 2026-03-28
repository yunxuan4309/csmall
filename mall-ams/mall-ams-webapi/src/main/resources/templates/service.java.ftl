<#if config.packageInfo.parent?? && config.packageInfo.parent != "">package ${config.packageInfo.parent}.${config.moduleName}.service;
<#else>package ${config.moduleName}.service;</#if>

import com.baomidou.mybatisplus.extension.service.IService;
import ${config.packageInfo.parent}.${config.moduleName}.model.${entityName};

/**
 * <p>${table.comment!} 服务类</p>
 *
 * @author ${author}
 * @since ${now?string("yyyy-MM-dd HH:mm:ss")}
 */
public interface ${entityName}Service extends IService<${entityName}> {

}