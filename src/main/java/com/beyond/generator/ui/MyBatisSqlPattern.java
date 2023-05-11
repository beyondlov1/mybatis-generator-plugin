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
public class MyBatisSqlPattern extends DialogWrapper {


    private Form form = new Form();

    private Project project;

    private SubmitRunnable submitRunnable;

    private JButton submitButton;

    private JButton testButton;

    public MyBatisSqlPattern(@Nullable Project project) {
        super(project);
        this.project = project;
        init();
        submitRunnable = new SubmitRunnable();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return form.addItem(new TextAreaItem("patterns", "patterns: "))
                .addItem(new InputItem("annotation", "annotationFullName: "))
                .buildPanel(6, 2);
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

    public void show(Callback<MyBatisSqlPattern> submitCallback) {
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

        private Callback<MyBatisSqlPattern> callback;

        @Override
        public void run() {
            if (callback != null) {
                callback.run(MyBatisSqlPattern.this);
            }
        }

        public Callback<MyBatisSqlPattern> getCallback() {
            return callback;
        }

        public void setCallback(Callback<MyBatisSqlPattern> callback) {
            this.callback = callback;
        }
    }

}
