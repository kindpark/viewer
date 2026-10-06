package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="event")
@Getter
@Setter
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
}
