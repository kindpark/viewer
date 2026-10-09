package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)//불필요한것들은 안받는 용도의 어노테이션
public class EventJsonDto {
    private Integer id;
    private String name;
    //JSON의 전달 방식이 숫자임
    private Long startAt;
    private Long aggregateAt;
}
