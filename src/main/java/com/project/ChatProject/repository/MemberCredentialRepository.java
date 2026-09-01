package com.project.ChatProject.repository;

import com.project.ChatProject.entity.MemberCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberCredentialRepository extends JpaRepository<MemberCredential, Long> {
}
