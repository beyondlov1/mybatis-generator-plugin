package com.beyond.generator.ui;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

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

    public GenerateForm(@Nullable Project project) {
        super(project);
        this.project = project;
        init();
        submitRunnable = new SubmitRunnable();
        testRunnable = new TestRunnable();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        return form.addItem(new InputItem("jdbcUrl", "jdbcUrl: ", properties.getValue("jdbcUrl")))
                .addItem(new InputItem("username", "username: ",  getUserName("datasource")))
                .addItem(new PassItem("password", "password: ",  getPassword("datasource")))
                .addItem(new InputItem("schema", "schema: ",  properties.getValue("schema")))
                .addItem(new InputItem("tables", "tables: ",  properties.getValue("tables")))
                .addItem(new InputItem("package", "package: ",  properties.getValue("package")))
                .addItem(new InputItem("mapperPackage", "mapperPackage: ",  properties.getValue("mapperPackage")))
                .addItem(new InputItem("mapperXmlPathInResource", "mapperXmlPathInResource: ",  properties.getValue("mapperXmlPathInResource")))
                .buildPanel(8, 2);
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

        @SuppressWarnings("MissingRecentApi")
        @Override
        public void run() {
            PropertiesComponent properties = PropertiesComponent.getInstance(project);
            properties.setValue("jdbcUrl", getData().get("jdbcUrl"));
            properties.setValue("schema", getData().get("schema"));
            properties.setValue("tables", getData().get("tables"));
            properties.setValue("package", getData().get("package"));
            properties.setValue("mapperPackage", getData().get("mapperPackage"));
            properties.setValue("mapperXmlPathInResource", getData().get("mapperXmlPathInResource"));

            saveUserNameAndPassword("datasource", getData().get("username"), getData().get("password"));


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

    @SuppressWarnings("MissingRecentApi")
    private void saveUserNameAndPassword(String key, String username, String password) {
        CredentialAttributes credentialAttributes = createCredentialAttributes(key);
        Credentials credentials = new Credentials(username, password);
        PasswordSafe.getInstance().set(credentialAttributes, credentials);
    }

    @SuppressWarnings("MissingRecentApi")
    private String getUserName(String key) {
        CredentialAttributes credentialAttributes = createCredentialAttributes(key);
        Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes);
        if (credentials != null) {
            return credentials.getUserName();
        }
        return null;
    }

    @SuppressWarnings("MissingRecentApi")
    private String getPassword(String key) {
        CredentialAttributes credentialAttributes = createCredentialAttributes(key);
        Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes);
        if (credentials != null) {
            return credentials.getPasswordAsString();
        }
        return null;
    }

    @SuppressWarnings("MissingRecentApi")
    private CredentialAttributes createCredentialAttributes(String key) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("MySystem", key));
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
