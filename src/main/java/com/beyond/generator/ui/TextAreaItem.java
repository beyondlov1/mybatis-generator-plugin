package com.beyond.generator.ui;

import com.intellij.ui.components.JBTextArea;
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
public class TextAreaItem implements FormItem {
    private String bindName;
    private String labelText;

    private JLabel jLabel;
    private JTextArea jTextArea;


    public TextAreaItem(String bindName) {
        this(bindName, null);
    }

    public TextAreaItem(String bindName, String labelText) {
        this(bindName, labelText, null);
    }

    public TextAreaItem(String bindName, String labelText, String defaultValue) {
        this(bindName, labelText, defaultValue, null);
    }


    public TextAreaItem(String bindName, String labelText, String defaultValue, Runnable runnable) {
        this.bindName = bindName;
        this.labelText = labelText;

        if (labelText != null){
            jLabel = new JLabel(labelText);
            jTextArea = new JBTextArea();
            jTextArea.setPreferredSize(new Dimension(300, 0));
        }else{
            jTextArea = new JBTextArea();
            jTextArea.setColumns(2);
            jTextArea.setPreferredSize(new Dimension(500, 0));
        }

        if (StringUtils.isNotBlank(defaultValue)){
            jTextArea.setText(defaultValue);
        }
        if (runnable != null){
            jTextArea.addKeyListener(new KeyListener() {
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
        map.put(bindName, jTextArea.getText());
    }

    @Override
    public List<JComponent> getJComponents() {
        if (jLabel == null){
            return Collections.singletonList(jTextArea);
        }
        return Arrays.asList(jLabel, jTextArea);
    }

    public String getBindName() {
        return bindName;
    }

    public String getLabelText() {
        return labelText;
    }

    public JLabel getjLabel() {
        return jLabel;
    }

    public JTextArea getjTextArea() {
        return jTextArea;
    }
}
