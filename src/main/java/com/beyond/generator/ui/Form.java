package com.beyond.generator.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author chenshipeng
 * @date 2021/02/22
 */
public class Form {
    private List<FormItem> itemList = new ArrayList<>();

    public Form addItem(FormItem item){
        itemList.add(item);
        return this;
    }

    public JPanel buildPanel(int rows, int cols){
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new GridLayout(rows, cols));
        for (FormItem formItem : itemList) {
            for (JComponent jComponent : formItem.getJComponents() ){
                jPanel.add(jComponent);
            }
        }
        return jPanel;
    }


    public JPanel buildPanel(int rows, int cols, int prefWidth, int prefHight){
        JPanel jPanel = buildPanel(rows, cols);
        jPanel.setPreferredSize(new Dimension(prefWidth, prefHight));
        return jPanel;
    }

    public Map<String,String> getData() {
        Map<String, String> data = new HashMap<>();
        for (FormItem formItem : itemList) {
            formItem.bind(data);
        }
        return data;
    }
}
