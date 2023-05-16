package com.beyond.generator.ui;

import com.alibaba.druid.support.json.JSONUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.BeanUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.webcore.util.JsonUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;
import java.util.Map;

import static com.beyond.generator.utils.PropertyUtil.*;

/**
 * @author chenshipeng
 * @date 2021/02/22
 */
public class GenerateForm extends DialogWrapper {


    private Form form = new Form();

    private Project project;

    private SubmitRunnable submitRunnable;

    private JButton submitButton;

    private TestRunnable testRunnable;

    private JButton testButton;

    private JButton exportButton;

    private JButton importButton;

    public GenerateForm(@Nullable Project project) {
        super(project);
        this.project = project;
        init();
        submitRunnable = new SubmitRunnable();
        testRunnable = new TestRunnable();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return form.addItem(new InputItem("jdbcUrl", "jdbcUrl: ", getProperty("jdbcUrl", project)))
                .addItem(new InputItem("username", "username: ", getUserName(project)))
                .addItem(new PassItem("password", "password: ", getPassword(project)))
                .addItem(new InputItem("schema", "schema: ", getProperty("schema", project)))
                .addItem(new InputItem("tables", "tables: ", getProperty("tables", project)))
                .addItem(new InputItem("package", "package: ", getProperty("package", project)))
                .addItem(new InputItem("mapperPackage", "mapperPackage: ", getProperty("mapperPackage", project)))
                .addItem(new InputItem("mapperXmlPathInResource", "mapperXmlPathInResource: ", getProperty("mapperXmlPathInResource", project)))
                .addItem(new InputItem("tableExcludePrefix", "tableExcludePrefix: ", getProperty("tableExcludePrefix", project)))
                .addItem(new InputItem("tablePrefix", "tablePrefix: ", getProperty("tablePrefix", project)))
                .addItem(new InputItem("mapperSuffix", "mapperSuffix: ", getProperty("mapperSuffix", project)))
                .addItem(new InputItem("mapperXmlSuffix", "mapperXmlSuffix: ", getProperty("mapperXmlSuffix", project)))
                .addItem(new InputItem("forMyBatisPlus", "forMyBatisPlus: ", getProperty("forMyBatisPlus", project)))
                .buildPanel(13, 2);
    }


    @Override
    protected JComponent createSouthPanel() {

        //定义表单的提交按钮，放置到IDEA会话框的底部位置
        JPanel south = new JPanel();
        JButton submit = new JButton("OK");
        submit.setHorizontalAlignment(SwingConstants.CENTER); //水平居中
        submit.setVerticalAlignment(SwingConstants.CENTER); //垂直居中
        south.add(submit);

        //按钮事件绑定submitListener
        submit.addActionListener(e -> {
            submitRunnable.run();
        });
        submitButton = submit;


        JButton testConnectionButton = new JButton("TEST CONNECTION");
        testConnectionButton.setHorizontalAlignment(SwingConstants.CENTER); //水平居中
        testConnectionButton.setVerticalAlignment(SwingConstants.CENTER); //垂直居中
        south.add(testConnectionButton);

        //按钮事件绑定submitListener
        testConnectionButton.addActionListener(e -> {
            testRunnable.run();
        });
        this.testButton = testConnectionButton;

        JButton exportButton = new JButton("EXPORT");
        exportButton.setHorizontalAlignment(SwingConstants.CENTER); //水平居中
        exportButton.setVerticalAlignment(SwingConstants.CENTER); //垂直居中
        south.add(exportButton);
        exportButton.addActionListener(e -> {
            ObjectMapper objectMapper = new ObjectMapper();
            String data = null;
            try {
                data = objectMapper.writeValueAsString(getData());
                CopyableMsgDialog.show(project, data);
            } catch (JsonProcessingException jsonProcessingException) {
                jsonProcessingException.printStackTrace();
            }
        });
        this.exportButton = exportButton;

        JButton importButton = new JButton("IMPORT");
        importButton.setHorizontalAlignment(SwingConstants.CENTER); //水平居中
        importButton.setVerticalAlignment(SwingConstants.CENTER); //垂直居中
        south.add(importButton);
        importButton.addActionListener(e -> {
            CopyableMsgDialog msgd = CopyableMsgDialog.show(project, "");
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                Map<String, String> data = objectMapper.readValue(msgd.getMessage(), new TypeReference<Map<String, String>>() {
                });
                form.setData(data);
            } catch (JsonProcessingException jsonProcessingException) {
                jsonProcessingException.printStackTrace();
            }
        });
        this.importButton = importButton;

        return south;
    }

    public void show(Callback<GenerateForm> submitCallback, Callback<GenerateForm> testCallback) {
        submitRunnable.setCallback(submitCallback);
        testRunnable.setCallback(testCallback);
        show();
    }


    public Map<String, String> getData() {
        return form.getData();
    }

    public void startLoading() {
        submitButton.setText("Running....Please Wait...");
    }

    public void stopLoading() {
        submitButton.setText("Done");
    }


    private class SubmitRunnable implements Runnable {

        private Callback<GenerateForm> callback;

        @Override
        public void run() {
            saveFormProperties();
            if (callback != null) {
                callback.run(GenerateForm.this);
            }
        }

        public Callback<GenerateForm> getCallback() {
            return callback;
        }

        public void setCallback(Callback<GenerateForm> callback) {
            this.callback = callback;
        }
    }

    private String getProperty(String key, Project project) {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        if (StringUtils.isNotBlank(properties.getValue(key))) {
            return properties.getValue(key);
        } else {
            PropertiesComponent global = PropertiesComponent.getInstance();
            if (StringUtils.isNotBlank(global.getValue(key))) {
                return global.getValue(key);
            }
        }
        return null;
    }

    private void saveFormProperties() {
        saveProperty("jdbcUrl", getData().get("jdbcUrl"), true, project);
        saveProperty("schema", getData().get("schema"), true, project);
        saveProperty("tables", getData().get("tables"), true, project);
        saveProperty("package", getData().get("package"), true, project);
        saveProperty("mapperPackage", getData().get("mapperPackage"), true, project);
        saveProperty("mapperXmlPathInResource", getData().get("mapperXmlPathInResource"), true, project);
        saveProperty("tableExcludePrefix", getData().get("tableExcludePrefix"), true, project);
        saveProperty("tablePrefix", getData().get("tablePrefix"), true, project);
        saveProperty("mapperSuffix", getData().get("mapperSuffix"), true, project);
        saveProperty("mapperXmlSuffix", getData().get("mapperXmlSuffix"), true, project);
        saveProperty("forMyBatisPlus", getData().get("forMyBatisPlus"), true, project);
        saveUserNameAndPassword(getData().get("username"), getData().get("password"), true, project);
    }



    private class TestRunnable implements Runnable {

        private Callback<GenerateForm> callback;

        @Override
        public void run() {
            saveFormProperties();
            if (callback != null) {
                callback.run(GenerateForm.this);
            }
        }

        public Callback<GenerateForm> getCallback() {
            return callback;
        }

        public void setCallback(Callback<GenerateForm> callback) {
            this.callback = callback;
        }
    }


}
