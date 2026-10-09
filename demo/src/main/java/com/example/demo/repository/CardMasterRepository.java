package com.example.demo.repository;

import com.example.demo.entity.CardMaster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardMasterRepository extends JpaRepository<CardMaster, Long> {
}