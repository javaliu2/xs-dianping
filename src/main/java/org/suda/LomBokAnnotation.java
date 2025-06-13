package org.suda;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

public class LomBokAnnotation {
    public static void main(String[] args) {
        Student s = new Student();
        s.setAge(18).setName("xs");

    }
}

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
class Student {
    private String name;
    private int age;
    private String motto;
}