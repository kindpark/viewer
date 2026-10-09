package com.example.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="card_master")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CardMaster {
    @Id
    private Long id;

    private Integer characterId;
    private String attrType;
    private Integer rarity;
    private String assetbundleName;
    private String normalImageUrl;
    private String afterTrainingImageUrl;
}
