package com.example.demo.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CardMeta {
    private String iconUrl;
    private Integer rarity;
    private String attrType;
    private Integer cardLevel;
    private Boolean isAfterTraining;
}