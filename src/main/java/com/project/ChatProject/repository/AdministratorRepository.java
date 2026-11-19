package com.project.ChatProject.repository;

import com.project.ChatProject.entity.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministratorRepository
        extends JpaRepository<Administrator, Long> {
}
