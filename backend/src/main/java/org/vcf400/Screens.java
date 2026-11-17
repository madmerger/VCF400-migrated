package org.vcf400;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Screens {
    private Screens() {}

    static Map<String, Object> response(String screen) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("screen", screen);
        String title = switch (screen) {
            case "VOTE1" -> "NOMINATE EXHIBIT FOR AWARD";
            case "ADDCMT" -> "GUESTBOOK/400 - ADD COMMENT";
            case "READCMT" -> "GUESTBOOK/400 - Read a Comment";
            case "MAIN" -> "LEARN/400";
            case "MENU" -> "WELCOME TO...";
            case "ADMPSWRD" -> "Are you sure you want to exit the kiosk?";
            case "VCFMAIN" -> "AS/400 DEMO MENU";
            case "CREDITS" -> "VCF/400 - Credits and Copyright";
            case "INTERTEST" -> "VCF/400 - How to Navigate";
            case "ENDOFCON" -> "The voting period has ended and you can no longer vote.";
            case "ENDCMT" -> "Thank you for commenting on this exhibit! Your Comment ID Is:";
            case "VOTEEND" -> " Your vote has been RECORDED! Thank you for participating in the awards";
            default -> "";
        };
        result.put("screenTitle", title);
        if (List.of("VOTE1", "ADDCMT", "READCMT").contains(screen)) {
            result.put("banner", "VINTAGE COMPUTER FESTIVAL");
        }
        result.put("fKeys", switch (screen) {
            case "VCFMAIN" -> Map.of("F3", "メニュー標準");
            case "VOTE1" -> Map.of("F5", "Submit Vote", "F12", "Cancel");
            case "ADDCMT" -> Map.of("F5", "Submit Comment", "F12", "Cancel");
            case "READCMT" -> Map.of("F5", "Submit", "F12", "Cancel");
            case "MAIN" -> Map.of("F3", "Exit", "F5", "Forwards", "F8", "Backwards");
            default -> Map.of();
        });
        result.put("inputOrder", switch (screen) {
            case "VOTE1" -> List.of("badge", "exhibit", "award");
            case "ADDCMT" -> List.of("name", "exhibit", "comment");
            case "READCMT" -> List.of("id");
            default -> List.of();
        });
        if (screen.equals("VOTE1")) result.put("errorPosition", Map.of("row", 6, "column", 14, "length", 45));
        if (screen.equals("ADDCMT")) result.put("errorPosition", Map.of("row", 10, "column", 5, "length", 21));
        if (screen.equals("READCMT")) result.put("errorPosition", Map.of("row", 4, "column", 4, "length", 20));
        return result;
    }
}
