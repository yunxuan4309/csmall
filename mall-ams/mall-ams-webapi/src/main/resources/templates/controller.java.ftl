<#if config.packageInfo.parent?? && config.packageInfo.parent != "">package ${config.packageInfo.parent}.${config.moduleName}.web;
<#else>package ${config.moduleName}.web;</#if>

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import ${config.packageInfo.parent}.${config.moduleName}.service.${entityName}Service;
import ${config.packageInfo.parent}.${config.moduleName}.model.${entityName};
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * <p>${table.comment!} 前端控制器</p>
 *
 * @author ${author}
 * @since ${now?string("yyyy-MM-dd HH:mm:ss")}
 */
@Api(tags = {"${table.comment!}"})
@RestController
@RequestMapping("/${config.moduleName}/${entityName?uncap_first}")
class ${entityName}Controller {

    @Autowired
    private ${entityName}Service ${entityName?uncap_first}Service;

    /**
     * 查询所有
     */
    @RequestMapping("/list")
    public List<${entityName}> list() {
        return ${entityName?uncap_first}Service.list();
    }
}