package com.beyond.generator.ui;

import com.intellij.ui.components.JBPasswordField;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author chenshipeng
 * @date 2021/02/22
 */
public class PassItem implements FormItem {
    private String bindName;
    private String labelText;

    private JLabel jLabel;
    private JPasswordField jTextField;


    public PassItem(String bindName) {
        this(bindName, null);
    }

    public PassItem(String bindName, String labelText) {
        this(bindName, labelText, null);
    }

    public PassItem(String bindName, String labelText, String defaultValue) {
        this(bindName, labelText, defaultValue, null);
    }


    public PassItem(String bindName, String labelText, String defaultValue, Runnable runnable) {
        this.bindName = bindName;
        this.labelText = labelText;

        if (labelText != null){
            jLabel = new JLabel(labelText);
            jTextField = new JBPasswordField();
            jTextField.setPreferredSize(new Dimension(300, 0));
        }else{
            jTextField = new JBPasswordField();
            jTextField.setColumns(2);
            jTextField.setPreferredSize(new Dimension(500, 0));
        }

        if (StringUtils.isNotBlank(defaultValue)){
            jTextField.setText(defaultValue);
        }
        if (runnable != null){
            jTextField.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {

                }

                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER){
                        runnable.run();
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {

                }
            });
        }
    }

    @Override
    public void bind(Map<String, String> map) {
        map.put(bindName, jTextField.getText());
    }

    @Override
    public List<JComponent> getJComponents() {
        if (jLabel == null){
            return Collections.singletonList(jTextField);
        }
        return Arrays.asList(jLabel, jTextField);
    }

    public String getBindName() {
        return bindName;
    }

    @Override
    public void setValue(String value) {
        jTextField.setText(value);
    }

    public String getLabelText() {
        return labelText;
    }

    public JLabel getjLabel() {
        return jLabel;
    }

    public JTextField getjTextField() {
        return jTextField;
    }
}
