package com.beyond.generator.ui;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public interface FormItem {
    void bind(Map<String,String> map);
    List<JComponent> getJComponents();
}
