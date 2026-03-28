package com.cooxiao.mall.ams.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;
import java.util.Scanner;

/**
 * 代码生成类
 */
public class CodeGenerator {

    // 数据库连接参数
    public static String driver = "com.mysql.cj.jdbc.Driver";
    public static String url = "jdbc:mysql://localhost:3306/mall_ams?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true";
    public static String username = "root";
    public static String password = "tarena2017Up;";
    // 父级别包名称
    public static String parentPackage = "com.cooxiao.mall";
    // 模块名称，用于组成包名
    public static String modelName = "ams";
    // 当前项目名称（在磁盘上的文件夹名）
    public static String projectName = "mall-sso";
    // 代码生成的目标路径
    public static String generateTo = "/" + projectName + "/src/main/java";
    // mapper.xml的生成路径
    public static String mapperXmlPath = "/" + projectName + "/src/main/resources/mapper";
    // 作者名
    public static String author = "cooxiao.com";
    // Mapper接口的模板文件，不用写后缀 .ftl
    public static String mapperTemplate = "/ftl/mapper.java";
    // 控制器的公共基类，用于抽象控制器的公共方法，null值表示没有父类
    public static String baseControllerClassName;
    // 业务层的公共基类，用于抽象公共方法
    public static String baseServiceClassName;

    /**
     * <p>
     * 读取控制台内容
     * </p>
     */
    public static String scanner(String tip) {
        Scanner scanner = new Scanner(System.in);
        StringBuilder help = new StringBuilder();
        help.append("请输入" + tip + "：");
        System.out.println(help.toString());
        if (scanner.hasNext()) {
            String ipt = scanner.next();
            if (ipt != null && !ipt.trim().isEmpty()) {
                return ipt;
            }
        }
        throw new RuntimeException("请输入正确的" + tip + "！");
    }

    /**
     * RUN THIS
     */
    public static void main(String[] args) {
        // 代码生成器
        String projectPath = System.getProperty("user.dir");

        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> {
                    builder.author(author)
                            .outputDir(projectPath + generateTo)
                            .disableOpenDir(); // 生成后不自动打开目录
                })
                .packageConfig(builder -> {
                    builder.parent(parentPackage)
                            .moduleName(modelName)
                            .entity("model")
                            .pathInfo(Collections.singletonMap(OutputFile.xml, projectPath + mapperXmlPath));
                })
                .templateConfig(builder -> {
                    builder.mapper("/ftl/mapper.java").xml(null); // 使用自定义模板并禁用默认XML模板
                })
                .strategyConfig(builder -> {
                    builder.addInclude(scanner("表名, all全部表"))
                            .addTablePrefix(modelName + "_")
                            .entityBuilder()
                                .enableLombok()
                                .enableTableFieldAnnotation()
                                .naming(NamingStrategy.underline_to_camel)
                                .columnNaming(NamingStrategy.underline_to_camel)
                            .controllerBuilder()
                                .enableRestStyle()
                            .serviceBuilder()
                                .formatServiceFileName("%sService")
                                .formatServiceImplFileName("%sServiceImpl");
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
