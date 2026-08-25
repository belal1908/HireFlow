package com.hireflow.application.repository;

import com.hireflow.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    List<Application> findByCandidateId(Long candidateId);

    boolean existsByCandidateIdAndJobPostingId(Long candidateId, Long jobPostingId);
}
