package com.beyond.generator;

import com.beyond.gen.freemarker.FreeMarkerWriter;
import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.gen.freemarker.MapperEntity;
import com.beyond.gen.freemarker.MapperXmlEntity;
import com.beyond.generator.ui.GenerateForm;
import com.beyond.generator.ui.MsgDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * @author chenshipeng
 * @date 2021/05/07
 */
public class GenerateAction extends AnAction {

    public void actionPerformed(@NotNull AnActionEvent event) {

        GenerateForm generateForm = new GenerateForm(event.getProject());
        generateForm.show(form -> {

            Project project = event.getProject();

            try {
                String jdbcUrl = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("jdbcUrl"));
                String username = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("username"));
                String password = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("password"));
                String schema = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("schema"));
                String tables = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("tables"));
                String pkg = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("package"));
                String mapperPackage = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("mapperPackage"));
                String mapperXmlPathInResource = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("mapperXmlPathInResource"));

                MysqlDataSource dataSource = new MysqlDataSource();
                dataSource.setUrl(jdbcUrl);
                dataSource.setUser(username);
                dataSource.setPassword(password);
                JdbcTemplate jdbcTemplate = new JdbcTemplate();
                jdbcTemplate.setDataSource(dataSource);

                List<String> tableList;
                if (org.apache.commons.lang3.StringUtils.isBlank(tables)){
                    String sql = String.format("select TABLE_NAME from information_schema.COLUMNS where TABLE_SCHEMA = '%s' group by TABLE_NAME", schema);
                    tableList = jdbcTemplate.queryForList(sql, String.class);
                }else {
                    tableList = Arrays.asList(org.apache.commons.lang3.StringUtils.split(tables, ","));
                }
                for (String table : tableList) {
                    table = table.trim();
                    String sql = String.format("select * from information_schema.COLUMNS where TABLE_SCHEMA = '%s' and TABLE_NAME = '%s'", schema, table);
                    List<Column> columns = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Column.class));
                    if (CollectionUtils.isEmpty(columns)) {
                        throw new RuntimeException("table not exist");
                    }

                    JavaEntity javaEntity = writeEntity(project, table, columns, pkg);
                    MapperEntity mapperEntity = writeMapper(project, javaEntity, mapperPackage);
                    writeMapperXml(project, javaEntity, mapperEntity, columns, mapperXmlPathInResource);
                }

                form.close(OK_EXIT_CODE);
            } catch (Exception e) {
                e.printStackTrace();
                MsgDialog msgDialog = new MsgDialog(project, e.getMessage());
                msgDialog.show();
            }

        }, form -> {

            try {
                String jdbcUrl = form.getData().get("jdbcUrl").trim();
                String username = form.getData().get("username").trim();
                String password = form.getData().get("password").trim();

                MysqlDataSource dataSource = new MysqlDataSource();
                dataSource.setUrl(jdbcUrl);
                dataSource.setUser(username);
                dataSource.setPassword(password);
                JdbcTemplate jdbcTemplate = new JdbcTemplate();
                jdbcTemplate.setDataSource(dataSource);

                String sql = "select 1";
                String result = jdbcTemplate.queryForObject(sql, String.class);

                if ("1".equals(result)) {
                    MsgDialog msgDialog = new MsgDialog(event.getProject(), "success");
                    msgDialog.show();
                } else {
                    MsgDialog msgDialog = new MsgDialog(event.getProject(), "fail");
                    msgDialog.show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                MsgDialog msgDialog = new MsgDialog(event.getProject(), e.getMessage());
                msgDialog.show();
            }

        });

    }


    private JavaEntity writeEntity(Project project, String table, List<Column> columns, String pkg) {
        if (org.apache.commons.lang3.StringUtils.isBlank(pkg)) {
            return null;
        }

        String javaPath = PluginUtils.getProjectJavaPath(project);
        String targetDir = PathUtils.concat(javaPath, pkg.split("\\."));

        JavaEntity javaEntity = new JavaEntity();
        javaEntity.setPackageName(pkg);
        List<String> imports = new ArrayList<>();
        imports.add("lombok.Data");
        javaEntity.setImports(imports);
        javaEntity.setClassName(org.apache.commons.lang3.StringUtils.capitalize(StringUtils.lineToHump(table)));
        for (Column column : columns) {
            Class<?> aClass = TypeConverter.toJavaType(column.getDataType());
            if (!aClass.getName().startsWith("java.lang")) {
                if (!imports.contains(aClass.getName())) {
                    imports.add(aClass.getName());
                }
            }
            JavaEntity.FieldEntity fieldEntity = new JavaEntity.FieldEntity();
            fieldEntity.setType(aClass.getSimpleName());
            fieldEntity.setName(StringUtils.lineToHump(column.getColumnName()));
            fieldEntity.setComment(column.getColumnComment());
            javaEntity.getFields().add(fieldEntity);
        }

        FreeMarkerWriter freeMarkerWriter =
                new FreeMarkerWriter("", "entity.ftl", targetDir, javaEntity.getClassName() + ".java");
        freeMarkerWriter.write(javaEntity);
        return javaEntity;
    }


    private MapperEntity writeMapper(Project project, JavaEntity javaEntity, String mapperPackage) {
        if (org.apache.commons.lang3.StringUtils.isBlank(mapperPackage)) {
            return null;
        }
        String javaPath = PluginUtils.getProjectJavaPath(project);
        String targetDir = PathUtils.concat(javaPath, mapperPackage.split("\\."));
        FreeMarkerWriter freeMarkerWriter =
                new FreeMarkerWriter("", "mapper.ftl", targetDir, javaEntity.getClassName() + "Mapper.java");

        MapperEntity mapperEntity = new MapperEntity();
        mapperEntity.setMapperName(javaEntity.getClassName() + "Mapper");
        mapperEntity.setPackageName(mapperPackage);
        List<String> imports = new ArrayList<String>();
//        if (!javaEntity.getPackageName().equals(mapperPackage)){
//            imports.add(javaEntity.getPackageName()+"."+javaEntity.getClassName());
//        }
        imports.add("org.apache.ibatis.annotations.Mapper");
        mapperEntity.setImports(imports);
        freeMarkerWriter.write(mapperEntity);
        return mapperEntity;
    }


    private void writeMapperXml(Project project, JavaEntity javaEntity, MapperEntity mapperEntity, List<Column> columns, String mapperXmlPathInResource) {
        if (org.apache.commons.lang3.StringUtils.isBlank(mapperXmlPathInResource)) {
            return;
        }
        if (mapperEntity == null) {
            return;
        }
        String resources = PathUtils.concat(PluginUtils.getProjectSrcPath(project), "main", "resources");
        String targetDir = PathUtils.concat(resources, mapperXmlPathInResource);
        FreeMarkerWriter freeMarkerWriter =
                new FreeMarkerWriter("", "mapperxml.ftl", targetDir, javaEntity.getClassName() + "Mapper.xml");

        MapperXmlEntity mapperXmlEntity = new MapperXmlEntity();
        mapperXmlEntity.setEntityClassFullName(javaEntity.getPackageName() + "." + javaEntity.getClassName());
        mapperXmlEntity.setMapperClassFullName(mapperEntity.getPackageName() + "." + mapperEntity.getMapperName());
        for (Column column : columns) {
            String columnKey = column.getColumnKey();
            if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                mapperXmlEntity.setIdColumnName(column.getColumnName());
                mapperXmlEntity.setIdPropertyName(StringUtils.lineToHump(column.getColumnName()));
                mapperXmlEntity.setIdJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                break;
            }
        }
        ;
        for (Column column : columns) {
            String columnKey = column.getColumnKey();
            if (!org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                MapperXmlEntity.ColumnEntity columnEntity = new MapperXmlEntity.ColumnEntity();
                columnEntity.setColumnName(column.getColumnName());
                columnEntity.setPropertyName(StringUtils.lineToHump(column.getColumnName()));
                columnEntity.setJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                mapperXmlEntity.getNormalColumns().add(columnEntity);
            }
        }

        freeMarkerWriter.write(mapperXmlEntity);
    }


}
