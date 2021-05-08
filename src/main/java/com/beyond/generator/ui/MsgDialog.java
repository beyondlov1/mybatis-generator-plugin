package com.beyond.generator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author chenshipeng
 * @date 2021/02/26
 */
public class MsgDialog extends DialogWrapper {

    private JBLabel jbLabel = new JBLabel();
    private String errorMsg;

    public MsgDialog(@Nullable Project project, String errorMsg) {
        super(project);
        this.errorMsg = errorMsg;
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel jPanel = new JPanel();
        jPanel.setSize(300,200);
        jbLabel.setText(errorMsg);
        jPanel.add(jbLabel);
        return jPanel;
    }
}
