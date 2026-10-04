package model;
public class Student {
    public int id;
    public String name;
    public int age;
    public Student(int id, String name, int age) {
        this.id = id; this.name = name; this.age = age;
    }
    public String toJson() {
        String n = name == null ? "" : name.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":" + id + ",\"name\":\"" + n + "\",\"age\":" + age + "}";
    }
}
