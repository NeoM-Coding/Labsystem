package xyz.jasenon.lab.auth.permission;

public interface RelationShip extends Permission {

    enum App implements RelationShip {
        super_admin,
        app_authz_admin,
        account_manager,
        base_manager,
        edu_semester_manager,
        edu_timetable_scheduler,
        edu_timetable_viewer,
        smart_control_manager,
        smart_control_enable_disable_manager,
        smart_control_viewer,
        access_control_controller,
        circuitbreak_control_controller,
        light_control_controller,
        aircondition_control_controller,
        data_analysis_edu,
        data_analysis_aircondition,
        data_analysis_circuitbreak;

        @Override
        public String str() {
            return name();
        }
    }

    enum Laboratory implements RelationShip {
        creator,
        viewer,
        manager;

        @Override
        public String str() {
            return name();
        }
    }
}
