package com.beyond.generator.action;

import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.generator.Column;
import com.beyond.generator.PathUtils;
import com.beyond.generator.PluginUtils;
import com.beyond.generator.StringUtil;
import com.beyond.generator.TypeConverter;
import com.beyond.generator.ui.JdbcForm;
import com.beyond.generator.utils.MapperUtil;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiFileUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.PatternUtil;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.beyond.generator.utils.MapperUtil.msg;
import static com.beyond.generator.utils.MapperUtil.testConnection;
import static com.beyond.generator.utils.PropertyUtil.*;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * @author chenshipeng
 * @date 2023/01/24
 */
public abstract class GenerateMyBatisBaseAction extends MyBaseIntentionAction {


    protected boolean createXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, String tableFullName, String entityFullName, Document xmldoc, boolean resultMapExists, boolean baseColumnListExists ) throws JDOMException {

        String jdbcUrl = getProperty("jdbcUrl", project);
        String username = getUserName(project);
        String password = getPassword(project);
        if (StringUtils.isBlank(jdbcUrl) || StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            JdbcForm jdbcForm = new JdbcForm(project);
            jdbcForm.show(form -> {
                try {
                    String jdbcUrl1 = StringUtils.trimToNull(form.getData().get("jdbcUrl"));
                    String username1 = StringUtils.trimToNull(form.getData().get("username"));
                    String password1 = StringUtils.trimToNull(form.getData().get("password"));
                    boolean pass = testConnection(jdbcUrl1, username1, password1);
                    if (!pass) {
                        throw new RuntimeException("fail");
                    }

                    form.setOk(true);
                    form.close(OK_EXIT_CODE);
                } catch (Exception e) {
                    e.printStackTrace();
                    msg(project, e.getMessage());
                }
            }, form -> {
                try {
                    String jdbcUrl12 = form.getData().get("jdbcUrl").trim();
                    String username12 = form.getData().get("username").trim();
                    String password12 = form.getData().get("password").trim();

                    boolean pass = testConnection(jdbcUrl12, username12, password12);
                    if (!pass) {
                        throw new RuntimeException("fail");
                    }

                    form.setOk(false);

                } catch (Exception e) {
                    e.printStackTrace();
                    msg(project, e.getMessage());
                }
            });
            if (jdbcForm.isOk()) {
                jdbcUrl = getProperty("jdbcUrl", project);
                username = getUserName(project);
                password = getPassword(project);
                doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc,  resultMapExists, baseColumnListExists, jdbcUrl, username, password);
            }
            return jdbcForm.isOk();
        } else {
            doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc, resultMapExists, baseColumnListExists, jdbcUrl, username, password);
        }
        return true;
    }


    private void doCreateXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, String tableFullName, String entityFullName, Document xmldoc, boolean resultMapExists, boolean baseColumnListExists, String jdbcUrl, String username, String password) throws JDOMException {

        if (resultMapExists && baseColumnListExists) return;

        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setQueryTimeout(5);
        jdbcTemplate.setDataSource(dataSource);

        if (tableFullName == null || !tableFullName.contains(".")) {
            throw new RuntimeException("please add '/** @table schema.table */' in doc comment.");
        }

        String[] split = tableFullName.split("\\.");
        String schema = split[0];
        String tableName = split[1];

        String sql = String.format("select * from information_schema.COLUMNS where TABLE_SCHEMA = '%s' and TABLE_NAME = '%s'", schema, tableName);
        List<Column> columns = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Column.class));
        if (CollectionUtils.isEmpty(columns)) {
            throw new RuntimeException("table not exist");
        }


        if (!baseColumnListExists) {
            String[] lines = xmldoc.getText().split("\n");
            String mapperStr = PatternUtil.getFirstMatch(Arrays.asList(lines), Pattern.compile("(<mapper.*>)"));
            if (mapperStr != null) {
                int insertPos = xmldoc.getText().indexOf(mapperStr) + mapperStr.length();
                String columnList = "\n" + FragmentGenUtils.createXmlColumnList(MapperUtil.createMapperXmlColumnListEntity(columns)) + "\n";
                xmldoc.insertString(insertPos, columnList);
            }
        }

        if (!resultMapExists) {
            if (entityFullName == null || !entityFullName.contains(".")) {
                throw new RuntimeException("please add '/** @entity entityFullName */' in doc comment.");
            }

            String[] lines = xmldoc.getText().split("\n");
            String mapperStr = PatternUtil.getFirstMatch(Arrays.asList(lines), Pattern.compile("(<mapper.*>)"));
            if (mapperStr != null) {
                int insertPos = xmldoc.getText().indexOf(mapperStr) + mapperStr.length();
                String resultMap = "\n" + FragmentGenUtils.createXmlResultMap(MapperUtil.createMapperXmlResultMapEntity(entityFullName, columns)) + "\n";
                xmldoc.insertString(insertPos, resultMap);
            }
        }

        writeEntity(project, schema, tableName, columns, StringUtils.substringBeforeLast(entityFullName, "."), StringUtils.substringAfterLast(entityFullName, "."), false, false);

        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
    }


    private JavaEntity writeEntity(Project project, String schema, String table, List<Column> columns, String pkg, String entityName, boolean forMyBatisPlus, boolean withDefaultValue) {
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
        javaEntity.setClassName(org.apache.commons.lang3.StringUtils.capitalize(StringUtil.lineToHump(entityName)));

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
            fieldEntity.setName(StringUtil.lineToHump(idCol.getColumnName()));
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
            fieldEntity.setName(StringUtil.lineToHump(column.getColumnName()));
            fieldEntity.setComment(column.getColumnComment());
            if (withDefaultValue){
                fieldEntity.setValueStr(column.getColumnDefault());
            }
            javaEntity.getFields().add(fieldEntity);
        }
        javaEntity.setTableFullName(schema+"."+table);

        String path = PathUtils.concat(targetDir, javaEntity.getClassName() + ".java");
        if (new File(path).exists()){
            return javaEntity;
        }
        if (forMyBatisPlus) {
            PsiFileUtil.writeFromTemplate(project, path, javaEntity.toGen(false), "entity_mybatisplus.ftl");
        } else {
            PsiFileUtil.writeFromTemplate(project, path, javaEntity.toGen(false), "entity.ftl");
        }

        return javaEntity;
    }


}
