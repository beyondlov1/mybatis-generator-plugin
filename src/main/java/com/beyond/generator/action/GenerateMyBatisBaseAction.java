package com.beyond.generator.action;

import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.generator.Column;
import com.beyond.generator.ui.JdbcForm;
import com.beyond.generator.utils.MapperUtil;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.beyond.generator.utils.MapperUtil.msg;
import static com.beyond.generator.utils.MapperUtil.testConnection;
import static com.beyond.generator.utils.PropertyUtil.*;
import static com.beyond.generator.utils.PropertyUtil.getPassword;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * @author chenshipeng
 * @date 2023/01/24
 */
public abstract class GenerateMyBatisBaseAction extends PsiElementBaseIntentionAction {

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

        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
    }

}
