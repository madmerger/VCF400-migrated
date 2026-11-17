package org.vcf400;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

public final class Models {
    private Models() {}

    @Entity(name = "Exhibit") @Table(name = "EXHBDB")
    public static class Exhibit {
        @Id @Column(name = "EXHUSRPRF", length = 9) public String EXHUSRPRF;
        @Column(name = "EXHBDBID") public int EXHBDBID;
        @Column(name = "EXHBITOR", length = 20) public String EXHBITOR;
        @Column(name = "EXHBCITY", length = 20) public String EXHBCITY;
        @Column(name = "EXHBSTATE", length = 2) public String EXHBSTATE;
        @Column(name = "EXHBTITLE", length = 50) public String EXHBTITLE;
        @Column(name = "EXHBDESC", length = 1000) public String EXHBDESC;
        @Column(name = "ELIGIBLE") public int ELIGIBLE;
        @Column(name = "ENLRN400") public int ENLRN400;
    }

    @Entity(name = "Vote") @Table(name = "VOTINGDB")
    public static class Vote {
        @Id @Column(name = "BADGENBR") public int BADGENBR;
        @Column(name = "AWARDNBR") public int AWARDNBR;
        @Column(name = "EXHBNBR", length = 9) public String EXHBNBR;
    }

    @Entity(name = "GuestComment") @Table(name = "GUESTBKDB")
    public static class GuestComment {
        @Id @Column(name = "CMTID") public int CMTID;
        @Column(name = "VISIBLE", length = 1) public String VISIBLE;
        @Column(name = "EXHBID", length = 9) public String EXHBID;
        @Column(name = "GUESTNAME", length = 20) public String GUESTNAME;
        @Column(name = "GUESTCMT", length = 200) public String GUESTCMT;
    }

    @Entity(name = "Award") @Table(name = "AWARDDB")
    public static class Award {
        @Id @Column(name = "AWARDID") public int AWARDID;
        @Column(name = "AWARDTITLE", length = 100) public String AWARDTITLE;
        @Column(name = "AWARDDESC", length = 1000) public String AWARDDESC;
    }

    @Entity(name = "Setting") @Table(name = "SETTINGS")
    public static class Setting {
        @Id @Column(name = "SETTING", length = 9) public String SETTING;
        @Column(name = "VALUE", length = 512) public String VALUE;
    }

    @Entity(name = "SecurityOfficer") @Table(name = "SECOFRS")
    public static class SecurityOfficer {
        @Id @Column(name = "USERPROF", length = 9) public String USERPROF;
    }

    public static class PageKey implements Serializable {
        public String COLLECTION;
        public int PAGENBR;
        public PageKey() {}
        public PageKey(String collection, int page) { COLLECTION = collection; PAGENBR = page; }
        @Override public boolean equals(Object other) {
            return other instanceof PageKey key && PAGENBR == key.PAGENBR && Objects.equals(COLLECTION, key.COLLECTION);
        }
        @Override public int hashCode() { return Objects.hash(COLLECTION, PAGENBR); }
    }

    @Entity(name = "LearnPage") @Table(name = "LRN400STR") @IdClass(PageKey.class)
    public static class LearnPage {
        @Id @Column(name = "COLLECTION", length = 9) public String COLLECTION;
        @Id @Column(name = "PAGENBR") public int PAGENBR;
        @Column(name = "CONTENT", length = 1500) public String CONTENT;
        @Column(name = "EXTRA", length = 9) public String EXTRA;
    }
}
