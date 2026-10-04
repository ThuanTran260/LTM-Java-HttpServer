package service;
import model.Student;
import java.util.*;
public class StudentService {
    private final List<Student> list = new ArrayList<>();
    private int nextId = 1;
    public StudentService() {
        add("An", 20);
        add("Binh", 21);
    }
    public synchronized List<Student> list() { return new ArrayList<>(list); }
    public synchronized Student get(int id) {
        for (Student s : list) if (s.id == id) return s;
        return null;
    }
    public synchronized Student add(String name, int age) {
        Student s = new Student(nextId++, name, age);
        list.add(s);
        return s;
    }
    public synchronized boolean update(int id, String name, int age) {
        Student s = get(id);
        if (s == null) return false;
        s.name = name; s.age = age;
        return true;
    }
    public synchronized boolean delete(int id) {
        return list.removeIf(s -> s.id == id);
    }
    public synchronized String listJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i).toJson());
        }
        return sb.append("]").toString();
    }
}
