package org.vcf400;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class Inserts {
    private final EntityManager entityManager;
    public Inserts(EntityManager entityManager) { this.entityManager = entityManager; }

    @Transactional
    public void persist(Object row) {
        entityManager.persist(row);
        entityManager.flush();
    }
}
