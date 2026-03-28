package com.cooxiao.mall.seckill.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.TemplateType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;
import java.util.Scanner;

/**
 * 代码生成类 - 兼容 MyBatis Plus 3.5.x
 */
@Deprecated
public class CodeGenerator {

    // 数据库连接参数
    public static String url = "jdbc:mysql://localhost:3306/mall_seckill?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true";
    public static String username = "root";
    public static String password = "tarena2017Up;";
    // 父级别包名称
    public static String parentPackage = "com.cooxiao.mall";
    // 模块名称，用于组成包名
    public static String modelName = "seckill";
    // 当前项目名称（在磁盘上的文件夹名）
    public static String projectName = "mall-seckill/mall-seckill-service";
    // 代码生成的目标路径
    public static String generateTo = "/" + projectName + "/src/main/java";
    // mapper.xml的生成路径
    public static String mapperXmlPath = "/" + projectName + "/src/main/resources/mapper";
    // 作者名
    public static String author = "cooxiao.com";

    /**
     * 读取控制台内容
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
        String projectPath = System.getProperty("user.dir");

        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> {
                    builder.author(author)
                            .outputDir(projectPath + generateTo)
                            .disableOpenDir();
                })
                .packageConfig(builder -> {
                    builder.parent(parentPackage)
                            .moduleName(modelName)
                            .entity("model")
                            .pathInfo(Collections.singletonMap(OutputFile.xml, projectPath + mapperXmlPath));
                })
                .templateConfig(builder -> {
                    builder.disable(TemplateType.XML)
                            .mapper("/ftl/mapper.java");
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

        System.out.println("代码生成完成！");
    }
}
