package com.piuda.wilson.domain.elderly.repository;

import com.piuda.wilson.domain.elderly.entity.SubjectErasure;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectErasureRepository extends JpaRepository<SubjectErasure, Long> {

    /** 먼저 요청된 것부터 한 번에 100건까지 보낸다. */
    List<SubjectErasure> findTop100ByOrderByIdAsc();
}
