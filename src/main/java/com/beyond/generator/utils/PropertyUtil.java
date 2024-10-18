package com.beyond.generator.utils;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang3.StringUtils;

/**
 * @author chenshipeng
 * @date 2022/11/23
 */
public class PropertyUtil {
    private static final String GLOBAL_SENSITIVE_KEY = "mybatis-generate-plugin-jdbc-sk";

    public static String getProperty(String key, Project project) {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        if (org.apache.commons.lang3.StringUtils.isNotBlank(properties.getValue(key))) {
            return StringUtils.trim(properties.getValue(key));
        } else {
            PropertiesComponent global = PropertiesComponent.getInstance();
            if (org.apache.commons.lang3.StringUtils.isNotBlank(global.getValue(key))) {
                return StringUtils.trim(global.getValue(key));
            }
        }
        return null;
    }

    @SuppressWarnings("MissingRecentApi")
    public static String getUserName(Project project) {
        String sensitiveKey = project.getBasePath();
        CredentialAttributes credentialAttributes = createCredentialAttributes(sensitiveKey);
        Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes);
        if (credentials != null) {
            return credentials.getUserName();
        }
        CredentialAttributes globalCredentialAttributes = createCredentialAttributes(GLOBAL_SENSITIVE_KEY);
        Credentials globalCredentials = PasswordSafe.getInstance().get(globalCredentialAttributes);
        if (globalCredentials != null) {
            return globalCredentials.getUserName();
        }
        return null;
    }

    @SuppressWarnings("MissingRecentApi")
    public static String getPassword(Project project) {
        String sensitiveKey = project.getBasePath();
        CredentialAttributes credentialAttributes = createCredentialAttributes(sensitiveKey);
        Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes);
        if (credentials != null) {
            return credentials.getPasswordAsString();
        }
        CredentialAttributes globalCredentialAttributes = createCredentialAttributes(GLOBAL_SENSITIVE_KEY);
        Credentials globalCredentials = PasswordSafe.getInstance().get(globalCredentialAttributes);
        if (globalCredentials != null) {
            return globalCredentials.getPasswordAsString();
        }
        return null;
    }


    @SuppressWarnings("MissingRecentApi")
    public static CredentialAttributes createCredentialAttributes(String key) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("mybatis-generator-plugin", key));
    }


    public static void saveProperty(String key, String value, boolean global, Project project) {
        if (global) {
            PropertiesComponent globalProperties = PropertiesComponent.getInstance();
            globalProperties.setValue(key, StringUtils.trim(value));
            return;
        }
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        properties.setValue(key, StringUtils.trim(value));
    }

    @SuppressWarnings("MissingRecentApi")
    public static void saveUserNameAndPassword(String username, String password, boolean global, Project project) {

        String sensitiveKey;
        if (global){
            sensitiveKey = GLOBAL_SENSITIVE_KEY;
        }else {
            sensitiveKey = project.getBasePath();
        }
        CredentialAttributes credentialAttributes = createCredentialAttributes(sensitiveKey);
        Credentials credentials = new Credentials(username, password);
        PasswordSafe.getInstance().set(credentialAttributes, credentials);
    }
}
