/**
 * <p>${table.comment!} 实体类</p>
 *
 * @author ${author}
 * @since ${now?string("yyyy-MM-dd HH:mm:ss")}
 */
<#if config.packageInfo.parent?? && config.packageInfo.parent != "">package ${config.packageInfo.parent}.${config.moduleName}.model;
<#else>package ${config.moduleName}.model;</#if>

import com.baomidou.mybatisplus.annotation.*;
<#if entityLombokModel??>
import lombok.Data;
import lombok.experimental.Accessors;
</#if>
<#if chainModel??>
import lombok.experimental.Accessors;
</#if>

<#assign tableName="${table.name}"/>
<#assign tableComment="${table.comment!table.name}"/>
<#assign pk = table.pk as string/>
<#assign pkType = table.pk.type as string/>
<#assign fields = table.fields />

<#if superEntityClass??>
import ${superEntityClass};
<#if !entityLombokModel??>
import java.io.Serializable;
</#if>
</#if>

<#if activeRecord??>
import com.baomidou.mybatisplus.extension.activerecord.Model;
<#if !entityLombokModel??>
import java.io.Serializable;
</#if>
</#if>

<#if entityColumnConstant??>
import com.baomidou.mybatisplus.annotation.ColumnConstants;
</#if>

<#if kotlin?>
import java.io.Serializable;
</#if>

<#if swagger2??>
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
</#if>

<#if hibernateValidator??>
import javax.validation.constraints.*;
</#if>

<#-- 逻辑删除注解 -->
<#if tableLogicField??>
import com.baomidou.mybatisplus.annotation.TableLogic;
</#if>

<#-- 枚举类型注解 -->
<#if hasEnumFields??>
import com.fasterxml.jackson.annotation.JsonValue;
</#if>

<#-- 表名和主键策略 -->
@TableName("${tableName}")
<#if swagger2??>
@ApiModel(value = "${tableComment}对象", description = "${tableComment}表实体类")
</#if>
<#if entityLombokModel??>
@Data
</#if>
<#if chainModel??>
@Accessors(chain = true)
</#if>
<#if activeRecord??>
public class ${entityName} extends Model<${entityName}><#elseif superEntityModel??>
public class ${entityName} extends ${superEntityClass}<#elseif kotlin?>
class ${entityName}<#else>
class ${entityName}<#if superEntityClass??> extends ${superEntityClass}</#if></#if> {

<#-- 序列化ID -->
<#if !kotlin??>
<#if entitySerialVersionUID??>
    private static final long serialVersionUID = 1L;
</#if>
</#if>

<#-- 字段生成 -->
<#list fields as field>
<#assign isPk = field.keyFlag/>
<#assign fieldType = field.type as string/>
<#assign fieldName = field.name/>
<#assign fieldComment = field.comment!""/>
<#assign columnName = field.columnName/>
<#assign ignoreConvert = field.ignoreConvert/>

<#-- 字段注释 -->
<#if fieldComment?has_content>
    /**
     * ${fieldComment}
     */
</#if>

<#-- Swagger 注解 -->
<#if swagger2??>
    <#if fieldComment?has_content>
    @ApiModelProperty(value = "${fieldComment}")
    </#if>
</#if>

<#-- 验证注解 -->
<#if hibernateValidator??>
    <#if field.notNull && !field.keyFlag>
    @NotNull(message = "${fieldComment!'该字段'}不能为空")
    </#if>
    <#if field.length.gt(0)>
    @Size(max=${field.length}, message = "${fieldComment!'该字段'}长度不能超过${field.length}个字符")
    </#if>
</#if>

<#-- 主键注解 -->
    <#if isPk>
        <#if idGenType??>
            <#if idGenType == "auto">
    @TableId(value = "${columnName}", type = IdType.AUTO)
            <#elseif idGenType == "none">
    @TableId(value = "${columnName}", type = IdType.NONE)
            <#elseif idGenType == "input">
    @TableId(value = "${columnName}", type = IdType.INPUT)
            <#elseif idGenType == "idWorker">
    @TableId(value = "${columnName}", type = IdType.ID_WORKER)
            <#elseif idGenType == "uuid">
    @TableId(value = "${columnName}", type = IdType.UUID)
            <#elseif idGenType == "snowflake">
    @TableId(value = "${columnName}", type = IdType.SNOWFLAKE)
            <#elseif idGenType == "idWorkerStr">
    @TableId(value = "${columnName}", type = IdType.ID_WORKER_STR)
            <#elseif idGenType == "snowflake">
    @TableId(value = "${columnName}", type = IdType.SNOWFLAKE)
            </#if>
        <#else>
    @TableId(value = "${columnName}", type = IdType.AUTO)
        </#if>
    <#else>
        <#-- 逻辑删除字段 -->
        <#if field.logicDelete>
    @TableLogic
        <#-- 版本号字段 -->
        <#elseif field.version>
    @Version
        <#-- 其他普通字段 -->
        </#if>
    @TableField("${columnName}")
    </#if>

<#-- 字段定义 -->
    <#if kotlin?>
    var ${fieldName}: ${fieldType}<#if field.nullable>?</#if> = <#if field.defaultValue?has_content>${field.defaultValue}<#else>null</#if>
    <#else>
    private ${fieldType} ${fieldName};
    </#if>

<#-- Getter/Setter 方法 (仅当没有使用 Lombok 时) -->
<#if !entityLombokModel? && !kotlin? && !chainModel? >
    public ${fieldType} get${field.capitalName}() {
        return ${fieldName};
    }

    public void set${field.capitalName}(${fieldType} ${fieldName}) {
        this.${fieldName} = ${fieldName};
    }
</#if>
</#list>

<#-- toString, equals, hashCode 方法 (仅当没有使用 Lombok 时) -->
<#if !entityLombokModel? && !kotlin? && !chainModel? >
    @Override
    public String toString() {
        return "${entityName}{" +
<#list fields as field>
                "${field.name}=" + ${field.name} + ", " +
</#list>
                "}";
    }
</#if>

<#if !entityLombokModel? && !kotlin? && !chainModel? >
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ${entityName} that = (${entityName}) o;

<#list fields as field>
        if (${field.name} != null ? !${field.name}.equals(that.${field.name}) : that.${field.name} != null) return false;
</#list>
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
<#list fields as field>
        result = 31 * result + (${field.name} != null ? ${field.name}.hashCode() : 0);
</#list>
        return result;
    }
</#if>

<#if activeRecord??>
    @Override
    protected Serializable pkVal() {
        <#if pk??>return this.${pk};
        <#else>return null;
        </#if>
    }
</#if>
}