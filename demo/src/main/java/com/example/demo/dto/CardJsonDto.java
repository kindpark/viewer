package com.example.demo.dto; // 기존 파일의 package 줄에 맞추세요

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardJsonDto {
    private Long id;
    private Integer characterId;

    // sekai-master-db-diff의 cards.json은 속성 키가 "attr" 입니다 (attrType 아님)
    @JsonAlias({"attr", "attrType"})
    private String attrType;

    // 레어리티도 "cardRarityType": "rarity_4" 형태로 내려옵니다
    private String cardRarityType;
    private Integer rarity;

    private String assetbundleName;

    /** rarity 숫자가 없으면 cardRarityType("rarity_4")에서 계산 */
    public Integer resolveRarity() {
        if (rarity != null) return rarity;
        if (cardRarityType == null) return null;
        if ("rarity_birthday".equals(cardRarityType)) return 4;
        try {
            return Integer.parseInt(cardRarityType.replace("rarity_", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}