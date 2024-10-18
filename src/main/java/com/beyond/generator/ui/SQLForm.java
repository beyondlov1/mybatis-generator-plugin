package com.beyond.generator.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

import static com.beyond.generator.utils.PropertyUtil.saveProperty;

/**
 * @author chenshipeng
 * @date 2021/02/22
 */
public class SQLForm extends DialogWrapper {


    private Form form = new Form();

    private Project project;

    private SubmitRunnable submitRunnable;

    private JButton submitButton;


    private boolean ok;

    public SQLForm(@Nullable Project project) {
        super(project);
        this.project = project;
        submitRunnable = new SubmitRunnable();
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return form.addItem(new TextAreaItem("sql", null, "", submitRunnable))
                .buildPanel(1, 2, 500, 300);
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

    public void show(Callback<SQLForm> submitCallback) {
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

        private Callback<SQLForm> callback;

        @Override
        public void run() {
            if (callback != null) {
                callback.run(SQLForm.this);
            }
        }

        public Callback<SQLForm> getCallback() {
            return callback;
        }

        public void setCallback(Callback<SQLForm> callback) {
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
