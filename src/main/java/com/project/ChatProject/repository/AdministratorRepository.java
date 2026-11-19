package com.project.ChatProject.repository;

import com.project.ChatProject.entity.Administrators;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministratorRepository
        extends JpaRepository<Administrators, Long> {
}
