package org.jeecg.modules.demo.face.util;

import lombok.Data;

/**
 * @author wggg
 * @date 2025/11/28 9:28
 */
@Data
public  class GenderAgeResult {

    private String gender;           // "Male" 或 "Female"
    private float genderConfidence;  // 性别置信度
    private int age;                 // 预测年龄

    public GenderAgeResult(String gender, float genderConfidence, int age) {
        this.gender = gender;
        this.genderConfidence = genderConfidence;
        this.age = age;
    }
}
