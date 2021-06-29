package com.beyond.generator.ui;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author chenshipeng
 * @date 2021/02/22
 */
public class GenerateForm extends DialogWrapper {


    private static final String GLOBAL_SENSITIVE_KEY = "mybatis-generate-plugin-jdbc-sk";

    private Form form = new Form();

    private Project project;

    private SubmitRunnable submitRunnable;

    private JButton submitButton;

    private TestRunnable testRunnable;

    private JButton testButton;

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
                .addItem(new InputItem("username", "username: ", getUserName()))
                .addItem(new PassItem("password", "password: ", getPassword()))
                .addItem(new InputItem("schema", "schema: ", getProperty("schema", project)))
                .addItem(new InputItem("tables", "tables: ", getProperty("tables", project)))
                .addItem(new InputItem("package", "package: ", getProperty("package", project)))
                .addItem(new InputItem("mapperPackage", "mapperPackage: ", getProperty("mapperPackage", project)))
                .addItem(new InputItem("mapperXmlPathInResource", "mapperXmlPathInResource: ", getProperty("mapperXmlPathInResource", project)))
                .addItem(new InputItem("tableExcludePrefix", "tableExcludePrefix: ", getProperty("tableExcludePrefix", project)))
                .addItem(new InputItem("mapperSuffix", "mapperSuffix: ", getProperty("mapperSuffix", project)))
                .addItem(new InputItem("mapperXmlSuffix", "mapperXmlSuffix: ", getProperty("mapperXmlSuffix", project)))
                .buildPanel(11, 2);
    }


    @Override
    protected JComponent createSouthPanel() {

        //定义表单的提交按钮，放置到IDEA会话框的底部位置
        JPanel south = new JPanel();
        JButton submit = new JButton("确定");
        submit.setHorizontalAlignment(SwingConstants.CENTER); //水平居中
        submit.setVerticalAlignment(SwingConstants.CENTER); //垂直居中
        south.add(submit);

        //按钮事件绑定submitListener
        submit.addActionListener(e -> {
            submitRunnable.run();
        });
        submitButton = submit;


        JButton testConnectionButton = new JButton("测试连接");
        testConnectionButton.setHorizontalAlignment(SwingConstants.CENTER); //水平居中
        testConnectionButton.setVerticalAlignment(SwingConstants.CENTER); //垂直居中
        south.add(testConnectionButton);

        //按钮事件绑定submitListener
        testConnectionButton.addActionListener(e -> {
            testRunnable.run();
        });
        this.testButton = testConnectionButton;
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
            saveProperty("jdbcUrl", getData().get("jdbcUrl"), true, project);
            saveProperty("schema", getData().get("schema"), true, project);
            saveProperty("tables", getData().get("tables"), true, project);
            saveProperty("package", getData().get("package"), true, project);
            saveProperty("mapperPackage", getData().get("mapperPackage"), true, project);
            saveProperty("mapperXmlPathInResource", getData().get("mapperXmlPathInResource"), true, project);
            saveProperty("tableExcludePrefix", getData().get("tableExcludePrefix"), true, project);
            saveProperty("mapperSuffix", getData().get("mapperSuffix"), true, project);
            saveProperty("mapperXmlSuffix", getData().get("mapperXmlSuffix"), true, project);
            saveUserNameAndPassword(getData().get("username"), getData().get("password"), true);
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

    private void saveProperty(String key, String value, boolean global, Project project) {
        if (global) {
            PropertiesComponent globalProperties = PropertiesComponent.getInstance();
            globalProperties.setValue(key, value);
            return;
        }
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        properties.setValue(key, value);
    }

    @SuppressWarnings("MissingRecentApi")
    private void saveUserNameAndPassword(String username, String password, boolean global) {

        String sensitiveKey;
        if (global){
            sensitiveKey = GLOBAL_SENSITIVE_KEY;
        }else {
            sensitiveKey = project.getBasePath();
        }
        CredentialAttributes credentialAttributes = createCredentialAttributes(sensitiveKey);
        Credentials credentials = new Credentials(username, password);
        PasswordSafe.getInstance().set(credentialAttributes, credentials);
    }

    @SuppressWarnings("MissingRecentApi")
    private String getUserName() {
        String sensitiveKey = project.getBasePath();
        CredentialAttributes credentialAttributes = createCredentialAttributes(sensitiveKey);
        Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes);
        if (credentials != null) {
            return credentials.getUserName();
        }
        CredentialAttributes globalCredentialAttributes = createCredentialAttributes(GLOBAL_SENSITIVE_KEY);
        Credentials globalCredentials = PasswordSafe.getInstance().get(globalCredentialAttributes);
        if (globalCredentials != null) {
            return globalCredentials.getUserName();
        }
        return null;
    }

    @SuppressWarnings("MissingRecentApi")
    private String getPassword() {
        String sensitiveKey = project.getBasePath();
        CredentialAttributes credentialAttributes = createCredentialAttributes(sensitiveKey);
        Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes);
        if (credentials != null) {
            return credentials.getPasswordAsString();
        }
        CredentialAttributes globalCredentialAttributes = createCredentialAttributes(GLOBAL_SENSITIVE_KEY);
        Credentials globalCredentials = PasswordSafe.getInstance().get(globalCredentialAttributes);
        if (globalCredentials != null) {
            return globalCredentials.getPasswordAsString();
        }
        return null;
    }

    @SuppressWarnings("MissingRecentApi")
    private CredentialAttributes createCredentialAttributes(String key) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("mybatis-generator-plugin", key));
    }


    private class TestRunnable implements Runnable {

        private Callback<GenerateForm> callback;

        @Override
        public void run() {
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
