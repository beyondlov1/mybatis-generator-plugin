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
public class EntityForm extends DialogWrapper {


    private Form form = new Form();

    private Project project;

    private SubmitRunnable submitRunnable;

    private JButton submitButton;


    private boolean ok;

    public EntityForm(@Nullable Project project) {
        super(project);
        this.project = project;
        init();
        submitRunnable = new SubmitRunnable();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return form.addItem(new InputItem("packageName", "packageName: ", getProperty("entity.gen.packageName", project)))
                .buildPanel(1, 2);
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

        return south;
    }

    public void show(Callback<EntityForm> submitCallback) {
        submitRunnable.setCallback(submitCallback);
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

        private Callback<EntityForm> callback;

        @Override
        public void run() {
            saveFormProperties();
            if (callback != null) {
                callback.run(EntityForm.this);
            }
        }

        public Callback<EntityForm> getCallback() {
            return callback;
        }

        public void setCallback(Callback<EntityForm> callback) {
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
        saveProperty("entity.gen.packageName", getData().get("packageName"), true, project);
    }


    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }
}
