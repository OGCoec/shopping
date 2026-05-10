package com.example.ShoppingSystem.admin.mapper;

import com.example.ShoppingSystem.admin.model.AdminAccount;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AdminYamlConfigMapper {

    private static final String ROOT_KEY = "admin";

    public AdminAccount read(Path path) {
        ensureConfigFile(path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            Map<String, Object> root = asStringObjectMap(loaded);
            Map<String, Object> admin = asStringObjectMap(root.get(ROOT_KEY));
            AdminAccount account = AdminAccount.empty();
            account.setInitialized(readBoolean(admin.get("initialized")));
            account.setUsername(readString(admin.get("username")));
            account.setEmail(readString(admin.get("email")));
            account.setPhone(readString(admin.get("phone")));
            account.setPasswordHash(readString(admin.get("passwordHash")));
            account.setUpdatedAt(readString(admin.get("updatedAt")));
            return account;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read admin yaml config.", ex);
        }
    }

    public void write(Path path, AdminAccount account) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> admin = new LinkedHashMap<>();
            admin.put("initialized", account != null && account.isInitialized());
            admin.put("username", readString(account == null ? null : account.getUsername()));
            admin.put("email", readString(account == null ? null : account.getEmail()));
            admin.put("phone", readString(account == null ? null : account.getPhone()));
            admin.put("passwordHash", readString(account == null ? null : account.getPasswordHash()));
            admin.put("updatedAt", readString(account == null ? null : account.getUpdatedAt()));

            Map<String, Object> root = new LinkedHashMap<>();
            root.put(ROOT_KEY, admin);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                yaml.dump(root, writer);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write admin yaml config.", ex);
        }
    }

    private void ensureConfigFile(Path path) {
        if (Files.exists(path)) {
            return;
        }
        write(path, AdminAccount.empty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
