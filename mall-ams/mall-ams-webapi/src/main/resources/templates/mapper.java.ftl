<#if config.packageInfo.parent?? && config.packageInfo.parent != "">package ${config.packageInfo.parent}.${config.moduleName}.mapper;
<#else>package ${config.moduleName}.mapper;</#if>

import ${config.packageInfo.parent}.${config.moduleName}.model.${entityName};
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>${table.comment!} Mapper 接口</p>
 *
 * @author ${author}
 * @since ${now?string("yyyy-MM-dd HH:mm:ss")}
 */
@Mapper
public interface ${entityName}Mapper extends BaseMapper<${entityName}> {

}