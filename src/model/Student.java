package model;
public class Student {
    public volatile int id;
    public volatile String name;
    public volatile int age;
    public Student(int id, String name, int age) {
        this.id = id; this.name = name; this.age = age;
    }
    public String toJson() {
        return "{\"id\":" + id + ",\"name\":\"" + escapeJson(name) + "\",\"age\":" + age + "}";
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
