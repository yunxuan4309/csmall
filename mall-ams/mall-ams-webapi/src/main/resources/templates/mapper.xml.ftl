<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${config.packageInfo.parent}.${config.moduleName}.mapper.${entityName}Mapper">

    <!-- 结果映射 -->
    <resultMap id="BaseResultMap" type="${config.packageInfo.parent}.${config.moduleName}.model.${entityName}">
<#list table.fields as field>
        <id column="${field.columnName}" property="${field.name}" />
</#list>
<#list table.commonFields as field>
        <result column="${field.columnName}" property="${field.name}" />
</#list>
    </resultMap>

    <!-- 所有列 -->
    <sql id="All_Columns">
<#assign columns = [] />
<#list table.fields as field>
        <#assign columns = columns + ["${field.columnName}"] />
</#list>
<#assign allColumns = columns?join(", ") />
        ${allColumns}
    </sql>

    <!-- 基础查询字段 -->
    <sql id="Base_Column_List">
        <include refid="All_Columns" />
    </sql>

</mapper>