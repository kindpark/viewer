package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="player")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    @Id
    @Column(name = "user_id")
    private Long userId;
    private String name;
    @Column(name = "user_profile_json", columnDefinition = "TEXT")
    private String rowJson;

}
