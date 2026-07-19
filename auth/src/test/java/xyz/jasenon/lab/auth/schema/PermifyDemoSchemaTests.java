package xyz.jasenon.lab.auth.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermifyDemoSchemaTests {

    @Test
    void demoSchemaDefinesDeviceOperatePermission() throws IOException {
        String schema = Files.readString(demoSchema());

        assertTrue(schema.contains("entity user {}"));
        assertTrue(schema.contains("entity device"));
        assertTrue(schema.contains("relation operator @user"));
        assertTrue(schema.contains("relation admin @user"));
        assertTrue(schema.contains("action operate = operator or admin"));
    }

    @Test
    void labSystemSchemaUsesPermissionsForDerivedCapabilities() throws IOException {
        String schema = Files.readString(schemaPath("lab-system.perm"));

        assertTrue(schema.contains("permission is_super_admin = super_admin"));
        assertTrue(schema.contains("permission app_permission_admin = super_admin or app_authz_admin"));
        assertTrue(schema.contains("action grant_app_permission = app_permission_admin"));
        assertTrue(schema.contains("permission menu_account = is_super_admin or account_manager"));
        assertTrue(schema.contains("permission account_view = is_super_admin or menu_account"));
        assertTrue(schema.contains("permission view_scope = viewer or manager or authz_admin or creator or app.is_super_admin"));
        assertTrue(schema.contains("permission device_manage = view_scope and app.smart_control_manage"));
        assertTrue(schema.contains("permission access_control_manage = lab.access_control_manage"));
        assertTrue(schema.contains("permission smart_control_manage = is_super_admin or smart_control_manager"));
        assertFalse(schema.contains("action menu_"));
        assertFalse(schema.contains("account_create = app_permission_admin"));
        assertFalse(schema.contains("smart_control_manage = app_permission_admin"));

        assertTrue(schema.contains("action account_create = is_super_admin or account_manager"));
        assertTrue(schema.contains("action lab_update = is_super_admin or base_manager"));
        assertTrue(schema.contains("action grant_viewer = grant_scope_permission"));
        assertTrue(schema.contains("action operate = manage"));
        assertTrue(schema.contains("action enable_disable = lab.device_enable_disable"));
    }

    @Test
    void labSystemV2SchemaDefinesFlattenedApplicationRolesAndLaboratoryScope() throws IOException {
        String schema = Files.readString(schemaPath("lab-system-v2.perm"));

        assertTrue(schema.contains("entity app"));
        assertTrue(schema.contains("relation super_admin @user"));
        assertTrue(schema.contains("relation user_manager @user"));
        assertTrue(schema.contains("relation laboratory_manager @user"));
        assertTrue(schema.contains("action create_user = user_manager or super_admin"));
        assertTrue(schema.contains("action view_timetable = edu_timetable_manager or edu_timetable_viewer or super_admin"));
        assertTrue(schema.contains("action manage_laboratory = laboratory_manager or super_admin"));
        assertTrue(schema.contains("entity laboratory"));
        assertTrue(schema.contains("relation viewer @user"));
        assertTrue(schema.contains("permission can_view = app.super_admin or viewer"));
        assertFalse(schema.contains("view_timetable = edu_timetable_manager or edu_semester_viewer"));
    }

    private Path demoSchema() {
        return schemaPath("demo.perm");
    }

    private Path schemaPath(String fileName) {
        Path modulePath = Path.of("schema", fileName);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("auth", "schema", fileName);
    }
}
