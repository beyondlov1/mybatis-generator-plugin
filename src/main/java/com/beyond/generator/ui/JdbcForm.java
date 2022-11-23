package com.beyond.generator.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

import static com.beyond.generator.utils.PropertyUtil.*;

/**
 * @author chenshipeng
 * @date 2021/02/22
 */
public class JdbcForm extends DialogWrapper {


    private Form form = new Form();

    private Project project;

    private SubmitRunnable submitRunnable;

    private JButton submitButton;

    private TestRunnable testRunnable;

    private JButton testButton;

    private boolean ok;

    public JdbcForm(@Nullable Project project) {
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
                .buildPanel(3, 2);
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
        return south;
    }

    public void show(Callback<JdbcForm> submitCallback, Callback<JdbcForm> testCallback) {
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

        private Callback<JdbcForm> callback;

        @Override
        public void run() {
            saveFormProperties();
            if (callback != null) {
                callback.run(JdbcForm.this);
            }
        }

        public Callback<JdbcForm> getCallback() {
            return callback;
        }

        public void setCallback(Callback<JdbcForm> callback) {
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
        saveUserNameAndPassword(getData().get("username"), getData().get("password"), true, project);
    }



    private class TestRunnable implements Runnable {

        private Callback<JdbcForm> callback;

        @Override
        public void run() {
            saveFormProperties();
            if (callback != null) {
                callback.run(JdbcForm.this);
            }
        }

        public Callback<JdbcForm> getCallback() {
            return callback;
        }

        public void setCallback(Callback<JdbcForm> callback) {
            this.callback = callback;
        }
    }


    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }
}
