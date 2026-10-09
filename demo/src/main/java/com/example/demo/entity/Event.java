package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="event")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    private Integer eventId;

    private String name;
    private LocalDateTime startAt;
    private LocalDateTime aggregateAt;
    public Event(Integer eventId) {
        this.eventId = eventId;
        this.name = "Event " + eventId;
    }
    public void updateInfo(String name, LocalDateTime startAt, LocalDateTime aggregateAt) {
        this.name = name;
        this.startAt = startAt;
        this.aggregateAt = aggregateAt;
    }
}
