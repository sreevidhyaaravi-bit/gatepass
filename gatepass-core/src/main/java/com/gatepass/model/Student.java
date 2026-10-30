package com.gatepass.model;

/** A hostel student (INHERITANCE: Student IS-A User). Uses the website. */
public class Student extends User {

    private String className;
    private int semester;
    private String hostelRoll;
    private Parent parent; // null when the student is loaded only for gate/pass listings

    /** Full constructor: used when the complete profile (with parent) is loaded. */
    public Student(String collegeId, String name, String email,
                   String className, int semester, String hostelRoll, Parent parent) {
        super(collegeId, name, email); // 'super' runs the User constructor
        this.className = className;
        this.semester = semester;
        this.hostelRoll = hostelRoll;
        this.parent = parent;
    }

    /**
     * Short constructor (CONSTRUCTOR OVERLOADING): used inside pass listings, where parent
     * details are intentionally NOT loaded.
     */
    public Student(String collegeId, String name, String className, int semester, String hostelRoll) {
        this(collegeId, name, null, className, semester, hostelRoll, null); // 'this' calls the full one
    }

    /** For students the login ID and the college ID are the same value. */
    public String getCollegeId() {
        return getUserId();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public String getHostelRoll() {
        return hostelRoll;
    }

    public void setHostelRoll(String hostelRoll) {
        this.hostelRoll = hostelRoll;
    }

    public Parent getParent() {
        return parent;
    }

    public void setParent(Parent parent) {
        this.parent = parent;
    }

    @Override
    public Role getRole() {
        return Role.STUDENT;
    }

    @Override
    public String showDashboard() {
        return "student-dashboard";
    }
}
