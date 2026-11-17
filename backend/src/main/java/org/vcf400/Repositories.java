package org.vcf400;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.vcf400.Models.*;

interface Exhibits extends JpaRepository<Exhibit, String> {
    List<Exhibit> findAllByOrderByEXHUSRPRFAsc();
}
interface Votes extends JpaRepository<Vote, Integer> {}
interface Comments extends JpaRepository<GuestComment, Integer> {
    Optional<GuestComment> findFirstByCMTIDGreaterThanEqualOrderByCMTIDAsc(int id);
    Optional<GuestComment> findFirstByOrderByCMTIDDesc();
}
interface Awards extends JpaRepository<Award, Integer> {}
interface Settings extends JpaRepository<Setting, String> {}
interface Officers extends JpaRepository<SecurityOfficer, String> {}
interface Pages extends JpaRepository<LearnPage, PageKey> {
    List<LearnPage> findByCOLLECTIONOrderByPAGENBRAsc(String collection);
    Optional<LearnPage> findFirstByCOLLECTIONOrderByPAGENBRAsc(String collection);
}
