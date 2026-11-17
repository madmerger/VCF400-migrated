package org.vcf400;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.vcf400.Models.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"vcf.seed-admpswrd=", "spring.datasource.url=jdbc:h2:mem:review;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class ReviewRegressionTest {
    private static final List<String> ENTITIES = List.of("Vote", "GuestComment", "LearnPage", "Exhibit", "Award", "Setting", "SecurityOfficer");
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transaction;
    @Autowired BaselineSeeder seeder;

    @BeforeEach void clear() {
        transaction.executeWithoutResult(status -> {
            for (String entity : ENTITIES) em.createQuery("delete from " + entity).executeUpdate();
            em.clear();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vote", "GuestComment", "LearnPage", "Exhibit", "Award", "Setting", "SecurityOfficer"})
    void startupDoesNotSeedIfAnyTableHasRows(String existingEntity) throws Exception {
        Object row = switch (existingEntity) {
            case "Vote" -> { Vote vote = new Vote(); vote.BADGENBR = 1001; yield vote; }
            case "GuestComment" -> { GuestComment comment = new GuestComment(); comment.CMTID = 1; yield comment; }
            case "LearnPage" -> { LearnPage page = new LearnPage(); page.COLLECTION = "global"; page.PAGENBR = 1; yield page; }
            case "Exhibit" -> { Exhibit exhibit = new Exhibit(); exhibit.EXHUSRPRF = "EXH001"; yield exhibit; }
            case "Award" -> { Award award = new Award(); award.AWARDID = 1; yield award; }
            case "Setting" -> { Setting setting = new Setting(); setting.SETTING = "ALWVOTE"; yield setting; }
            case "SecurityOfficer" -> { SecurityOfficer officer = new SecurityOfficer(); officer.USERPROF = "EXISTING"; yield officer; }
            default -> throw new IllegalArgumentException();
        };
        transaction.executeWithoutResult(status -> em.persist(row));
        seeder.run(new DefaultApplicationArguments());
        for (String entity : ENTITIES) {
            long count = transaction.execute(status -> em.createQuery("select count(row) from " + entity + " row", Long.class).getSingleResult());
            assertEquals(entity.equals(existingEntity) ? 1 : 0, count, entity);
        }
    }

    @Test void completelyEmptyDatabaseSeedsOnlyOnce() throws Exception {
        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());
        long comments = transaction.execute(status -> em.createQuery("select count(c) from GuestComment c", Long.class).getSingleResult());
        assertEquals(3, comments);
    }
}
