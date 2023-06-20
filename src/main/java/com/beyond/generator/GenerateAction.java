package com.beyond.generator;

import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.gen.freemarker.MapperEntity;
import com.beyond.gen.freemarker.MapperXmlEntity;
import com.beyond.generator.ui.GenerateForm;
import com.beyond.generator.ui.MsgDialog;
import com.beyond.generator.utils.MapperUtil;
import com.beyond.generator.utils.PsiFileUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
                String tableExcludePrefix = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("tableExcludePrefix"));
                String tablePrefix = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("tablePrefix"));
                String mapperSuffix = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("mapperSuffix"));
                String mapperXmlSuffix = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("mapperXmlSuffix"));
                String forMyBatisPlus = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("forMyBatisPlus"));
                String withDefaultValue = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("withDefaultValue"));

                MysqlDataSource dataSource = new MysqlDataSource();
                dataSource.setUrl(jdbcUrl);
                dataSource.setUser(username);
                dataSource.setPassword(password);
                JdbcTemplate jdbcTemplate = new JdbcTemplate();
                jdbcTemplate.setDataSource(dataSource);

                List<String> tableList;
                if (org.apache.commons.lang3.StringUtils.isBlank(tables)) {
                    String sql = String.format("select TABLE_NAME from information_schema.COLUMNS where TABLE_SCHEMA = '%s' group by TABLE_NAME", schema);
                    tableList = jdbcTemplate.queryForList(sql, String.class);
                } else {
                    tableList = Arrays.asList(org.apache.commons.lang3.StringUtils.split(tables, ","));
                }
                for (String table : tableList) {
                    table = table.trim();
                    String sql = String.format("select * from information_schema.COLUMNS where TABLE_SCHEMA = '%s' and TABLE_NAME = '%s'", schema, table);
                    List<Column> columns = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Column.class));
                    if (CollectionUtils.isEmpty(columns)) {
                        throw new RuntimeException("table not exist");
                    }

                    JavaEntity javaEntity = writeEntity(project, schema, table, columns, pkg, tableExcludePrefix, tablePrefix, Boolean.parseBoolean(forMyBatisPlus),  Boolean.parseBoolean(withDefaultValue));
                    MapperEntity mapperEntity = writeMapper(project, javaEntity, mapperPackage, mapperSuffix, schema, table, Boolean.parseBoolean(forMyBatisPlus));
                    writeMapperXml(project, javaEntity, mapperEntity, columns, mapperXmlPathInResource, mapperXmlSuffix);
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


    private JavaEntity writeEntity(Project project, String schema, String table, List<Column> columns, String pkg, String excludePrefix, String tablePrefix, boolean forMyBatisPlus, boolean withDefaultValue) {
        if (org.apache.commons.lang3.StringUtils.isBlank(pkg)) {
            return null;
        }

        String javaPath = PluginUtils.getProjectJavaPath(project);
        String targetDir = PathUtils.concat(javaPath, pkg.split("\\."));

        JavaEntity javaEntity = new JavaEntity();
        javaEntity.setPackageName(pkg);
        List<String> imports = new ArrayList<>();
        imports.add("lombok.Data");
        if (forMyBatisPlus) {
            imports.add("com.baomidou.mybatisplus.annotation.IdType");
            imports.add("com.baomidou.mybatisplus.annotation.TableField");
            imports.add("com.baomidou.mybatisplus.annotation.TableId");
            imports.add("com.baomidou.mybatisplus.annotation.TableName");
        }
        javaEntity.setImports(imports);
        String modifiedTableName;
        if (org.apache.commons.lang3.StringUtils.isNotBlank(excludePrefix)) {
            modifiedTableName = org.apache.commons.lang3.StringUtils.substringAfter(table, excludePrefix);
        } else {
            modifiedTableName = table;
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(tablePrefix)) {
            modifiedTableName = StringUtils.lineToHump(tablePrefix) + org.apache.commons.lang3.StringUtils.capitalize(modifiedTableName);
        }
        javaEntity.setClassName(org.apache.commons.lang3.StringUtils.capitalize(StringUtils.lineToHump(modifiedTableName)));

        List<Column> normalCols = columns;
        Column idCol = null;
        List<Column> ids = columns.stream().filter(x -> "PRI".equals(x.getColumnKey())).collect(Collectors.toList());
        if (ids.size() == 1){
            normalCols = columns.stream().filter(x -> !"PRI".equals(x.getColumnKey())).collect(Collectors.toList());
            idCol = ids.get(0);
        }
        if (idCol != null){
            Class<?> aClass = TypeConverter.toJavaType(idCol.getDataType());
            if (!aClass.getName().startsWith("java.lang")) {
                if (!imports.contains(aClass.getName())) {
                    imports.add(aClass.getName());
                }
            }
            JavaEntity.FieldEntity fieldEntity = new JavaEntity.FieldEntity();
            fieldEntity.setType(aClass.getSimpleName());
            fieldEntity.setName(StringUtils.lineToHump(idCol.getColumnName()));
            fieldEntity.setComment(idCol.getColumnComment());
            javaEntity.setId(fieldEntity);
        }

        for (Column column : normalCols) {
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
            if (withDefaultValue){
                fieldEntity.setValueStr(column.getColumnDefault());
            }
            javaEntity.getFields().add(fieldEntity);
        }
        javaEntity.setTableFullName(schema+"."+table);

        if (forMyBatisPlus) {
            PsiFileUtil.writeFromTemplate(project, PathUtils.concat(targetDir, javaEntity.getClassName()+".java"), javaEntity.toGen(false), "entity_mybatisplus.ftl");
        } else {
            PsiFileUtil.writeFromTemplate(project, PathUtils.concat(targetDir, javaEntity.getClassName()+".java"), javaEntity.toGen(false), "entity.ftl");
        }

        return javaEntity;
    }



    private MapperEntity writeMapper(Project project, JavaEntity javaEntity, String mapperPackage, String mapperSuffix, String schema, String table, boolean forMyBatisPlus) {
        if (org.apache.commons.lang3.StringUtils.isBlank(mapperSuffix)) {
            mapperSuffix = "Mapper";
        }
        mapperSuffix = org.apache.commons.lang3.StringUtils.capitalize(mapperSuffix);
        if (org.apache.commons.lang3.StringUtils.isBlank(mapperPackage)) {
            return null;
        }
        String javaPath = PluginUtils.getProjectJavaPath(project);
        String targetDir = PathUtils.concat(javaPath, mapperPackage.split("\\."));


        MapperEntity mapperEntity = new MapperEntity();
        mapperEntity.setMapperName(javaEntity.getClassName() + mapperSuffix);
        mapperEntity.setPackageName(mapperPackage);
        mapperEntity.setTableFullName(schema + "." + table);
        mapperEntity.setEntityName(javaEntity.getPackageName()+"."+javaEntity.getClassName());
        List<String> imports = new ArrayList<String>();
//        if (!javaEntity.getPackageName().equals(mapperPackage)){
//            imports.add(javaEntity.getPackageName()+"."+javaEntity.getClassName());
//        }
//        imports.add("java.util.List");

        if (forMyBatisPlus){
            mapperEntity.setSuperMapperName("BaseMapper<"+javaEntity.getClassName()+">");
            imports.add(javaEntity.getPackageName()+"."+javaEntity.getClassName());
        }

        imports.add("org.apache.ibatis.annotations.Mapper");
        if (forMyBatisPlus){
            imports.add("com.baomidou.mybatisplus.core.mapper.BaseMapper");
        }
        mapperEntity.setImports(imports);

        PsiFileUtil.writeFromTemplate(project, PathUtils.concat(targetDir,javaEntity.getClassName() + mapperSuffix + ".java"), mapperEntity, "mapper.ftl");

        return mapperEntity;
    }


    private void writeMapperXml(Project project, JavaEntity javaEntity, MapperEntity mapperEntity, List<Column> columns, String mapperXmlPathInResource, String mapperXmlSuffix) {
        if (org.apache.commons.lang3.StringUtils.isBlank(mapperXmlPathInResource)) {
            return;
        }
        if (mapperEntity == null) {
            return;
        }
        if (org.apache.commons.lang3.StringUtils.isBlank(mapperXmlSuffix)) {
            mapperXmlSuffix = "Mapper";
        }
        mapperXmlSuffix = org.apache.commons.lang3.StringUtils.capitalize(mapperXmlSuffix);

        String resources = PathUtils.concat(PluginUtils.getProjectSrcPath(project), "main", "resources");
        String targetDir = PathUtils.concat(resources, mapperXmlPathInResource);
        MapperXmlEntity mapperXmlEntity = MapperUtil.createMapperXmlEntity(javaEntity, mapperEntity, columns);

        PsiFileUtil.writeFromTemplate(project, PathUtils.concat(targetDir, javaEntity.getClassName() + mapperXmlSuffix + ".xml"), mapperXmlEntity, "mapperxml.ftl");

    }


}
