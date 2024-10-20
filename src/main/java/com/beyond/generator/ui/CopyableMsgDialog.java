package com.beyond.generator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTextField;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author chenshipeng
 * @date 2021/02/26
 */
public class CopyableMsgDialog extends DialogWrapper {

    private JBTextField jbTextField = new JBTextField();
    private String message;

    public CopyableMsgDialog(@Nullable Project project, String message) {
        super(project);
        this.message = message;
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel jPanel = new JPanel();
        jPanel.setSize(300,200);
        jbTextField.setText(message);
        jPanel.add(jbTextField);
        return jPanel;
    }

    public static CopyableMsgDialog show(@Nullable Project project,String message){
        if (StringUtils.isEmpty(message)){
            return null;
        }
        CopyableMsgDialog copyableMsgDialog = new CopyableMsgDialog(project, message);
        copyableMsgDialog.show();
        return copyableMsgDialog;
    }

    public String getMessage(){
        return jbTextField.getText();
    }

}
