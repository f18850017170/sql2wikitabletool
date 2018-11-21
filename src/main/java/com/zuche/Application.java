package com.zuche;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

@SpringBootApplication
public class Application implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static final Path ROOT_PATH = Paths.get(System.getProperty("user.dir"), "src/main/resources","target");

    public static void main(String[] args) {
        System.getProperties().list(System.out);

        SpringApplication.run(Application.class, args);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Value("${schema}")
    private String schema;
    @Override
    public void run(String... args) throws Exception {
        log.info("query tables");
        List<Map<String, Object>> tables = jdbcTemplate.queryForList("select TABLE_NAME from information_schema.TABLES WHERE table_schema =?", new Object[]{schema});

        for (Map<String, Object> table : tables) {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList("select  * from information_schema.`COLUMNS` where TABLE_NAME=? and TABLE_SCHEMA=?", new Object[]{table.get("TABLE_NAME"), schema});
            generate((String) table.get("TABLE_NAME"), columns);
        }
        String totalTables = "totalTables";
        File targetFile = ROOT_PATH.resolve(totalTables + ".wm").toFile();
        if (targetFile.exists()) {
            targetFile.delete();
        }
        targetFile.createNewFile();
        Stream<Path> pathStreams = Files.list(ROOT_PATH);
        pathStreams.forEach(item -> {
            try {
                Files.write(targetFile.toPath(), Files.readAllBytes(item), StandardOpenOption.APPEND);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        log.info("all tables generated in wiki-table style ,total={}", tables.size());
    }

    /**
     * 创建文件
     *
     * @param table
     * @param columns
     */
    private void generate(String table, List<Map<String, Object>> columns) {
        VelocityContext context = new VelocityContext();
        List<String> columnsDes = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        for (Map<String, Object> column : columns) {
            stringBuilder.setLength(0);
            stringBuilder.append("|").append(getColumnProValue(column.get("COLUMN_NAME"))).append("|").append(getColumnProValue(column.get("COLUMN_COMMENT")));
            stringBuilder.append("|").append(getColumnProValue(column.get("COLUMN_TYPE"))).append("|").append(getColumnProValue(column.get("COLUMN_DEFAULT")));
            stringBuilder.append("|").append(getColumnProValue(column.get("IS_NULLABLE")));
            stringBuilder.append("|").append(" ").append("|").append(" ");
            columnsDes.add(stringBuilder.toString());

            context.put("columns", columnsDes);
            context.put("tableName", table);
            //模板文件地址
            String templateBasePath = "template";
            Properties properties = new Properties();
            properties.setProperty("resource.loader", "class");
            properties.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
            properties.setProperty("file.resource.loader.description", "Velocity File Resource Loader");
            properties.setProperty("file.resource.loader.path", templateBasePath);
            properties.setProperty("file.resource.loader.cache", "true");
            properties.setProperty("file.resource.loader.modificationCheckInterval", "30");
            properties.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.Log4JLogChute");
            properties.setProperty("runtime.log.logsystem.log4j.logger", "org.apache.velocity");
            properties.setProperty("directive.set.null.allowed", "true");
            VelocityEngine velocityEngine = new VelocityEngine();
            velocityEngine.init(properties);
            try {
                //加载模板文件
                Template template = velocityEngine.getTemplate("template/template.vel", "UTF-8");
                File targetFile = ROOT_PATH.resolve(table + ".wm").toFile();
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                targetFile.createNewFile();
                FileOutputStream fos = new FileOutputStream(targetFile);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
                //渲染生成
                template.merge(context, writer);
                writer.flush();
                writer.close();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("create file {}.wm", table);
        }
    }

    /**
     * 获取column属性
     *
     * @param value
     * @return
     */
    private String getColumnProValue(Object value) {
        return null == value ? " " : ((String) value).length() > 0 ? ((String) value) : " ";
    }
}
