package xyz.jasenon.lab.auth.permission;

public interface Action extends Permission {

    enum App implements Action {
        create_user,
        edit_user,
        delete_user,
        list_user,

        manage_semester,
        list_semester,

        manage_timetable,
        view_timetable,

        manage_laboratory,

        manage_smart_strategy,
        change_smart_strategy_status,
        list_smart_strategies,

        edu_data_analysis,
        air_condition_data_analysis,
        circuit_break_data_analysis;

        @Override
        public String str() {
            return name();
        }
    }

    enum Laboratory implements Action {
        can_view;

        @Override
        public String str() {
            return name();
        }
    }


}
