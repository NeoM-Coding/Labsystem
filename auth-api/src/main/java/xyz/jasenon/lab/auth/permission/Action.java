package xyz.jasenon.lab.auth.permission;

public interface Action extends Permission {

    enum App implements Action {
        grant_app_permission,
        grant_lab_permission,
        account_view,
        account_create,
        account_update,
        account_delete,
        lab_create,
        lab_update,
        lab_delete,
        edu_semester_view,
        edu_semester_manage,
        edu_timetable_view,
        edu_timetable_schedule,
        smart_control_view,
        smart_control_manage,
        smart_control_enable_disable,
        access_control_manage,
        circuitbreak_control_manage,
        light_control_manage,
        aircondition_control_manage,
        data_analysis_edu_view,
        data_analysis_aircondition_view,
        data_analysis_circuitbreak_view;

        @Override
        public String str() {
            return name();
        }
    }

    enum Laboratory implements Action {
        view,
        update,
        delete;

        @Override
        public String str() {
            return name();
        }
    }
}
