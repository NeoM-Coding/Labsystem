package xyz.jasenon.lab.base.deployment;

import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SuperAdminFixtureTests {

    @Test
    void examplePasswordMatchesPrecompiledHash() throws IOException {
        Map<String, String> environment = readExampleEnvironment();

        assertTrue(BCrypt.checkpw(
                environment.get("SUPER_ADMIN_PASSWORD"),
                environment.get("SUPER_ADMIN_PASSWORD_BCRYPT")
        ));
    }

    @Test
    @EnabledIfSystemProperty(named = "super.admin.password", matches = ".+")
    void generateBootstrapPasswordHash() {
        String password = System.getProperty("super.admin.password");
        System.out.println("SUPER_ADMIN_PASSWORD_BCRYPT="
                + BCrypt.hashpw(password, BCrypt.gensalt()));
    }

    private static Map<String, String> readExampleEnvironment() throws IOException {
        Path file = Files.exists(Path.of(".env.example"))
                ? Path.of(".env.example")
                : Path.of("..", ".env.example");
        try (var lines = Files.lines(file)) {
            return lines
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.split("=", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> unquote(parts[1])));
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
