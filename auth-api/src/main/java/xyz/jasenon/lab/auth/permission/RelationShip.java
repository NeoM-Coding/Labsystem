package xyz.jasenon.lab.auth.permission;

public interface RelationShip extends Permission {

    enum App implements RelationShip {
        super_admin,
        user_manager,
        user_viewer,

        edu_semester_manager,
        edu_semester_viewer,

        edu_timetable_manager,
        edu_timetable_viewer,

        laboratory_manager,

        smart_manager,
        smart_viewer,
        smart_keeper,

        data_analyst;

        @Override
        public String str() {
            return name();
        }
    }

    enum Laboratory implements RelationShip {
        viewer;

        @Override
        public String str() {
            return name();
        }
    }
}
